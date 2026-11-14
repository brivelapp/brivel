// Progreso, como ProgressScreen.kt: tu cuerpo frente a tu rango habitual, tu forma, la actividad semanal y lo último hecho
import { useState } from "react"
import type { WebApp } from "brivel-shared"
import type { Metric, Milestone, Point, ProgressScreen, RecentActivity } from "./types"
import { Icon, SPORT_ICON, type Act } from "./Today"

const BODY = ["sleep", "sleepScore", "hrv", "restingHR", "weight", "steps"]
const fmt = (v: number | null) => v == null ? "—" : v >= 20 ? String(Math.round(v)) : String(Math.round(v * 10) / 10).replace(".", ",")
/** Con un decimal también por encima de 20: en una medida o un 1RM, medio centímetro o medio kilo cuentan. */
export const fmt1 = (v: number | null) => v == null ? "—" : String(Math.round(v * 10) / 10).replace(".", ",")

/** Línea de evolución con tu rango de fondo (SVG que escala al ancho). */
export function Sparkline({ points, low, high, warn = false }: { points: Point[]; low: number | null; high: number | null; warn?: boolean }) {
  if (points.length < 2) return null
  const values = points.map((p) => p.value)
  const min = Math.min(...values, low ?? Infinity)
  const max = Math.max(...values, high ?? -Infinity)
  const span = max - min || 1
  const y = (v: number) => 60 * (1 - (v - min) / span) + 2
  const path = values.map((v, i) => `${i ? "L" : "M"}${(i / (values.length - 1)) * 100},${y(v)}`).join(" ")
  return (
    <svg className="spark" viewBox="0 0 100 64" preserveAspectRatio="none" aria-hidden="true">
      {low != null && high != null && <rect x="0" y={y(high)} width="100" height={y(low) - y(high)} className="spark-band" />}
      <path d={path} className={warn ? "spark-line warn" : "spark-line"} vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

function MetricRow({ m }: { m: Metric }) {
  const [open, setOpen] = useState(false)
  const { normalLow: low, normalHigh: high, latest } = m
  const ranged = low != null && high != null && latest != null && fmt(high) !== fmt(low)
  let rangeText = "", good = true, mark = 0, band: [number, number] = [0, 0]
  if (ranged) {
    const values = m.points.map((p) => p.value)
    const lo = Math.min(low, ...values), hi = Math.max(high, ...values), pad = (hi - lo) * 0.15
    const f = (v: number) => Math.min(1, Math.max(0, (v - (lo - pad)) / (hi - lo + 2 * pad || 1))) * 100
    const inRange = latest >= low && latest <= high
    // Fuera de rango hacia el lado bueno (más VO2max, menos pulso en reposo) no es un aviso
    good = inRange || (m.higherIsBetter === true && latest > high) || (m.higherIsBetter === false && latest < low)
    const range = low < 0 ? `${fmt(low)} a ${fmt(high)}` : `${fmt(low)}–${fmt(high)}`
    rangeText = inRange ? `Dentro de tu rango habitual (${range})` : latest > high ? `Por encima de tu rango habitual (${range})` : `Por debajo de tu rango habitual (${range})`
    mark = f(latest); band = [f(low), f(high)]
  }
  return (
    <button className="metric" aria-expanded={open} onClick={() => setOpen(!open)}>
      <span className="row between"><span className="metric-label">{m.label}</span>
        <span className="mono metric-value">{(m.key.startsWith("measure-") ? fmt1 : fmt)(latest)}{m.unit && ` ${m.unit}`}</span></span>
      {m.source && <span className="muted small source">{m.source}</span>}
      {ranged && (
        <>
          <span className="range" aria-hidden="true">
            <span className="range-band" style={{ left: `${band[0]}%`, width: `${band[1] - band[0]}%` }} />
            <span className={`range-mark${good ? "" : " warn"}`} style={{ left: `${mark}%` }} />
          </span>
          <span className={`small${good ? " muted" : " caution-text"}`}>{rangeText}</span>
        </>
      )}
      {open && <><Sparkline points={m.points} low={low} high={high} /><span className="muted small-text">{m.explanation}</span></>}
    </button>
  )
}

function Recent({ r, core }: { r: RecentActivity; core: WebApp }) {
  const [open, setOpen] = useState(false)
  const total = r.zoneMinutes.reduce((a, b) => a + b, 0)
  return (
    <button className="metric" aria-expanded={open} onClick={() => setOpen(!open)}>
      <span className="row">
        <span className="badge"><Icon src={SPORT_ICON[r.sport] ?? SPORT_ICON.strength} size={18} /></span>
        <span className="grow"><span className="metric-label">{r.title}</span><br />{r.source && <><span className="muted small source">{r.source}</span><br /></>}<span className="muted small">{core.dateLabel(r.date)}</span></span>
        <span className="mono">{r.minutes}′</span>
      </span>
      {open && r.detail && <span className="muted small-text">{r.detail}</span>}
      {open && total > 0 && (
        <>
          <span className="zones-bar">{r.zoneMinutes.map((m, i) => m > 0 && <span key={i} style={{ flex: m, background: `var(--z${Math.min(i + 1, 6)})` }} />)}</span>
          <span className="muted small">{r.zoneMinutes.map((m, i) => (m > 0 ? `Z${i + 1} ${m}′` : null)).filter(Boolean).join(" · ")}</span>
        </>
      )}
    </button>
  )
}

/** Mejores marcas, semanas seguidas cumpliendo la OMS y récords de fuerza; los umbrales, aparte. */
function Milestones({ core, screen }: { core: WebApp; screen: ProgressScreen }) {
  const row = (m: Milestone) => (
    <div key={m.title} className="row between">
      <span>{m.title}{m.date && <span className="muted small"> · {core.dateLabel(m.date)}</span>}
        {m.source && <span className="muted small source"> · {m.source}</span>}</span>
      <span className="mono nowrap">{m.value}</span>
    </div>
  )
  return (
    <>
      {screen.milestones.length + screen.strengthRecords.length > 0 && (
        <section className="module">
          <h3>Tus hitos</h3>
          {screen.milestones.map(row)}
          {screen.strengthRecords.length > 0 && <p className="muted small-text">Lo más pesado que has levantado en cada ejercicio</p>}
          {screen.strengthRecords.map(row)}
        </section>
      )}
      {screen.thresholds.length > 0 && (
        <section className="module">
          <h3>Tus umbrales</h3>
          <p className="muted small-text">De intervals.icu: con ellos salen tus zonas. Cámbialos allí si haces un test.</p>
          {screen.thresholds.map((t) => (
            <div key={t.label} className="row between"><span>{t.label}<br /><span className="muted small">{t.explanation}</span></span><span className="mono nowrap">{t.value}</span></div>
          ))}
        </section>
      )}
    </>
  )
}

/** Medidas corporales apuntadas a mano (Training.kt): su evolución y el formulario para apuntar la de hoy. */
function Measures({ core, act, screen }: { core: WebApp; act: Act; screen: ProgressScreen }) {
  const kinds: [string, string, string][] = JSON.parse(core.measuresJson())
  const [kind, setKind] = useState(kinds[0][0])
  const [value, setValue] = useState("")
  const unit = kinds.find(([k]) => k === kind)?.[2] ?? ""
  return (
    <section className="module">
      <h3>Tus medidas</h3>
      <p className="muted small-text">Apunta tu peso o tus perímetros con una cinta para ver cómo cambian. La de hoy sustituye a la que ya hubiera hoy.</p>
      {screen.measures.map((m) => <MetricRow key={m.key} m={m} />)}
      <div className="row wrap">
        <label className="field small-field">Medida
          <select value={kind} onChange={(e) => setKind(e.target.value)}>
            {kinds.map(([k, label]) => <option key={k} value={k}>{label}</option>)}
          </select>
        </label>
        <label className="field small-field">Valor ({unit})
          <input inputMode="decimal" value={value} onChange={(e) => setValue(e.target.value.replace(/[^\d.,]/g, ""))} />
        </label>
        <button className="secondary" onClick={() => { act(() => { core.logMeasure(kind, value); setValue("") }, "Medida apuntada") }}>Apuntar</button>
      </div>
    </section>
  )
}

export function Progress({ core, act, openProfile }: { core: WebApp; act: Act; openProfile: () => void }) {
  const screen: ProgressScreen = JSON.parse(core.progressJson())
  const body = screen.metrics.filter((m) => BODY.includes(m.key))
  const training = screen.metrics.filter((m) => !BODY.includes(m.key))
  const last = screen.weeklyMinutes.at(-1)?.value ?? 0
  const top = Math.max(...screen.weeklyMinutes.map((p) => p.value), 180)
  const warn = screen.status?.label === "Sobrecarga" || screen.status?.label === "Perdiendo forma"
  return (
    <main className="wide">
      <header className="page-head"><div><p className="muted">Últimos 90 días</p><h1>Progreso</h1></div></header>
      {!screen.linked ? (
        <div className="profile-grid">
        <section className="module">
          <p className="muted">El navegador no puede leer Apple Salud ni Health Connect. Para ver aquí tu sueño, tu VFC o tu pulso en
            reposo, actívalo en la app del móvil y sincroniza: Perfil → «Usar en otros dispositivos».</p>
          <button className="primary" onClick={openProfile}>Ir a Perfil</button>
        </section>
        <Measures core={core} act={act} screen={screen} />
        <Milestones core={core} screen={screen} />
        </div>
      ) : (
        <div className="profile-grid">
          {body.length > 0 && (
            <section className="module">
              <div className="row between"><h3>Tu cuerpo</h3><span className="muted small">último dato · banda = tu rango</span></div>
              {body.map((m) => <MetricRow key={m.key} m={m} />)}
            </section>
          )}
          {(screen.status || training.length > 0) && (
            <section className="module">
              {screen.status && (
                <>
                  <h3>Forma: {screen.status.label.toLowerCase()}</h3>
                  {screen.garminNote && <p className="muted small source">{screen.garminNote}</p>}
                  {(() => { const ctl = screen.metrics.find((m) => m.key === "ctl"); return ctl && <Sparkline points={ctl.points} low={null} high={null} warn={warn} /> })()}
                  <p className="muted">{screen.status.text}</p>
                </>
              )}
              {training.map((m) => <MetricRow key={m.key} m={m} />)}
            </section>
          )}
          <section className="module">
            <div className="row between"><h3>Actividad semanal</h3><span className="mono">{Math.round(last)} / 150 min</span></div>
            <p className="muted small-text">La OMS recomienda al menos 150 minutos moderados (o 75 intensos) a la semana.</p>
            <div className="week-bars" role="img" aria-label={`Minutos por semana; esta semana, ${Math.round(last)}`}>
              <span className="target" style={{ bottom: `${(150 / top) * 100}%` }} />
              {screen.weeklyMinutes.map((p) => <span key={p.date} className={p.value >= 150 ? "on" : ""} style={{ height: `${(p.value / top) * 100}%` }} />)}
            </div>
          </section>
          {screen.predictions.length > 0 && (
            <section className="module">
              <h3>Tiempos previstos</h3>
              {screen.garminNote && <p className="muted small source">{screen.garminNote}</p>}
              {screen.predictions.map((p) => <p key={p.distance} className="row between"><span>{p.distance}</span><span className="mono">{p.time}</span></p>)}
              {screen.predictionSource && <p className="muted small-text">Calculado con {screen.predictionSource} (fórmula de Daniels).</p>}
            </section>
          )}
          {screen.recent.length > 0 && (
            <section className="module">
              <h3>Tus últimos entrenamientos</h3>
              {screen.recent.map((r, i) => <Recent key={i} r={r} core={core} />)}
            </section>
          )}
          <Measures core={core} act={act} screen={screen} />
          <Milestones core={core} screen={screen} />
        </div>
      )}
    </main>
  )
}
