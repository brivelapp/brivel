// Sesión de fuerza a pantalla completa, como StrengthSession.kt: Serie (una cada vez) y Sesión (toda la sesión en tabla)
import { useEffect, useState } from "react"
import type { WebApp } from "brivel-shared"
import type { FigureLines, LoggedSet, StrengthExercise, StrengthScreen } from "./types"
import { Icon, type Act } from "./Today"
import { Segmented } from "./ProfileParts"
import check from "../../iosApp/iosApp/Assets.xcassets/check.imageset/check.svg"
import back from "../../iosApp/iosApp/Assets.xcassets/chevron_left.imageset/chevron_left.svg"

const kgText = (w: number | null) => (w == null ? "Tu peso" : String(w).replace(".", ","))
const amount = (reps: number, timed: boolean) => (timed ? `${reps}″` : String(reps))
const setText = (s: LoggedSet, timed: boolean) => (s.weightKg != null ? `${kgText(s.weightKg)} × ` : "") + amount(s.reps, timed)
const effort = (rir: number) => (rir === 0 ? "Hasta no poder más" : `Termina con ${rir} en reserva`)
const clock = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`
const pendingIndex = (list: StrengthExercise[]) => list.findIndex((e) => e.done.length < e.sets)

/** Figura del patrón de movimiento: cuerpo en el color del texto y, en verde, lo que trabaja. */
export function Figure({ core, pattern, size = 52 }: { core: WebApp; pattern: string; size?: number }) {
  const json = core.figureJson(pattern)
  if (!json) return null
  const f: FigureLines = JSON.parse(json)
  const points = (l: number[]) => l.map((v, i) => (i % 2 ? `${v} ` : `${v},`)).join("")
  return (
    <svg className="figure" width={size} height={size} viewBox="0 0 48 48" aria-hidden="true">
      {f.body.map((l, i) => <polyline key={`b${i}`} points={points(l)} className="fig-body" />)}
      {f.work.map((l, i) => <polyline key={`w${i}`} points={points(l)} className="fig-work" />)}
      <circle cx={f.head[0]} cy={f.head[1]} r="3" className="fig-body" />
    </svg>
  )
}

export function Strength({ core, date, act, close }: { core: WebApp; date: string; act: Act; close: () => void }) {
  const json = core.strengthJson(date)
  const screen: StrengthScreen | null = json ? JSON.parse(json) : null
  const exercises = screen?.exercises ?? []
  const [mode, setMode] = useState("set")
  const [current, setCurrent] = useState(() => Math.max(0, pendingIndex(exercises)))
  const [setNo, setSetNo] = useState(() => (exercises[Math.max(0, pendingIndex(exercises))]?.done.length ?? 0) + 1)
  const [restEnd, setRestEnd] = useState<number | null>(null)
  const [restTotal, setRestTotal] = useState(0)
  const [logged, setLogged] = useState<[StrengthExercise, LoggedSet] | null>(null)  // la última serie apuntada
  const [restView, setRestView] = useState(false)  // tras "Serie hecha"; al abrir una serie de la tabla, la serie y el descanso abajo
  const [now, setNow] = useState(Date.now())

  useEffect(() => { if (!screen) close() }, [screen, close])
  // La pantalla no se apaga entre series, donde el navegador lo permite
  useEffect(() => {
    let lock: { release: () => Promise<void> } | null = null
    const nav = navigator as Navigator & { wakeLock?: { request: (t: "screen") => Promise<{ release: () => Promise<void> }> } }
    nav.wakeLock?.request("screen").then((l) => { lock = l }, () => {})
    return () => { lock?.release().catch(() => {}) }
  }, [])
  useEffect(() => {
    if (restEnd == null) return
    const t = window.setInterval(() => {
      const n = Date.now()
      setNow(n)
      if (n >= restEnd) { setRestEnd(null); navigator.vibrate?.(300) }
    }, 250)
    return () => clearInterval(t)
  }, [restEnd])
  if (!screen) return null
  const left = restEnd == null ? 0 : Math.max(0, Math.ceil((restEnd - now) / 1000))
  const e = exercises[current]
  const pending = pendingIndex(exercises)

  const save = (ex: StrengthExercise, weight: string, reps: number) => {
    const editing = ex.done.find((d) => d.set === setNo)
    act(() => core.logSet(date, ex.name, setNo, weight, reps, editing?.rir ?? null))
    const w = weight.trim() === "" ? null : parseFloat(weight.replace(",", "."))
    setLogged([ex, { set: setNo, weightKg: w, reps, rir: editing?.rir ?? null }])
    // Siguiente: otra serie de este ejercicio o el primero que falte; sin ninguno, sesión completa
    const freshJson = core.strengthJson(date)
    const fresh: StrengthExercise[] = freshJson ? JSON.parse(freshJson).exercises : exercises
    const p = pendingIndex(fresh)
    if (!editing && setNo < ex.sets && !fresh[current].done.some((d) => d.set === setNo + 1)) setSetNo(setNo + 1)
    else if (p >= 0) { setCurrent(p); setSetNo(fresh[p].done.length + 1) }
    else setSetNo(ex.sets + 1)
    if (!editing && p >= 0) { setRestTotal(ex.restSeconds); setNow(Date.now()); setRestEnd(Date.now() + ex.restSeconds * 1000); setRestView(true) }
  }
  const rate = (rir: number) => {
    if (!logged) return
    const [ex, s] = logged
    act(() => core.logSet(date, ex.name, s.set, s.weightKg == null ? "" : String(s.weightKg), s.reps, rir))
    setLogged([ex, { ...s, rir }])
  }

  return (
    <main className="strength">
      <div className="row">
        <button className="back" aria-label="Cerrar la sesión" onClick={close}><Icon src={back} size={22} /></button>
        <h1 className="grow strength-title">{screen.title}</h1>
        <span className="mono muted small">
          {exercises.reduce((a, x) => a + x.done.length, 0)}/{exercises.reduce((a, x) => a + x.sets, 0)} series
        </span>
      </div>
      <Segmented options={[["set", "Serie"], ["session", "Sesión"]]} selected={mode} onSelect={setMode} label="Vista" />
      {mode === "session"
        ? exercises.map((ex, i) => (
          <SessionRows key={ex.name} e={ex} isCurrent={i === current} currentSet={setNo}
            open={(n) => { setCurrent(i); setSetNo(n); setMode("set"); setRestView(false) }} />
        ))
        : restEnd != null && restView
          ? <Rest left={left} total={restTotal} e={e} setNo={setNo} logged={logged} rate={rate}
              adjust={(s) => { const end = restEnd + s * 1000; setRestEnd(end <= Date.now() ? null : end) }} skip={() => setRestEnd(null)} />
          : e && !(setNo > e.sets && pending < 0)
            ? <SetView key={`${e.name}-${setNo}`} core={core} e={e} setNo={setNo} next={exercises[current + 1]} done={(w, r) => save(e, w, r)} />
            : <Finished other={screen.other} close={close} />}
      {restEnd != null && (mode === "session" || !restView) && (
        <div className="rest-bar" role="timer" aria-label="Descanso">
          <strong>Descanso</strong>
          <span className="rest-track"><span style={{ width: `${(left / Math.max(restTotal, 1)) * 100}%` }} /></span>
          <span className="mono">{clock(left)}</span>
        </div>
      )}
    </main>
  )
}

function SetView({ core, e, setNo, next, done }:
  { core: WebApp; e: StrengthExercise; setNo: number; next?: StrengthExercise; done: (weight: string, reps: number) => void }) {
  const today = e.done.find((d) => d.set === setNo)
  const previous = e.previous.find((d) => d.set === setNo)
  const startWeight = today?.weightKg ?? e.weightKg ?? previous?.weightKg ?? null
  const [weight, setWeight] = useState(startWeight == null ? "" : String(startWeight).replace(".", ","))
  // Con el mismo peso, las repeticiones de la última vez; si cambia el peso, el mínimo del rango (progresión doble)
  const [reps, setReps] = useState(today?.reps ?? (previous && previous.weightKg === e.weightKg ? previous.reps : e.repsLow))
  const w = weight.trim() === "" ? null : parseFloat(weight.replace(",", "."))
  const step = e.timed ? 5 : 1
  const setW = (v: number | null) => setWeight(v == null || v <= 0 ? "" : String(v).replace(".", ","))
  return (
    <>
      <div className="set-dots" aria-hidden="true">
        {Array.from({ length: Math.max(e.sets, e.done.length) }, (_, i) => <span key={i} className={e.done.some((d) => d.set === i + 1) ? "on" : ""} />)}
      </div>
      <div className="row">
        <span className="figure-box"><Figure core={core} pattern={e.pattern} /></span>
        <div className="grow">
          <h2>{e.name}</h2>
          <p className="muted small-text">Serie {setNo} de {e.sets} · {e.repsLow}–{e.repsHigh} {e.timed ? "segundos" : "repeticiones"}</p>
          {e.rir != null && <p className="small-text"><strong>{effort(e.rir)}</strong></p>}
        </div>
      </div>
      {e.suggestion && <p className="hint">{e.suggestion}</p>}
      <div className="steppers">
        <div className="stepper">
          <label className="muted small" htmlFor="set-weight">kg</label>
          <input id="set-weight" className="mono stepper-value" inputMode="decimal" placeholder="Tu peso" value={weight}
            onChange={(ev) => setWeight(ev.target.value.replace(/[^\d.,]/g, ""))} />
          <div className="row">
            <button aria-label="Menos peso" onClick={() => setW(w == null ? null : w - 2.5)}>−</button>
            <button aria-label="Más peso" onClick={() => setW((w ?? 0) + 2.5)}>+</button>
          </div>
        </div>
        <div className="stepper">
          <span className="muted small">{e.timed ? "segundos" : "repeticiones"}</span>
          <span className="mono stepper-value" aria-live="polite">{amount(reps, e.timed)}</span>
          <div className="row">
            <button aria-label={`Menos ${e.timed ? "segundos" : "repeticiones"}`} onClick={() => setReps(Math.max(1, reps - step))}>−</button>
            <button aria-label={`Más ${e.timed ? "segundos" : "repeticiones"}`} onClick={() => setReps(Math.min(600, reps + step))}>+</button>
          </div>
        </div>
      </div>
      <button className="primary" onClick={() => done(weight, reps)}>{today ? "Guardar cambio" : "Serie hecha"}</button>
      {!today && previous && <p className="muted small-text">La última vez: {setText(previous, e.timed)}</p>}
      {next && <p className="small-text"><span className="muted">Después: </span><strong>{next.name}</strong></p>}
    </>
  )
}

function Rest({ left, total, e, setNo, logged, rate, adjust, skip }: {
  left: number; total: number; e?: StrengthExercise; setNo: number; logged: [StrengthExercise, LoggedSet] | null
  rate: (rir: number) => void; adjust: (s: number) => void; skip: () => void
}) {
  const r = 52, c = 2 * Math.PI * r
  const last = e && (e.done.find((d) => d.set === setNo) ?? e.previous.find((d) => d.set === setNo))
  return (
    <>
      <div className="rest-ring" role="timer" aria-label={`Descanso: quedan ${clock(left)}`}>
        <svg viewBox="0 0 120 120" aria-hidden="true">
          <circle cx="60" cy="60" r={r} className="ring-track" />
          <circle cx="60" cy="60" r={r} className="ring-left" strokeDasharray={c} strokeDashoffset={c * (1 - left / Math.max(total, 1))} />
        </svg>
        <div><span className="mono rest-clock">{clock(left)}</span><br /><span className="muted small">descanso de {clock(total)}</span></div>
      </div>
      <div className="row rest-actions">
        <button className="secondary" onClick={() => adjust(-15)}>−15 s</button>
        <button className="secondary" onClick={() => adjust(15)}>+15 s</button>
        <button className="secondary" onClick={skip}>Saltar</button>
      </div>
      {logged && (
        <section className="module ok-module">
          <span className="muted small">{logged[0].name} · serie {logged[1].set} apuntada</span>
          <strong>{setText(logged[1], logged[0].timed)}</strong>
          {/* Opcional: con lo que te quedaba, Brivel no sube el peso si fuiste más cerca del fallo de lo indicado */}
          {logged[0].rir != null && (
            <fieldset className="rir">
              <legend className="muted small">¿Cuántas más podías hacer?</legend>
              {[0, 1, 2, 3, 4].map((n) => (
                <button key={n} className={`mono${logged[1].rir === n ? " on" : ""}`} aria-pressed={logged[1].rir === n}
                  aria-label={`Me quedaban ${n === 4 ? "4 o más" : n}`} onClick={() => rate(n)}>{n === 4 ? "4+" : n}</button>
              ))}
            </fieldset>
          )}
        </section>
      )}
      {e && (
        <section className="module">
          <span className="muted small">Ahora</span>
          <strong>{e.name} · serie {setNo}</strong>
          {last && <span className="muted small-text">La última vez: {setText(last, e.timed)}</span>}
        </section>
      )}
    </>
  )
}

/** Un ejercicio en la vista Sesión: una fila por serie con la última vez, lo de hoy y la serie actual marcada. */
function SessionRows({ e, isCurrent, currentSet, open }: { e: StrengthExercise; isCurrent: boolean; currentSet: number; open: (n: number) => void }) {
  return (
    <section className="module">
      <div className="row between"><strong>{e.name}</strong><span className="mono muted small">{e.done.length}/{e.sets}</span></div>
      <table className="sets">
        <thead><tr><th>Serie</th><th>Anterior</th><th>{e.timed ? "Peso" : "Kg"}</th><th>{e.timed ? "Seg" : "Reps"}</th><th><span className="sr-only">Hecha</span></th></tr></thead>
        <tbody>
          {Array.from({ length: Math.max(e.sets, e.done.length) }, (_, i) => i + 1).map((n) => {
            const today = e.done.find((d) => d.set === n)
            const before = e.previous.find((d) => d.set === n)
            const cls = today ? "done" : isCurrent && n === currentSet ? "now" : ""
            return (
              <tr key={n} className={cls} onClick={() => open(n)}>
                <td className="mono">
                  <button className="row-open" aria-label={`Abrir la serie ${n}`} onClick={(ev) => { ev.stopPropagation(); open(n) }}>{n}</button>
                </td>
                <td className="mono muted">{before ? setText(before, e.timed) : "—"}</td>
                <td><span className="cell mono">{today ? kgText(today.weightKg) : "—"}</span></td>
                <td><span className="cell mono">{today ? amount(today.reps, e.timed) : "—"}</span></td>
                <td><span className="tick"><Icon src={check} size={16} /></span></td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </section>
  )
}

function Finished({ other, close }: { other: string[]; close: () => void }) {
  return (
    <section className="module ok-module">
      <div className="row"><Icon src={check} size={22} /><h3>Sesión completa</h3></div>
      <p className="muted small-text">Queda apuntada: la próxima vez Brivel te propone el peso con estas series.</p>
      {other.map((o) => <p key={o} className="muted small-text">{o}</p>)}
      <button className="primary" onClick={close}>Terminar</button>
    </section>
  )
}
