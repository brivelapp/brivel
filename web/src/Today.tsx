import { useEffect, useState, type ReactNode } from "react"
import { WebApp } from "brivel-shared"
import type { DayView, PlanItem, Reason, TodayScreen, Tone } from "./types"
import { clearKeys } from "./sync"
import { sendToWatch } from "./garmin"
import { Segmented } from "./ProfileParts"
import { TodayWidgets } from "./Widgets"
import running from "../../iosApp/iosApp/Assets.xcassets/sport_running.imageset/sport_running.svg"
import cycling from "../../iosApp/iosApp/Assets.xcassets/sport_cycling.imageset/sport_cycling.svg"
import swimming from "../../iosApp/iosApp/Assets.xcassets/sport_swimming.imageset/sport_swimming.svg"
import walking from "../../iosApp/iosApp/Assets.xcassets/sport_walking.imageset/sport_walking.svg"
import strength from "../../iosApp/iosApp/Assets.xcassets/sport_strength.imageset/sport_strength.svg"
import stateOk from "../../iosApp/iosApp/Assets.xcassets/state_ok.imageset/state_ok.svg"
import stateCaution from "../../iosApp/iosApp/Assets.xcassets/state_caution.imageset/state_caution.svg"
import stateRest from "../../iosApp/iosApp/Assets.xcassets/state_rest.imageset/state_rest.svg"
import check from "../../iosApp/iosApp/Assets.xcassets/check.imageset/check.svg"

export const SPORT_ICON: Record<string, string> = { running, cycling, swimming_pool: swimming, swimming_open: swimming, walking, strength }
const STATE: Record<Tone, [string, string]> = { OK: ["ok", stateOk], CAUTION: ["caution", stateCaution], REST: ["rest", stateRest] }

/** Si la pantalla es de ordenador (el mismo corte que styles.css). */
export function useWide() {
  const query = "(min-width: 900px)"
  const [wide, setWide] = useState(() => matchMedia(query).matches)
  useEffect(() => {
    const m = matchMedia(query)
    const on = () => setWide(m.matches)
    m.addEventListener("change", on)
    return () => m.removeEventListener("change", on)
  }, [])
  return wide
}

/** Ejecuta una acción del núcleo y vuelve a pintar; si falla, enseña su texto (como el aviso de las apps). */
export type Act = (f: () => void | string, success?: string) => void  // si f devuelve texto, ese es el aviso

// Los SVG de iOS son plantillas en negro: como máscara toman el color del texto
export const Icon = ({ src, label, size = 20 }: { src: string; label?: string; size?: number }) => (
  <span className="icon" role={label ? "img" : undefined} aria-label={label} aria-hidden={label ? undefined : true}
    style={{ width: size, height: size, maskImage: `url("${src}")`, WebkitMaskImage: `url("${src}")` }} />
)

/** Como minutesText de las apps: 4′, 30″ o 2′30″. */
const minutesText = (m: number) =>
  m < 1 ? `${Math.trunc(m * 60)}″` : m % 1 === 0 ? `${m}′` : `${Math.trunc(m)}′${String(Math.trunc((m % 1) * 60)).padStart(2, "0")}″`

/** Motivo con su etiqueta de evidencia: se toca para ver qué significa. */
function ReasonRow({ core, reason, tone = "caution" }: { core: WebApp; reason: Reason; tone?: string }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="reason">
      <span className={`bar ${tone}`} />
      <div>
        <p>{reason.text}</p>
        {reason.evidence && (
          <button className="tag" aria-expanded={open} onClick={() => setOpen(!open)}>{reason.evidence}</button>
        )}
        {open && reason.evidence && <p className="muted small">{core.evidenceMeaning(reason.evidence)}</p>}
      </div>
    </div>
  )
}

// Del 1 al 5, con el mismo selector que el Perfil
const RATINGS: [string, string][] = [1, 2, 3, 4, 5].map((n) => [String(n), n === 1 ? "1 mal" : n === 5 ? "5 bien" : String(n)])

