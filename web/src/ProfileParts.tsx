// Piezas del perfil (lienzo de Pulso, opción B), las mismas que ProfileParts.kt y ProfileParts.swift de las apps
import type { CSSProperties } from "react"
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
    </div>
  )
}

/**
 * La semana, un día por fila: deslizar de 15 en 15 o escribir los minutos (de 0 a 5 h; 0 o vacío, ese día queda libre).
 * En filas y en horizontal, desplazar la página no mueve nada, y escribir la cifra evita afinar con el dedo.
 */
export function WeekBars({ core, days, onCommit }:
  { core: WebApp; days: Record<string, number>; onCommit: (d: Record<string, number>) => void }) {
  const top = core.maxDayMinutes
  const count = Object.values(days).filter((m) => m > 0).length
  const put = (d: string, m: number) => onCommit({ ...days, [d]: Math.min(top, Math.max(0, Math.round(m) || 0)) })
  return (
    <div className="stack">
      <div className="row between">
        <span className="muted">{count === 1 ? "1 día" : `${count} días`}</span>
        <span className="mono week-total">{core.weekTime(JSON.stringify(days))}</span>
      </div>
      <div className="week-rows">
        {DAYS.map(([d, , name]) => {
          const m = days[d] ?? 0
          return (
            <div key={d} className={`week-row${m === 0 ? " free" : ""}`}>
              <span className="week-day">{name}</span>
              <input type="range" min={0} max={top} step={15} value={m} aria-label={name}
                style={{ "--p": `${(m / top) * 100}%` } as CSSProperties}
                aria-valuetext={m === 0 ? "libre" : `${m} minutos`} onChange={(e) => put(d, Number(e.target.value))} />
              <label className="week-minutes">
                <input type="number" inputMode="numeric" min={0} max={top} value={m === 0 ? "" : m} placeholder="—"
                  aria-label={`Minutos el ${name.toLowerCase()}`} onChange={(e) => put(d, Number(e.target.value))} />
                <span className="muted" aria-hidden>min</span>
              </label>
            </div>
          )
        })}
      </div>
      <p className="muted small-text">Desliza o escribe los minutos de cada día, hasta 5 h. Sin minutos, ese día queda libre.</p>
    </div>
  )
}

/** Opciones cortas en una sola fila (fuerza, nivel y «¿Cómo estás hoy?»). */
export function Segmented({ options, selected, onSelect, label }:
  { options: [string, string][]; selected: string; onSelect: (k: string) => void; label: string }) {
  return (
    <div className="segmented" role="radiogroup" aria-label={label}>
      {options.map(([k, l]) => (
        <button key={k} type="button" role="radio" aria-checked={k === selected} className={k === selected ? "on" : ""} onClick={() => onSelect(k)}>{l}</button>
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
        <button key={k} type="button" role="radio" aria-checked={k === selected} className={`choice${k === selected ? " on" : ""}`} onClick={() => onSelect(k)}>
          <span className="choice-title">{title}</span>
          <span className="muted">{detail}</span>
        </button>
      ))}
    </div>
  )
}
