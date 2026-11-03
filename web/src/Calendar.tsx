// Calendario mensual, como CalendarScreen.kt: el pasado muestra lo que hiciste y el futuro, el plan; el día elegido, debajo
import { useState } from "react"
import type { WebApp } from "brivel-shared"
import type { DayView, MonthScreen } from "./types"
import { DayContent, Icon, SPORT_ICON, type Act } from "./Today"
import check from "../../iosApp/iosApp/Assets.xcassets/check.imageset/check.svg"

const LETTERS = ["L", "M", "X", "J", "V", "S", "D"]
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`

export function Calendar({ core, act }: { core: WebApp; act: Act }) {
  const now = new Date()
  const [ym, setYm] = useState({ year: now.getFullYear(), month: now.getMonth() + 1 })
  const [selected, setSelected] = useState(iso(now))
  const screen: MonthScreen = JSON.parse(core.monthJson(ym.year, ym.month))
  const day: DayView = JSON.parse(core.dayJson(selected))
  const move = (delta: number) => {
    const m = ym.month + delta
    setYm(m < 1 ? { year: ym.year - 1, month: 12 } : m > 12 ? { year: ym.year + 1, month: 1 } : { year: ym.year, month: m })
  }
  const done = screen.weeks.flat().find((d) => d.date === selected)?.done ?? []

  return (
    <main className="wide">
      <div className="layout">
        <div className="col">
          <div className="row month-head">
            <button className="back" aria-label="Mes anterior" onClick={() => move(-1)}>‹</button>
            <h1 className="month-title">{screen.title}</h1>
            <button className="back" aria-label="Mes siguiente" onClick={() => move(1)}>›</button>
          </div>
          <div className="month" role="grid" aria-label={screen.title}>
            {LETTERS.map((l) => <span key={l} className="muted small month-letter" aria-hidden>{l}</span>)}
            {screen.weeks.flat().map((d) => {
              const doneSports = [...d.done.map((a) => a.sport), ...d.manual]
              return (
                <button key={d.date} role="gridcell" aria-selected={d.date === selected}
                  className={`day-cell${d.inMonth ? "" : " out"}${d.isToday ? " today" : ""}${d.date === selected ? " selected" : ""}`}
                  aria-label={`${core.dateLabel(d.date)}${d.planned.length ? `, ${d.planned.length} planificada` : ""}${doneSports.length ? ", hecha" : ""}`}
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
              <div><p className="state-title">{a.title}</p><p className="muted">{a.minutes} min{a.start ? ` a las ${a.start}` : ""}</p></div>
            </section>
          ))}
          <DayContent key={selected} core={core} day={day} act={act} />
        </aside>
      </div>
    </main>
  )
}