/** Sin reloj vinculado: sueño, energía y agujetas de 1 (mal) a 5 (bien) ajustan la sesión de hoy. */
function ReadinessCard({ core, act, ask }: { core: WebApp; act: Act; ask: boolean }) {
  const [answers, setAnswers] = useState({ sleep: 3, energy: 3, soreness: 3 })
  const [open, setOpen] = useState(false)
  const questions = [["sleep", "Sueño"], ["energy", "Energía"], ["soreness", "Agujetas o molestias"]] as const
  if (!ask) return <p className="muted small-text">Hoy ya está ajustado con tus respuestas o con los datos de tu reloj.</p>
  if (!open) return <button className="secondary" onClick={() => setOpen(true)}>Responder 3 preguntas</button>
  return (
    <div className="stack">
      <p className="muted small-text">Si dormiste mal o estás cargado, la sesión de hoy será más suave.</p>
      {questions.map(([key, label]) => (
        <div key={key} className="stack tight">
          <span>{label}</span>
          <Segmented options={RATINGS} selected={String(answers[key])} label={label}
            onSelect={(v) => setAnswers({ ...answers, [key]: Number(v) })} />
        </div>
      ))}
      <button className="primary" onClick={() => act(() => core.answerReadiness(answers.sleep, answers.energy, answers.soreness))}>
        Ajustar hoy
      </button>
    </div>
  )
}

export const localToday = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }

function SessionCard({ core, item, done, children }: { core: WebApp; item: PlanItem; done: boolean; children?: ReactNode }) {
  const label = core.sportLabel(item.sport)
  return (
    <section className={`module${done ? " done" : ""}`}>
      <div className="row">
        <span className="badge"><Icon src={SPORT_ICON[item.sport] ?? strength} label={label} /></span>
        <span className="sport">{item.chosen ? `${label} · elegida por ti` : label}</span>
        {done && <span className="hecha"><Icon src={check} size={18} />Hecha</span>}
        <span className="mono big">{item.doneMin ?? item.durationMin}</span><span className="mono muted">&nbsp;min</span>
      </div>
      <h2>{item.title}</h2>
      {/* La cifra manda (2026-11-18): ritmo, vatios o pulso en grande; sin datos aún, cómo saber si vas bien */}
      {/* Hecha con una actividad: sus datos mandan; lo planificado queda debajo, en pequeño */}
      {item.doneDetail
        ? <div className="target"><span className="mono actual">{item.doneDetail}</span>
            <span className="muted small-text">Planificado: {item.durationMin} min{item.target ? ` a ${item.target}` : ""}</span></div>
        : item.target
        ? <div className="target"><span className="mono">{item.target}</span>{item.targetNote && <strong>{item.targetNote}</strong>}</div>
        : <p className="muted">{item.hint}</p>}
      {item.steps.length > 0 && (
        <ul className="steps">
          {item.steps.map((s, i) => (
            <li key={i}>
              <span className="zone" style={{ background: `var(--${s.intensityZone.toLowerCase()}, var(--chip))`, color: s.intensityZone === "Z6" ? "#FFFFFF" : undefined }}>{s.intensityZone}</span>
              <span className="step">
                <strong>{s.name.charAt(0).toUpperCase() + s.name.slice(1)}</strong>
                {s.target && <span className="mono muted small">{s.target}</span>}
                {item.testKind && s.detail && <span className="muted small">{s.detail}</span>}
              </span>
              <span className="mono muted">{s.reps > 1 ? `${s.reps}×` : ""}{s.meters != null ? `${s.meters} m` : minutesText(s.minutes)}</span>
            </li>
          ))}
        </ul>
      )}
      {item.exercises.map((e) => <p key={e}>{e}</p>)}
      {item.notes && <p className="muted small-text">{item.notes}</p>}
      {children}
    </section>
  )
}

const isDone = (day: DayView, item: PlanItem) => day.doneSports.includes(item.slot) || day.doneSports.includes(item.sport)

/** Lo de una sesión en una línea: los ejercicios de la fuerza, o la cifra o la pista del cardio. */
const summary = (item: PlanItem) =>
  item.sport === "strength" ? item.exercises.map((e) => e.split(":")[0]).join(" · ") : item.doneDetail ?? item.target ?? item.hint

