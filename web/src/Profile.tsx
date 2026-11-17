// Perfil de la web, como ProfileScreen.kt y ProfileView.swift: se guarda solo. Conexiones y datos, en Ajustes aparte
import { useState } from "react"
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

  const exportData = () => {
    const a = document.createElement("a")
    a.href = URL.createObjectURL(new Blob([core.exportJson()], { type: "application/json" }))
    a.download = "brivel-datos.json"
    a.click()
    URL.revokeObjectURL(a.href)
  }

  if (settings) return (
    <main className="wide">
      <div className="row">
        <button className="back" aria-label="Volver a Perfil" onClick={() => setSettings(false)}>‹</button>
        <h1>Ajustes</h1>
      </div>
      <div className="profile-grid">
        <IntervalsCard core={core} onDone={synced} />
        <GarminCard core={core} onDone={synced} />
        <SyncCard core={core} onDone={synced} />
        <section className="module">
          <h3>Tus datos</h3>
          <label className="row"><input type="checkbox" checked={profile.renameActivities !== false}
            onChange={(e) => save({ ...profile, renameActivities: e.target.checked })} /> Poner a tus actividades de intervals.icu un título que diga lo que fueron</label>
          <button className="secondary" onClick={exportData}>Exportar todos mis datos (JSON)</button>
          <h3>Aviso de salud</h3>
          <p className="muted small-text">{core.healthNotice}</p>
        </section>
      </div>
    </main>
  )

  return (
    <main className="wide">
      <div className="row between">
        <h1>Perfil</h1>
        <button className="secondary" onClick={() => setSettings(true)}>Ajustes y conexiones</button>
      </div>
      <div className="profile-grid">
        <section className="module">
          <h3>Qué haces además de caminar</h3>
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
