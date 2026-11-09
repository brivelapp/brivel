// Rutinas propias (Routines.kt): ejercicios con sus series y repeticiones y los días en que se hacen. Esos días
// sustituyen a la fuerza del plan; el resto del plan sigue igual.
import { useEffect, useRef, useState } from "react"
import type { WebApp } from "brivel-shared"
import type { Act } from "./Today"
import type { CustomExercise, ExerciseCard, Routine, RoutineExercise } from "./types"

const DAYS: [string, string][] = [["MON", "L"], ["TUE", "M"], ["WED", "X"], ["THU", "J"], ["FRI", "V"], ["SAT", "S"], ["SUN", "D"]]
const DAY_NAME: Record<string, string> = { MON: "lunes", TUE: "martes", WED: "miércoles", THU: "jueves", FRI: "viernes", SAT: "sábado", SUN: "domingo" }
const EMPTY: Routine = { id: "", name: "", exercises: [], days: [], rir: 2, since: "" }

const daysText = (days: string[]) =>
  days.length ? DAYS.filter(([k]) => days.includes(k)).map(([k]) => DAY_NAME[k]).join(", ") : "sin días: no entra en el plan"

function Num({ label, value, onChange }: { label: string; value: number; onChange: (n: number) => void }) {
  return (
    <label className="field small-field">{label}
      <input inputMode="numeric" value={value || ""} onChange={(e) => onChange(parseInt(e.target.value.replace(/\D/g, "").slice(0, 3)) || 0)} />
    </label>
  )
}

function Editor({ core, act, start, close }: { core: WebApp; act: Act; start: Routine; close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal() }, [])
  const [r, setR] = useState<Routine>(start)
  const all: ExerciseCard[] = JSON.parse(core.libraryJson("", null, null))
  const timed = (name: string) => all.find((c) => c.name === name)?.timed ?? false
  const setEx = (i: number, e: RoutineExercise) => setR({ ...r, exercises: r.exercises.map((x, j) => (j === i ? e : x)) })
  const add = (name: string) => setR({ ...r, exercises: [...r.exercises, timed(name)
    ? { name, sets: 3, low: 20, high: 40 } : { name, sets: 3, low: 8, high: 12 }] })
  const [query, setQuery] = useState("")
  const found: ExerciseCard[] = JSON.parse(core.libraryJson(query, null, null))
  const groups = [...new Set(found.map((c) => c.patternLabel))]
  return (
    <dialog ref={dialog} className="sheet" onClose={close} aria-label={start.id ? "Editar rutina" : "Nueva rutina"}>
      <h2>{start.id ? "Editar rutina" : "Nueva rutina"}</h2>
      <label className="field">Nombre
        <input className="text" value={r.name} onChange={(e) => setR({ ...r, name: e.target.value })} placeholder="Torso, Pierna…" />
      </label>
      <h3 id="routine-days">Qué días la haces</h3>
      <div className="day-chips" role="group" aria-labelledby="routine-days">
        {DAYS.map(([k, l]) => {
          const on = r.days.includes(k)
          return <button key={k} className="chip" aria-pressed={on} aria-label={DAY_NAME[k]}
            onClick={() => setR({ ...r, days: on ? r.days.filter((d) => d !== k) : [...r.days, k] })}>{l}</button>
        })}
      </div>
      <p className="muted small">Esos días sustituye a la fuerza de tu plan. Un día solo puede tener una rutina.</p>
      <h3>Ejercicios</h3>
      {r.exercises.map((e, i) => (
        <div key={i} className="module routine-exercise">
          <div className="row">
            <strong className="grow">{e.name}</strong>
            <button className="link" aria-label={`Quitar ${e.name}`}
              onClick={() => setR({ ...r, exercises: r.exercises.filter((_, j) => j !== i) })}>Quitar</button>
          </div>
          <div className="row wrap">
            <Num label="Series" value={e.sets} onChange={(sets) => setEx(i, { ...e, sets })} />
            <Num label={timed(e.name) ? "Desde (s)" : "Desde (reps)"} value={e.low} onChange={(low) => setEx(i, { ...e, low })} />
            <Num label={timed(e.name) ? "Hasta (s)" : "Hasta (reps)"} value={e.high} onChange={(high) => setEx(i, { ...e, high })} />
          </div>
        </div>
      ))}
      <label className="field">Buscar ejercicio
        <input className="text" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Curl, sentadilla, polea…" />
      </label>
      <label className="field">Añadir ejercicio
        <select value="" onChange={(e) => e.target.value && add(e.target.value)}>
          <option value="">Elige uno…</option>
          {groups.map((g) => (
            <optgroup key={g} label={g}>
              {found.filter((c) => c.patternLabel === g).map((c) => <option key={c.name} value={c.name}>{c.name}</option>)}
            </optgroup>
          ))}
        </select>
      </label>
      <label className="field">Reserva al acabar cada serie
        <select value={r.rir} onChange={(e) => setR({ ...r, rir: parseInt(e.target.value) })}>
          {[0, 1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n === 0 ? "Hasta no poder más" : `${n} repeticiones`}</option>)}
        </select>
      </label>
      <p className="muted small">Brivel te propone el peso con lo que apuntes, sin subir nunca más de un 10 % de una vez.</p>
      <button className="primary" onClick={() => act(() => { core.saveRoutineJson(JSON.stringify(r)); close() }, "Rutina guardada")}>Guardar</button>
      {start.id && <button className="link" onClick={() => confirm(`¿Borrar la rutina «${start.name}»?`)
        && act(() => { core.deleteRoutine(start.id); close() }, "Rutina borrada")}>Borrar rutina</button>}
      <button className="secondary" onClick={close}>Cancelar</button>
    </dialog>
  )
}