/** La sesión de hoy, compacta: el detalle se abre con «Detalles ›» (en el ordenador, a la derecha). */
function CompactSession({ core, day, item, act, openStrength, wide, selected, open }:
  { core: WebApp; day: DayView; item: PlanItem; act: Act; openStrength: (date: string) => void; wide: boolean; selected: boolean; open: () => void }) {
  const done = isDone(day, item)
  const label = core.sportLabel(item.sport)
  const strengthToday = item.sport === "strength" && day.date <= localToday()
  return (
    <section className={`module compact${done ? " done" : ""}${selected ? " selected" : ""}`} aria-label={item.title}>
      <div className="row">
        <span className="badge"><Icon src={SPORT_ICON[item.sport] ?? strength} label={label} /></span>
        <span className="sport">{item.chosen ? `${label} · elegida por ti` : label}</span>
        {done && <span className="hecha"><Icon src={check} size={18} />Hecha</span>}
        <span className="mono big">{item.doneMin ?? item.durationMin}</span><span className="mono muted">&nbsp;min</span>
      </div>
      <h2>{item.title}</h2>
      <p className="muted">{summary(item)}</p>
      {/* En el ordenador las acciones van en el detalle de la derecha; en el móvil, aquí lo de todos los días */}
      {!wide && strengthToday && (
        <button className="primary" data-strength={day.date} onClick={() => openStrength(day.date)}>{done ? "Ver las series" : "Empezar"}</button>
      )}
      <div className="row">
        {!wide && !done && <button className="text grow" onClick={() => act(() => core.markDone(day.date, item.sport))}>La he hecho</button>}
        {wide && <span className="grow" />}
        <button className="text" aria-expanded={wide ? selected : undefined} onClick={open}>Detalles ›</button>
      </div>
    </section>
  )
}

/** Las sesiones de hoy con sus acciones: hecha, cambiar de deporte, apuntar fuerza, hoy no puedo (DayContent de Android). */
export function DayContent({ core, day, act, openStrength, synced, only }:
  { core: WebApp; day: DayView; act: Act; openStrength: (date: string) => void; synced?: (m: string | null) => void; only?: string }) {
  const [openSlot, setOpenSlot] = useState<string | null>(null)
  const [movingSlot, setMovingSlot] = useState<string | null>(null)  // eligiendo a qué día mover esa sesión
  const [sending, setSending] = useState(false)
  const send = (sport: string) => {
    setSending(true)
    sendToWatch(core, sport).then(() => synced?.(core.watchSentText), (e) => synced?.(e instanceof Error && e.message ? e.message : "No se pudo enviar"))
      .finally(() => setSending(false))
  }
  const changeable = core.canChangeDay(day.date)
  return (
    <>
      {day.items.length === 0 && changeable && (
        <section className="module">
          <button className="secondary" onClick={() => act(() => core.preferTrain(day.date))}>Prefiero entrenar hoy</button>
        </section>
      )}
      {!day.isToday && day.items.length === 0 && day.note && <p className="muted">{day.note}</p>}
      <div className="sessions">
      {day.items.filter((item) => only == null || item.slot === only).map((item) => {
        const done = day.doneSports.includes(item.slot) || day.doneSports.includes(item.sport)
        const open = openSlot === item.slot
        const moving = movingSlot === item.slot
        const targets: string[] = !done && !day.isPast ? JSON.parse(core.moveTargetsJson(day.date, item.slot)) : []
        return (
          <SessionCard key={item.slot} core={core} item={item} done={done}>
            <div className="row">
              {/* Fuerza: apuntar las series ya la cuenta como hecha; "La he hecho" queda para quien no apunta */}
              {item.sport === "strength" && day.date <= localToday() && (
                <button className="primary grow" data-strength={day.date} onClick={() => openStrength(day.date)}>{done ? "Ver las series" : "Empezar"}</button>
              )}
              {!done && (
                <button className={item.sport === "strength" && day.date <= localToday() ? "secondary" : "primary grow"}
                  onClick={() => act(() => core.markDone(day.date, item.sport))}>La he hecho</button>
              )}
              {!done && !day.isPast && item.alternatives.length > 0 && (
                <button className="secondary" aria-expanded={open} onClick={() => setOpenSlot(open ? null : item.slot)}>{open ? "Cerrar" : "Cambiar"}</button>
              )}
            </div>
            {item.movedFrom && <p className="muted small-text">La has movido aquí desde el {core.dateLabel(item.movedFrom).toLowerCase()}.</p>}
            {targets.length > 0 && (
              <button className="text" aria-expanded={moving} onClick={() => setMovingSlot(moving ? null : item.slot)}>{moving ? "No moverla" : "Moverla a otro día"}</button>
            )}
            {moving && (
              <div className="row wrap" role="group" aria-label="Mover a">
                {targets.map((t) => (
                  <button key={t} className={`secondary${t === item.movedFrom ? " on" : ""}`}
                    onClick={() => { act(() => core.moveSession(day.date, item.slot, t), t === item.movedFrom ? "Devuelta a su día" : "Movida"); setMovingSlot(null) }}>
                    {core.dateLabel(t)}{t === item.movedFrom ? " (su día)" : ""}
                  </button>
                ))}
              </div>
            )}
            {open && item.alternatives.map((alt) => (
              <div className="row" key={alt.sport}>
                <span className="badge small-badge"><Icon src={SPORT_ICON[alt.sport] ?? strength} label={core.sportLabel(alt.sport)} size={18} /></span>
                <span className="grow">{alt.title} · {alt.durationMin}′</span>
                <button className="text" onClick={() => { act(() => core.chooseAlternative(day.date, item.slot, alt.sport)); setOpenSlot(null) }}>Haré esta</button>
              </div>
            ))}
            {synced && day.isToday && core.garminConnected && (
              <button className="text" disabled={sending} onClick={() => send(item.sport)}>Enviar al reloj</button>
            )}
            {item.testKind && (day.isToday || day.isPast) && <TestResult core={core} act={act} date={day.date} kind={item.testKind} />}
            {item.chosen && <button className="text" onClick={() => act(() => core.clearChoice(day.date))}>Volver a la planificada</button>}
          </SessionCard>
        )
      })}
      </div>
      {day.adjustments.length > 0 && day.reasons.map((r, i) => <ReasonRow key={i} core={core} reason={r} tone="ok" />)}
      {changeable && day.items.length > 0 && (
        <button className="text muted" onClick={() => act(() => core.cannotTrain(day.date))}>Hoy no puedo entrenar</button>
      )}
    </>
  )
}

