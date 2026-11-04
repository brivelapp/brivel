// Calendario mensual, como CalendarScreen.kt: el pasado muestra lo que hiciste y el futuro, el plan; el día elegido, debajo
import { useEffect, useRef, useState, type KeyboardEvent } from "react"
import type { WebApp } from "brivel-shared"
import type { DayView, MonthScreen } from "./types"
import { DayContent, Icon, SPORT_ICON, type Act } from "./Today"
import check from "../../iosApp/iosApp/Assets.xcassets/check.imageset/check.svg"

const LETTERS = ["L", "M", "X", "J", "V", "S", "D"]
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`

export function Calendar({ core, act, openStrength }: { core: WebApp; act: Act; openStrength: (date: string) => void }) {
  const now = new Date()
  const [ym, setYm] = useState({ year: now.getFullYear(), month: now.getMonth() + 1 })
  const [selected, setSelected] = useState(iso(now))
  const screen: MonthScreen = JSON.parse(core.monthJson(ym.year, ym.month))
  const day: DayView = JSON.parse(core.dayJson(selected))
  const move = (delta: number) => {
    const m = ym.month + delta
    setYm(m < 1 ? { year: ym.year - 1, month: 12 } : m > 12 ? { year: ym.year + 1, month: 1 } : { year: ym.year, month: m })
  }
  const cells = screen.weeks.flat()
  const tabStop = cells.some((d) => d.date === selected) ? selected : cells.find((d) => d.inMonth)?.date
  const done = screen.weeks.flat().find((d) => d.date === selected)?.done ?? []
  // Rejilla ARIA: una sola parada del tabulador (el día elegido) y flechas para moverse; Inicio y Fin, la semana
  const grid = useRef<HTMLDivElement>(null)
  const keyMoved = useRef(false)
  useEffect(() => {
    if (!keyMoved.current) return
    keyMoved.current = false
    grid.current?.querySelector<HTMLButtonElement>(`[data-date="${selected}"]`)?.focus()
  }, [selected, ym])
  const onKey = (e: KeyboardEvent) => {
    const [y, m, d] = selected.split("-").map(Number)
    const weekday = (new Date(y, m - 1, d).getDay() + 6) % 7
    const step = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7, Home: -weekday, End: 6 - weekday }[e.key]
    if (step === undefined) return
    e.preventDefault()
    const next = new Date(y, m - 1, d + step)
    keyMoved.current = true
    setSelected(iso(next))
    setYm({ year: next.getFullYear(), month: next.getMonth() + 1 })
  }

  return (
    <main className="wide">
      <div className="layout">
        <div className="col">
          <div className="row month-head">
            <button className="back" aria-label="Mes anterior" onClick={() => move(-1)}>‹</button>
            <h1 className="month-title">{screen.title}</h1>
            <button className="back" aria-label="Mes siguiente" onClick={() => move(1)}>›</button>
          </div>
          <div className="month" role="grid" aria-label={screen.title} ref={grid} onKeyDown={onKey}>
            {LETTERS.map((l) => <span key={l} className="muted small month-letter" aria-hidden>{l}</span>)}
            {screen.weeks.flat().map((d) => {
              const doneSports = [...d.done.map((a) => a.sport), ...d.manual]
              return (
                <button key={d.date} role="gridcell" aria-selected={d.date === selected} data-date={d.date}
                  tabIndex={d.date === tabStop ? 0 : -1}
                  className={`day-cell${d.inMonth ? "" : " out"}${d.isToday ? " today" : ""}${d.date === selected ? " selected" : ""}`}
                  aria-label={`${core.dateLabel(d.date)}${d.planned.length ? `, ${d.planned.length} ${d.planned.length === 1 ? "planificada" : "planificadas"}` : ""}${doneSports.length ? ", hecha" : ""}`}
                  onClick={() => setSelected(d.date)}>
                  <span className="mono">{d.dayOfMonth}</span>
                  <span className="row day-icons">{d.planned.slice(0, 2).map((p, i) => <Icon key={i} src={SPORT_ICON[p.sport] ?? SPORT_ICON.running} size={14} />)}</span>
                  {doneSports.length > 0 && <span className="row day-done"><Icon src={check} size={14} />{doneSports.length > 1 && doneSports.length}</span>}
                </button>
              )
            })}
          </div>
          <p className="row muted small-text legend">
            <Icon src={SPORT_ICON.running} size={16} />planificada <Icon src={check} size={16} />hecha
          </p>
        </div>
        <aside className="col side">
          <h2>{core.dateLabel(selected)}</h2>
          {done.map((a, i) => (
            <section key={i} className="state ok">
              <div><p className="state-title">{a.title}</p>{a.source && <p className="muted small source">{a.source}</p>}<p className="muted">{a.minutes} min{a.start ? ` a las ${a.start}` : ""}</p></div>
            </section>
          ))}
          <DayContent key={selected} core={core} day={day} act={act} openStrength={openStrength} />
        </aside>
      </div>
    </main>
  )
}
