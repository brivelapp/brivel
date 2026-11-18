// Perfil de la web, como ProfileScreen.kt y ProfileView.swift: se guarda solo. Conexiones y datos, en Ajustes aparte
import { useState, type ReactNode } from "react"
import type { WebApp } from "brivel-shared"
import type { Act } from "./Today"
import { SyncCard } from "./Sync"
import { GarminCard } from "./Garmin"
import { IntervalsCard } from "./Intervals"
import { GearCard } from "./Activities"
import { LEVEL_CHOICES, Segmented, SportPicker, STRENGTH_CHOICES, WeekBars, type Profile as ProfileData } from "./ProfileParts"

export function Profile({ core, act, synced, settings, setSettings }:
  { core: WebApp; act: Act; synced: (m: string | null) => void; settings: boolean; setSettings: (open: boolean) => void }) {
  const profile: ProfileData = JSON.parse(core.profileJson())
  const save = (p: ProfileData) => act(() => core.saveProfileJson(JSON.stringify(p)))
  const [year, setYear] = useState<number | null>(profile.birthYear)
  const [page, setPage] = useState<string | null>(null)  // dentro de Ajustes: garmin, intervals, sync o notice

  const exportData = () => {
    const a = document.createElement("a")
    a.href = URL.createObjectURL(new Blob([core.exportJson()], { type: "application/json" }))
    a.download = "brivel-datos.json"
    a.click()
    URL.revokeObjectURL(a.href)
  }

  if (settings) {
    const titles: Record<string, string> = { garmin: "Garmin Connect™", intervals: "intervals.icu", sync: "Otros dispositivos", notice: "Aviso de salud" }
    const back = () => (page ? setPage(null) : setSettings(false))
    return (
      <main className="wide">
        <div className="row">
          <button className="back" aria-label={page ? "Volver a Ajustes" : "Volver a Perfil"} onClick={back}>‹</button>
          <h1>{page ? titles[page] : "Ajustes"}</h1>
        </div>
        {page === "garmin" && <GarminCard core={core} onDone={synced} />}
        {page === "intervals" && <>
          <IntervalsCard core={core} onDone={synced} />
          <section className="module">
            <label className="row"><input type="checkbox" checked={profile.renameActivities !== false}
              onChange={(e) => save({ ...profile, renameActivities: e.target.checked })} /> Renombrar mis actividades</label>
            <p className="muted small-text">Les pone un título que dice lo que fueron.</p>
          </section>
        </>}
        {page === "sync" && <SyncCard core={core} onDone={synced} />}
        {page === "notice" && <section className="module"><p className="muted small-text">{core.healthNotice}</p></section>}
        {!page && <div className="settings">
          <SettingsGroup title="Conexiones">
            <SettingsRow name="Garmin Connect™" state={core.garminConnected ? "Conectado" : "Sin conectar"} on={core.garminConnected} onClick={() => setPage("garmin")} />
            <SettingsRow name="intervals.icu" state={core.intervalsAthlete ? "Conectado" : "Sin conectar"} on={!!core.intervalsAthlete} onClick={() => setPage("intervals")} />
          </SettingsGroup>
          <SettingsGroup title="Tus datos">
            <SettingsRow name="Otros dispositivos" state={core.syncNeedsConsent ? "En pausa" : core.syncActive ? "Sincronizado" : "Solo aquí"}
              on={core.syncActive && !core.syncNeedsConsent} onClick={() => setPage("sync")} />
            <SettingsRow name="Exportar mis datos" onClick={exportData} />
          </SettingsGroup>
          <SettingsGroup title="Sobre Brivel">
            <SettingsRow name="Aviso de salud" onClick={() => setPage("notice")} />
          </SettingsGroup>
        </div>}
      </main>
    )
  }

  return (
    <main className="wide">
      <div className="row between">
        <h1>Perfil</h1>
        <button className="gear" aria-label="Ajustes" onClick={() => setSettings(true)}>
          {/* Rueda: trazo de Lucide «settings» (ISC), el mismo que ic_settings.xml en Android */}
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        </button>
      </div>
      <div className="profile-grid">
        {/* Sin plan, deportes, semana, fuerza y nivel no cambian nada: se preguntan si un día pide un plan */}
        {!core.noPlan && <>
        <section className="module">
          <h3>Tus deportes</h3>
          <SportPicker core={core} cardio={profile.cardio} onChange={(cardio) => save({ ...profile, cardio })} />
        </section>
        <section className="module">
          <h3>Tu semana</h3>
          <WeekBars core={core} days={profile.days} onCommit={(days) => save({ ...profile, days })} />
        </section>
        <section className="module">
          <h3>Fuerza</h3>
          <Segmented options={STRENGTH_CHOICES.map(([k, l]) => [k, l])} selected={profile.strength} label="Fuerza"
            onSelect={(strength) => save({ ...profile, strength })} />
          <h3>Tu nivel</h3>
          <Segmented options={LEVEL_CHOICES.map(([k, l]) => [k, l])} selected={profile.level} label="Nivel"
            onSelect={(level) => save({ ...profile, level })} />
        </section>
        </>}
        <section className="module">
          <h3>Año de nacimiento</h3>
          <label className="field">Para calcular tus zonas de pulso
            <input inputMode="numeric" value={year ?? ""} onChange={(e) => {
              const y = parseInt(e.target.value.replace(/\D/g, "").slice(0, 4)) || null
              setYear(y)
              if (y != null && core.ageOk(y)) save({ ...profile, birthYear: y })
            }} />
          </label>
          {year != null && year >= 1000 && !core.ageOk(year) && <p className="caution-text">Hace falta tener al menos {core.minAge} años.</p>}
        </section>
        <GearCard core={core} act={act} />
      </div>
    </main>
  )
}

/** Un grupo de Ajustes: título pequeño y una tarjeta con sus filas. */
function SettingsGroup({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="settings-group" aria-label={title}>
      <h2 className="settings-title">{title}</h2>
      <div className="settings-list">{children}</div>
    </section>
  )
}

/** Una fila de Ajustes: punto de estado, nombre y estado en una palabra; se toca entera. */
function SettingsRow({ name, state, on = false, onClick }: { name: string; state?: string; on?: boolean; onClick: () => void }) {
  return (
    <button className="settings-row" onClick={onClick}>
      {state !== undefined && <span className={`dot${on ? " on" : ""}`} aria-hidden="true" />}
      <span className="settings-name">{name}</span>
      {state !== undefined && <span className={`settings-state${on ? " on" : ""}`}>{state}</span>}
      <span className="chev" aria-hidden="true">›</span>
    </button>
  )
}