/** Privacidad, términos y código fuente (AGPL), y borrar lo guardado en este navegador, como en Perfil de las apps. */
export function Footer({ core, act }: { core: WebApp; act: Act }) {
  const erase = () => {
    if (!confirm("¿Borrar todos tus datos de este navegador? Si sincronizas, la copia del servidor se queda.")) return
    act(() => core.deleteAll(), "Datos borrados")
    clearKeys()
  }
  return (
    <footer className="legal">
      <a href="privacidad">Privacidad</a>
      <a href="terminos">Términos</a>
      <a href="https://github.com/brivelapp/brivel">Código fuente</a>
      <button className="link" onClick={erase}>Borrar mis datos</button>
    </footer>
  )
}

interface Race { name: string; sport: string; date: string; priority: string; distanceKm: number | null; trail: boolean }

/** Carreras preparatorias (B o C) antes de la principal: el plan afina unos días antes y recupera después. Como en las apps. */
function OtherRaces({ core, act }: { core: WebApp; act: Act }) {
  const mainJson = core.mainRaceJson()
  const main: Race | null = mainJson ? JSON.parse(mainJson) : null
  const blank = (): Race => ({ name: "", sport: main?.sport ?? "running", date: "", priority: "C", distanceKm: null, trail: false })
  const [adding, setAdding] = useState(false)
  const [race, setRace] = useState<Race>(blank)
  if (!main) return null
  const others: Race[] = JSON.parse(core.otherRacesJson())
  const today = new Date().toISOString().slice(0, 10)
  return (
    <details className="module">
      <summary><strong>Otras carreras antes de {main.name}</strong>{others.length > 0 && <span className="muted"> · {others.length}</span>}</summary>
      {others.map((r) => (
        <div className="row" key={r.date}>
          <span className="grow">{r.name}<br /><span className="muted small-text">{core.dateLabel(r.date)} · {r.priority === "B" ? "importante" : "de preparación"}</span></span>
          <button className="text" onClick={() => act(() => core.removeRace(r.date))}>Quitar</button>
        </div>
      ))}
      {adding ? (
        <form className="stack" onSubmit={(e) => { e.preventDefault(); act(() => { core.addRace(JSON.stringify(race)); setAdding(false); setRace(blank()) }, "Carrera añadida") }}>
          <label className="field">Nombre de la carrera<input value={race.name} onChange={(e) => setRace({ ...race, name: e.target.value })} /></label>
          <label className="field">Fecha<input type="date" min={today} max={main.date} value={race.date} onChange={(e) => setRace({ ...race, date: e.target.value })} /></label>
          <Segmented options={[["B", "Importante"], ["C", "De preparación"]]} selected={race.priority} onSelect={(priority) => setRace({ ...race, priority })} label="Prioridad" />
          <div className="row wrap"><button className="primary" type="submit">Añadir</button><button className="text" type="button" onClick={() => setAdding(false)}>Cancelar</button></div>
        </form>
      ) : (
        <>
          <p className="muted small-text">El plan afina unos días antes de cada una y te deja recuperar después. Elige importante (B) o de preparación (C).</p>
          <button className="secondary" onClick={() => setAdding(true)}>Añadir otra carrera</button>
        </>
      )}
    </details>
  )
}

