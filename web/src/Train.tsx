// Entrenar (docs/VISION.md §5): empezar un entreno sin plan, tus rutinas y la biblioteca de ejercicios
import type { WebApp } from "brivel-shared"
import type { Act } from "./Today"
import type { FreeWorkout, Routine } from "./types"
import { RoutinesCard } from "./Routines"
import { LibraryCard } from "./Library"

export function Train({ core, act, openStrength }: { core: WebApp; act: Act; openStrength: (date: string) => void }) {
  const json = core.workoutTodayJson()
  const workout: FreeWorkout | null = json ? JSON.parse(json) : null
  const routines: Routine[] = JSON.parse(core.routinesJson())
  const start = (routineId: string | null) => act(() => openStrength(core.startWorkout(routineId)))
  return (
    <main className="wide">
      <header className="page-head"><div><p className="muted">Por tu cuenta</p><h1>Entrenar</h1></div></header>
      <div className="profile-grid">
        <section className="module">
          <h3>Entreno libre</h3>
          {workout ? (
            <>
              <p>Hoy: <strong>{workout.name}</strong><span className="muted"> · {workout.exercises.length === 1 ? "1 ejercicio" : `${workout.exercises.length} ejercicios`}</span></p>
              <button className="primary" data-strength={workout.date} onClick={() => openStrength(workout.date)}>Continuar</button>
              <button className="link" onClick={() => confirm("¿Descartar el entreno de hoy y las series que has apuntado en él?")
                && act(() => core.discardWorkout(), "Entreno descartado: hoy vuelve a tu plan")}>Descartar el entreno</button>
            </>
          ) : (
            <>
              <p className="muted">Entrena sin plan: elige los ejercicios sobre la marcha y apunta cada serie. Hoy sustituye a la fuerza de tu plan.</p>
              <button className="primary" onClick={() => start(null)}>Empezar entreno vacío</button>
              {routines.map((r) => (
                <button key={r.id} className="secondary" onClick={() => start(r.id)}>Empezar «{r.name}» hoy</button>
              ))}
            </>
          )}
        </section>
        <RoutinesCard core={core} act={act} />
        <LibraryCard core={core} />
      </div>
    </main>
  )
}
