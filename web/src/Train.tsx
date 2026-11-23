// Entrenar (docs/VISION.md §5; por secciones desde el 2026-11-23): arriba, las dos píldoras de lo que se hace ya
// («Entrenar fuerza» y, en la web, que no graba con GPS, «Subir actividad»); debajo, lo tuyo plegado en una línea.
import { useState } from "react"
import type { WebApp } from "brivel-shared"
import type { Act } from "./Today"
import type { FreeWorkout, Routine } from "./types"
import { RoutinesCard } from "./Routines"
import { LibraryCard } from "./Library"
import { SessionsCard } from "./Sessions"
import { ActivitiesCard } from "./Activities"
import { Fold } from "./ProfileParts"

const count = (n: number, one: string, many: string) => n === 0 ? "Ninguna todavía" : n === 1 ? `1 ${one}` : `${n} ${many}`

export function Train({ core, act, openStrength }: { core: WebApp; act: Act; openStrength: (date: string) => void }) {
  const json = core.workoutTodayJson()
  const workout: FreeWorkout | null = json ? JSON.parse(json) : null
  const routines: Routine[] = JSON.parse(core.routinesJson())
  const sessions: unknown[] = JSON.parse(core.ownSessionsJson())
  const activityCount = (JSON.parse(core.activitiesJson()) as unknown[]).length
  const custom: unknown[] = JSON.parse(core.customExercisesJson())
  const start = (routineId: string | null) => act(() => openStrength(core.startWorkout(routineId)))
  const [uploading, setUploading] = useState(0)  // «Subir actividad» abre «Actividades», donde está el botón de importar
  return (
    <main className="wide">
      <header className="page-head"><div><p className="muted">Por tu cuenta</p><h1>Entrenar</h1></div></header>
      <div className="row pills">
        <button className="primary grow" onClick={() => workout ? openStrength(workout.date) : start(null)}>{workout ? "Seguir con la fuerza" : "Entrenar fuerza"}</button>
        <button className="secondary grow" onClick={() => setUploading((n) => n + 1)}>Subir actividad</button>
      </div>
      {workout && (
        <section className="module">
          <p>Hoy: <strong>{workout.name}</strong><span className="muted"> · {workout.exercises.length === 1 ? "1 ejercicio" : `${workout.exercises.length} ejercicios`}</span></p>
          <button className="link" onClick={() => confirm("¿Descartar el entreno de hoy y las series que has apuntado en él?")
            && act(() => core.discardWorkout(), "Entreno descartado: hoy vuelve a tu plan")}>Descartar el entreno</button>
        </section>
      )}
      <h2 className="settings-title">Lo tuyo</h2>
      <div className="stack tight">
        <Fold title="Sesiones" line={count(sessions.length, "sesión", "sesiones") + " · carrera, bici, natación"}>
          <SessionsCard core={core} act={act} />
        </Fold>
        <Fold title="Rutinas de fuerza" line={count(routines.length, "rutina", "rutinas") + " · sustituyen a la del plan"}>
          {!workout && routines.length > 0 && (
            <div className="row wrap">{routines.map((r) => <button key={r.id} className="secondary" onClick={() => start(r.id)}>Empezar «{r.name}» hoy</button>)}</div>
          )}
          <RoutinesCard core={core} act={act} />
        </Fold>
        <Fold key={`act-${uploading}`} open={uploading > 0} title="Actividades" line={count(activityCount, "actividad", "actividades") + " · subir FIT, GPX o TCX"}>
          <ActivitiesCard core={core} act={act} />
        </Fold>
        {custom.length > 0 && <p className="muted small-text">Tus ejercicios propios están en «Rutinas de fuerza».</p>}
      </div>
      <h2 className="settings-title">Consultar</h2>
      <Fold title="Biblioteca de ejercicios" line="Busca por zona del cuerpo o material">
        <LibraryCard core={core} />
      </Fold>
    </main>
  )
}