function NewExercise({ core, act, close }: { core: WebApp; act: Act; close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal() }, [])
  const patterns: [string, string][] = Object.entries(JSON.parse(core.patternLabelsJson()))
  const [e, setE] = useState<CustomExercise>({ name: "", pattern: patterns[0][0], timed: false })
  return (
    <dialog ref={dialog} className="sheet" onClose={close} aria-label="Nuevo ejercicio">
      <h2>Nuevo ejercicio</h2>
      <label className="field">Nombre
        <input className="text" value={e.name} onChange={(v) => setE({ ...e, name: v.target.value })} />
      </label>
      <label className="field">Tipo
        <select value={e.pattern} onChange={(v) => setE({ ...e, pattern: v.target.value })}>
          {patterns.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
      </label>
      <label className="row"><input type="checkbox" checked={e.timed} onChange={(v) => setE({ ...e, timed: v.target.checked })} /> Va por tiempo, no por repeticiones</label>
      <button className="primary" onClick={() => act(() => { core.saveCustomExerciseJson(JSON.stringify(e)); close() }, "Ejercicio guardado")}>Guardar</button>
      <button className="secondary" onClick={close}>Cancelar</button>
    </dialog>
  )
}

/** En Perfil: las rutinas propias y el botón para crear otra. */
export function RoutinesCard({ core, act }: { core: WebApp; act: Act }) {
  const [editing, setEditing] = useState<Routine | null>(null)
  const [adding, setAdding] = useState(false)
  const routines: Routine[] = JSON.parse(core.routinesJson())
  const custom: CustomExercise[] = JSON.parse(core.customExercisesJson())
  return (
    <section className="module">
      <h3>Tus rutinas</h3>
      <p className="muted">Crea tu propia rutina de fuerza y elige qué días la haces. Esos días sustituye a la fuerza del plan.</p>
      {routines.map((r) => (
        <button key={r.id} className="row option" onClick={() => setEditing(r)}>
          <span className="grow">{r.name}<span className="muted small"> · {r.exercises.length} ejercicios · {daysText(r.days)}</span></span>
        </button>
      ))}
      <button className="secondary" onClick={() => setEditing(EMPTY)}>Nueva rutina</button>
      <h3>Tus ejercicios</h3>
      <p className="muted">Si falta alguno en la biblioteca, créalo y úsalo en tus rutinas.</p>
      {custom.map((e) => (
        <div key={e.name} className="row">
          <span className="grow">{e.name}</span>
          <button className="link" aria-label={`Borrar ${e.name}`} onClick={() => confirm(`¿Borrar el ejercicio «${e.name}»?`)
            && act(() => core.deleteCustomExercise(e.name), "Ejercicio borrado")}>Borrar</button>
        </div>
      ))}
      <button className="secondary" onClick={() => setAdding(true)}>Nuevo ejercicio</button>
      {editing && <Editor core={core} act={act} start={editing} close={() => setEditing(null)} />}
      {adding && <NewExercise core={core} act={act} close={() => setAdding(false)} />}
    </section>
  )
}
