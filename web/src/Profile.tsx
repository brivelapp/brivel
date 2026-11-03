// Perfil de la web, como ProfileScreen.kt y ProfileView.swift: se guarda solo y la semana se cambia en un diálogo
import { useEffect, useRef, useState } from "react"
import type { WebApp } from "brivel-shared"
import type { Act } from "./Today"
import { SyncCard } from "./Sync"
import { GarminCard } from "./Garmin"
import { LEVEL_CHOICES, Segmented, SportPicker, STRENGTH_CHOICES, WeekBars, type Profile as ProfileData } from "./ProfileParts"

export function Profile({ core, act, synced }: { core: WebApp; act: Act; synced: (m: string | null) => void }) {
  const profile: ProfileData = JSON.parse(core.profileJson())
  const save = (p: ProfileData) => act(() => core.saveProfileJson(JSON.stringify(p)))
  const [year, setYear] = useState<number | null>(profile.birthYear)
  const [editingWeek, setEditingWeek] = useState(false)
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { if (editingWeek) dialog.current?.showModal(); else dialog.current?.close() }, [editingWeek])

  return (
    <main className="wide">
      <h1>Perfil</h1>
      <div className="profile-grid">
        <section className="module">
          <h3>Tu objetivo</h3>
          <p className="big">{core.goalLabel}</p>
          <p className="muted">Puedes cambiarlo o abandonarlo cuando quieras; el plan se rehace al momento.</p>
          <button className="secondary" onClick={() => act(() => core.abandonGoal())}>Cambiar objetivo</button>
        </section>
        <section className="module">
          <h3>Qué haces además de caminar</h3>
          <SportPicker core={core} cardio={profile.cardio} onChange={(cardio) => save({ ...profile, cardio })} />
        </section>
        <section className="module">
          <h3>Tu semana</h3>
          <WeekBars core={core} days={profile.days} editable={false} />
          <button className="secondary" onClick={() => setEditingWeek(true)}>Cambiar tu semana</button>
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
        <SyncCard core={core} onDone={synced} />
        <GarminCard core={core} onDone={synced} />
      </div>
      {/* En un diálogo: en el móvil, dentro de la página, desplazarla movería las barras */}
      <dialog ref={dialog} className="sheet" onClose={() => setEditingWeek(false)} aria-label="Tu semana">
        <h2>Tu semana</h2>
        <WeekBars core={core} days={profile.days} onCommit={(days) => save({ ...profile, days })} />
        <button className="primary" onClick={() => setEditingWeek(false)}>Hecho</button>
      </dialog>
    </main>
  )
}
