// Piezas del perfil (lienzo de Pulso, opción B), las mismas que ProfileParts.kt y ProfileParts.swift de las apps
import { useRef, useState, type KeyboardEvent, type PointerEvent } from "react"
import type { WebApp } from "brivel-shared"
import { Icon, SPORT_ICON } from "./Today"

/** Forma del JSON de UserProfile (shared/.../app/Data.kt). */
export interface Profile {
  birthYear: number | null
  level: string
  strength: string
  cardio: string[]
  days: Record<string, number>
  [key: string]: unknown  // el resto (cribado, renombrar actividades) se guarda tal cual
}

const SPORT_NAME: Record<string, string> = { running: "Correr", cycling: "Bici", swimming_pool: "Nadar" }
const DAYS: [string, string, string][] = [["MON", "L", "Lunes"], ["TUE", "M", "Martes"], ["WED", "X", "Miércoles"],
  ["THU", "J", "Jueves"], ["FRI", "V", "Viernes"], ["SAT", "S", "Sábado"], ["SUN", "D", "Domingo"]]
export const STRENGTH_CHOICES: [string, string, string][] = [["gym", "Gimnasio", "Con máquinas, barras y mancuernas"],
  ["bodyweight", "En casa", "Con tu peso y poco material"], ["none", "Sin fuerza", "Solo resistencia"]]
export const LEVEL_CHOICES: [string, string, string][] = [["beginner", "Empiezo", "Hace meses que no entreno"],
  ["intermediate", "A veces", "Una o dos veces por semana"], ["advanced", "A menudo", "Tres o más veces por semana"]]

/** Correr, bici y nadar como pastillas con icono. Caminar no aparece: siempre cuenta. */
export function SportPicker({ core, cardio, onChange }: { core: WebApp; cardio: string[]; onChange: (c: string[]) => void }) {
  return (
    <div className="stack">
      <div className="pills">
        {core.pickableCardio.map((s) => {
          const on = cardio.includes(s)
          return (
            <button key={s} className={`pill${on ? " on" : ""}`} aria-pressed={on} onClick={() => onChange([...core.toggleCardio(cardio, s)])}>
              <Icon src={SPORT_ICON[s]} size={20} />{SPORT_NAME[s]}
            </button>
          )
        })}
      </div>
      <p className="muted small-text">Caminar siempre cuenta: no hace falta marcarlo.</p>
    </div>
  )
}

/**
 * La semana como barras: tocar o arrastrar pone esos minutos, de 15 en 15; abajo del todo, ese día queda libre. Con el
 * teclado, flecha arriba y abajo. `editable: false` en el Perfil: se cambia en un diálogo, porque en el móvil desplazar la
 * página movería las barras.
 */
export function WeekBars({ core, days, editable = true, onCommit }:
  { core: WebApp; days: Record<string, number>; editable?: boolean; onCommit?: (d: Record<string, number>) => void }) {
  const [live, setLive] = useState<Record<string, number> | null>(null)
  const shown = live ?? days
  const latest = useRef(shown)
  latest.current = shown
  const top = core.maxDayMinutes
  const count = Object.values(shown).filter((m) => m > 0).length
  const set = (d: string, m: number) => setLive({ ...latest.current, [d]: Math.min(top, Math.max(0, m)) })
  const commit = () => { onCommit?.(latest.current); setLive(null) }
  // La barra mide el alto menos los 22 px de los minutos: el dedo marca su tope
  const at = (e: PointerEvent<HTMLDivElement>) => {
    const r = e.currentTarget.getBoundingClientRect()
    return Math.round((r.bottom - e.clientY) / (r.height - 22) * top / 15) * 15
  }
  const key = (d: string, m: number) => (e: KeyboardEvent) => {
    const step = e.key === "ArrowUp" ? 15 : e.key === "ArrowDown" ? -15 : 0
    if (!step) return
    e.preventDefault()
    onCommit?.({ ...shown, [d]: Math.min(top, Math.max(0, m + step)) })
  }
  return (
    <div className="stack">
      <div className="row between">
        <span className="muted">{count === 1 ? "1 día" : `${count} días`}</span>
        <span className="mono week-total">{core.weekTime(JSON.stringify(shown))}</span>
      </div>
      <div className={`bars${editable ? " editable" : ""}`}>
        {DAYS.map(([d, letter, name]) => {
          const m = shown[d] ?? 0
          return (
            <div key={d} className="bar-col">
              <div className="bar-area" role={editable ? "slider" : "img"} tabIndex={editable ? 0 : undefined}
                aria-label={name} aria-valuemin={editable ? 0 : undefined} aria-valuemax={editable ? top : undefined}
                aria-valuenow={editable ? m : undefined} aria-valuetext={m === 0 ? "libre" : `${m} minutos`}
                onKeyDown={editable ? key(d, m) : undefined}
                onPointerDown={editable ? (e) => { e.currentTarget.setPointerCapture(e.pointerId); set(d, at(e)) } : undefined}
                onPointerMove={editable ? (e) => { if (e.currentTarget.hasPointerCapture(e.pointerId)) set(d, at(e)) } : undefined}
                onPointerUp={editable ? commit : undefined} onPointerCancel={editable ? commit : undefined}>
                <span className={`bar-value mono${m === 0 ? " muted" : ""}`}>{m === 0 ? "—" : m}</span>
                {m === 0 ? <span className="bar empty" /> : <span className="bar" style={{ height: `calc((100% - 22px) * ${m / top})` }} />}
              </div>
              <span className={`bar-day${m === 0 ? " muted" : ""}`} aria-hidden>{letter}</span>
            </div>
          )
        })}
      </div>
      {editable && <p className="muted small-text">Toca o arrastra cada barra hasta los minutos que tienes, hasta 5 h. Abajo del todo, ese día queda libre.</p>}
    </div>
  )
}

/** Opciones cortas en una sola fila (fuerza, nivel y «¿Cómo estás hoy?»). */
export function Segmented({ options, selected, onSelect, label }:
  { options: [string, string][]; selected: string; onSelect: (k: string) => void; label: string }) {
  return (
    <div className="segmented" role="radiogroup" aria-label={label}>
      {options.map(([k, l]) => (
        <button key={k} role="radio" aria-checked={k === selected} className={k === selected ? "on" : ""} onClick={() => onSelect(k)}>{l}</button>
      ))}
    </div>
  )
}

/** Opciones con explicación, una por tarjeta (bienvenida). */
export function ChoiceCards({ options, selected, onSelect, label }:
  { options: [string, string, string][]; selected: string | null; onSelect: (k: string) => void; label: string }) {
  return (
    <div className="stack" role="radiogroup" aria-label={label}>
      {options.map(([k, title, detail]) => (
        <button key={k} role="radio" aria-checked={k === selected} className={`choice${k === selected ? " on" : ""}`} onClick={() => onSelect(k)}>
          <span className="choice-title">{title}</span>
          <span className="muted">{detail}</span>
        </button>
      ))}
    </div>
  )
}
