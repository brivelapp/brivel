// Sesiones propias de resistencia (OwnSessions.kt): pasos con minutos, intensidad, repeticiones y descanso. Los días
// elegidos sustituyen a la sesión de ese deporte del plan; también se puede hacer hoy, solo esta vez.
import { useEffect, useRef, useState } from "react"
import type { WebApp } from "brivel-shared"
import type { Act } from "./Today"
import type { OwnSession, OwnStep } from "./types"
import { attempt, DAYS, DAY_NAME, daysText, Num } from "./Routines"
import { Choice } from "./ProfileParts"

const EMPTY: OwnSession = { id: "", name: "", sport: "running", steps: [{ name: "", minutes: 30, intensityZone: "Z2", reps: 1, restMinutes: 0 }], days: [], dates: [], since: "" }
const total = (s: OwnSession) => Math.round(s.steps.reduce((a, x) => a + x.reps * x.minutes + (x.reps > 1 ? x.reps * x.restMinutes : 0), 0))

/** Minutos con decimales (30″ = 0,5): se escribe como texto para poder teclear la coma. */
function Minutes({ label, value, onChange }: { label: string; value: number; onChange: (n: number) => void }) {
  const [text, setText] = useState(value ? String(value).replace(".", ",") : "")
  return (
    <label className="field small-field">{label}
      <input inputMode="decimal" value={text} onChange={(e) => {
        const t = e.target.value.replace(/[^\d.,]/g, "").slice(0, 5)
        setText(t)
        onChange(parseFloat(t.replace(",", ".")) || 0)
      }} />
    </label>
  )
}

function Editor({ core, act, start, close }: { core: WebApp; act: Act; start: OwnSession; close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal() }, [])
  const [s, setS] = useState<OwnSession>(start)
  const [error, setError] = useState<string | null>(null)
  const sports: string[] = JSON.parse(core.ownSportsJson())
  const zones: [string, string][] = JSON.parse(core.ownZonesJson())
  const setStep = (i: number, step: OwnStep) => setS({ ...s, steps: s.steps.map((x, j) => (j === i ? step : x)) })
  const title = start.id ? "Editar sesión" : "Nueva sesión"
  return (
    <dialog ref={dialog} className="sheet" onClose={close} aria-label={title}>
      <h2>{title}</h2>
      <label className="field">Nombre
        <input className="text" value={s.name} onChange={(e) => setS({ ...s, name: e.target.value })} placeholder="Series de 3 minutos, rodaje…" />
      </label>
      <Choice label="Deporte" options={sports.map((k): [string, string] => [k, core.sportLabel(k)])} value={s.sport} onChange={(sport) => setS({ ...s, sport })} />
      <h3>Pasos</h3>
      {s.steps.map((step, i) => (
        <div key={i} className="module routine-exercise">
          <div className="row">
            <strong className="grow">Paso {i + 1}</strong>
            {s.steps.length > 1 && <button className="link" aria-label={`Quitar el paso ${i + 1}`}
              onClick={() => setS({ ...s, steps: s.steps.filter((_, j) => j !== i) })}>Quitar</button>}
          </div>
          <label className="field">Nombre del paso (opcional)
            <input className="text" value={step.name} onChange={(e) => setStep(i, { ...step, name: e.target.value })} placeholder="Calentamiento, rápido…" />
          </label>
          <Choice label="Intensidad" options={zones.map(([k, label]): [string, string] => [k, `${label} (${k})`])} value={step.intensityZone}
            onChange={(intensityZone) => setStep(i, { ...step, intensityZone })} />
          <div className="row wrap">
            <Minutes label="Minutos" value={step.minutes} onChange={(minutes) => setStep(i, { ...step, minutes })} />
            <Num label="Veces" value={step.reps} onChange={(reps) => setStep(i, { ...step, reps })} />
            {step.reps > 1 && <Minutes label="Descanso (min)" value={step.restMinutes} onChange={(restMinutes) => setStep(i, { ...step, restMinutes })} />}
          </div>
        </div>
      ))}
      <button className="secondary" onClick={() => setS({ ...s, steps: [...s.steps, { name: "", minutes: 10, intensityZone: "Z2", reps: 1, restMinutes: 0 }] })}>Añadir paso</button>
      <p className="muted small-text">Total: {total(s)} min. El pulso o el ritmo de cada paso salen de tus zonas, como en las sesiones del plan.</p>
      <h3 id="session-days">Qué días la haces</h3>
      <div className="day-chips" role="group" aria-labelledby="session-days">
        {DAYS.map(([k, l]) => {
          const on = s.days.includes(k)
          return <button key={k} className="chip" aria-pressed={on} aria-label={DAY_NAME[k]}
            onClick={() => setS({ ...s, days: on ? s.days.filter((d) => d !== k) : [...s.days, k] })}>{l}</button>
        })}
      </div>
      <p className="muted small">Esos días sustituye a la sesión de ese deporte de tu plan. Sin días, queda guardada para hacerla cuando quieras.</p>
      {error && <p className="caution-text" role="alert">{error}</p>}
      <button className="primary" onClick={() => attempt(() => core.saveOwnSessionJson(JSON.stringify(s)), setError, () => act(close, "Sesión guardada"))}>Guardar</button>
      {start.id && <button className="link" onClick={() => confirm(`¿Borrar la sesión «${start.name}»?`)
        && act(() => { core.deleteOwnSession(start.id); close() }, "Sesión borrada")}>Borrar sesión</button>}
      <button className="secondary" onClick={close}>Cancelar</button>
    </dialog>
  )
}

/** En Entrenar: las sesiones propias, hacer una hoy y crear otra. */
export function SessionsCard({ core, act }: { core: WebApp; act: Act }) {
  const [editing, setEditing] = useState<OwnSession | null>(null)
  const sessions: OwnSession[] = JSON.parse(core.ownSessionsJson())
  return (
    <section className="module">
      <button className="primary" onClick={() => setEditing(EMPTY)}>Nueva sesión</button>
      {sessions.length === 0 && <p className="muted small-text">Aún no tienes ninguna. Móntala paso a paso y va al calendario y al reloj como las del plan.</p>}
      {sessions.map((s) => (
        <div key={s.id} className="row">
          <button className="row option grow" onClick={() => setEditing(s)}>
            <span className="grow">{s.name}<span className="muted small"> · {core.sportLabel(s.sport)} · {total(s)} min · {daysText(s.days).replace("sin días: no entra en el plan", "sin días fijos")}</span></span>
          </button>
          <button className="text" aria-label={`Hacer ${s.name} hoy`} onClick={() => act(() => core.doOwnSessionToday(s.id), "Puesta hoy: la tienes en Hoy")}>Hoy</button>
        </div>
      ))}
      {editing && <Editor core={core} act={act} start={editing} close={() => setEditing(null)} />}
    </section>
  )
}