/** Quien empieza: cómo fue su última carrera. Con las fáciles, Brivel aprende su ritmo suave (sin test). */
function RateRun({ core, act, run }: { core: WebApp; act: Act; run: NonNullable<TodayScreen["rateRun"]> }) {
  return (
    <section className="module">
      <h2 className="settings-title">{run.question}</h2>
      {run.source && <p className="muted small source">{run.source}</p>}
      <p className="mono">{run.detail}</p>
      <div className="row wrap" role="group" aria-label={run.question}>
        {core.efforts.map((e) => <button key={e} className="secondary" onClick={() => act(() => core.rateRun(run.id, e))}>{core.effortLabel(e)}</button>)}
      </div>
      <p className="muted small-text">{run.note}</p>
    </section>
  )
}

/** El resultado de un test de campo: con él, cada sesión lleva su ritmo, sus vatios o su pulso. */
function TestResult({ core, act, date, kind }: { core: WebApp; act: Act; date: string; kind: string }) {
  const [open, setOpen] = useState(false)
  const [main, setMain] = useState("")
  const [hr, setHr] = useState("")
  const [mainLabel, hrLabel] = core.testFields(kind)
  if (!open) return <button className="primary" onClick={() => { setMain(""); setHr(""); setOpen(true) }}>Apuntar el resultado</button>
  return (
    <form className="stack" onSubmit={(e) => { e.preventDefault(); act(() => { core.saveTestResult(date, kind, main, hr || null); setOpen(false) }, "Hecho: tus sesiones ya llevan cifras") }}>
      <label className="field">{mainLabel}<input value={main} inputMode="decimal" onChange={(e) => setMain(e.target.value)} /></label>
      {hrLabel && <label className="field">{hrLabel}<input value={hr} inputMode="numeric" onChange={(e) => setHr(e.target.value.replace(/\D/g, ""))} /></label>}
      <div className="row wrap"><button className="primary" type="submit">Guardar</button><button className="text" type="button" onClick={() => setOpen(false)}>Cancelar</button></div>
    </form>
  )
}

/** Sin plan y sin rutina hoy: la próxima, tus rutinas para hacer una hoy o un entreno libre; sin rutinas, crear la primera. */
function NoRoutineToday({ core, next, act, openStrength, openTrain }:
  { core: WebApp; next: string | null; act: Act; openStrength: (date: string) => void; openTrain: () => void }) {
  const routines: { id: string; name: string }[] = JSON.parse(core.routinesJson())
  const start = (id: string | null) => act(() => openStrength(core.startWorkout(id)))
  if (routines.length === 0) return (
    <section className="module">
      <h3>Empieza por tu rutina</h3>
      <p className="muted">Apunta sus ejercicios y los días que la haces. Esos días te saldrá aquí, lista para empezar, y Brivel guardará cada serie.</p>
      <button className="primary" onClick={openTrain}>Crear mi rutina</button>
      <button className="secondary" onClick={() => start(null)}>Entrenar sin rutina</button>
    </section>
  )
  return (
    <section className="module">
      <h3>Hoy no te toca rutina</h3>
      {next && <p className="muted">{next}. Si te apetece entrenar hoy, elige una o empieza en blanco.</p>}
      {routines.map((r) => <button key={r.id} className="secondary" onClick={() => start(r.id)}>Hacer «{r.name}» hoy</button>)}
      <button className="secondary" onClick={() => start(null)}>Entreno libre</button>
    </section>
  )
}

/** Hoy: el estado, la sesión y la semana. La portada, la bienvenida y el pie van en App.tsx. */
export function Today({ core, act, synced, openStrength, openTrain }:
  { core: WebApp; act: Act; synced: (m: string | null) => void; openStrength: (date: string) => void; openTrain: () => void }) {
  const screen: TodayScreen = JSON.parse(core.todayJson())
  const changeGoal = () => { if (confirm("¿Cambiar de objetivo? El plan se rehará al momento.")) act(() => core.abandonGoal()) }
  const [tone, stateIcon] = STATE[screen.tone]
  const wide = useWide()
  const [detail, setDetail] = useState<string | null>(null)  // la sesión abierta con «Detalles ›»
  const items = screen.day.items
  // En el ordenador el detalle va siempre a la derecha (decidido el 2026-11-23): de la elegida o, si no, de la primera
  const selected = items.some((i) => i.slot === detail) ? detail : wide ? items[0]?.slot ?? null : null
  if (!wide && selected) return (
    <main>
      <div className="row">
        <button className="back" aria-label="Volver a Hoy" onClick={() => setDetail(null)}>‹</button>
        <h1 className="grow">{items.find((i) => i.slot === selected)?.title}</h1>
      </div>
      <DayContent core={core} day={screen.day} act={act} openStrength={openStrength} synced={synced} only={selected} />
    </main>
  )
  return (
    <main className="wide">
      <header className="page-head">
        <div>
          <h1>Hoy</h1>
          <p className="muted">{screen.dateLabel}</p>
        </div>
        {screen.goalLine && <p className="muted goal">{screen.goalLine} · <button className="link" onClick={changeGoal}>Cambiar objetivo</button></p>}
        {screen.noPlan && <p className="goal"><button className="link" onClick={() => act(() => core.abandonGoal())}>Quiero un plan de Brivel</button></p>}
      </header>
      {!screen.goalFinished && <OtherRaces core={core} act={act} />}
      <div className="layout">
      <div className="col">
      {screen.goalFinished && (
        <section className="module">
          <h3>Tu carrera ya pasó</h3>
          <p className="muted">¿Qué quieres ahora? Otra carrera, mantener la forma o descansar unos días.</p>
          <button className="primary" onClick={() => act(() => core.abandonGoal())}>Elegir objetivo</button>
        </section>
      )}
      {screen.stateTitle && <section className={`state ${tone}`}>
        <span className="state-icon"><Icon src={stateIcon} size={22} /></span>
        <div>
          <p className="state-title">{screen.stateTitle}</p>
          {screen.garminNote && <p className="muted small source">{screen.garminNote}</p>}
          {screen.stateDetail.map((r, i) => <ReasonRow key={i} core={core} reason={r} tone={tone} />)}
        </div>
      </section>}
      {screen.noPlan && screen.day.items.length === 0 && <NoRoutineToday core={core} next={screen.nextRoutine} act={act} openStrength={openStrength} openTrain={openTrain} />}
      {screen.rateRun && <RateRun core={core} act={act} run={screen.rateRun} />}
      {items.length === 0
        ? <DayContent core={core} day={screen.day} act={act} openStrength={openStrength} synced={synced} />
        : items.map((item) => (
          <CompactSession key={item.slot} core={core} day={screen.day} item={item} act={act} openStrength={openStrength}
            wide={wide} selected={wide && item.slot === selected} open={() => setDetail(item.slot)} />
        ))}
      {/* Widgets (Widgets.tsx), debajo de la sesión */}
      <TodayWidgets core={core} act={act} screen={screen} readiness={<ReadinessCard core={core} act={act} ask={screen.askReadiness} />} />
      </div>
      {/* En el ordenador, el detalle de la sesión elegida a la derecha, fijo al desplazar */}
      {wide && selected && (
        <aside className="col side" aria-label="Detalle de la sesión">
          <DayContent core={core} day={screen.day} act={act} openStrength={openStrength} synced={synced} only={selected} />
        </aside>
      )}
      </div>
    </main>
  )
}
