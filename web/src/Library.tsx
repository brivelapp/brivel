// Biblioteca de ejercicios (Library.kt): buscar y filtrar, la ficha de cada uno y cambiar un ejercicio de la sesión
import { useEffect, useRef, useState } from "react"
import type { WebApp } from "brivel-shared"
import type { ExerciseCard, ExerciseHistory } from "./types"
import { Figure } from "./Strength"
import { fmt1, Sparkline } from "./Progress"

/** La ficha: figura, patrón, material y claves de técnica, con su fuente. */
function Card({ core, card }: { core: WebApp; card: ExerciseCard }) {
  return (
    <>
      <div className="row">
        {core.figureJson(card.figure) && <span className="figure-box"><Figure core={core} pattern={card.figure} /></span>}
        <div className="grow">
          <h2>{card.name}</h2>
          <p className="muted small-text">{[card.patternLabel, card.equipment.join(", "), card.timed ? "por tiempo" : "por repeticiones"].filter(Boolean).join(" · ")}</p>
        </div>
      </div>
      {card.muscles.length > 0 && <p className="muted small-text">Zona: {card.zone} · Músculos: {card.muscles.join(", ")}</p>}
      {card.description && <p>{card.description}</p>}
      <ul>{card.cues.map((c) => <li key={c}>{c}</li>)}</ul>
      <p className="muted small source">{card.cueSource}</p>
    </>
  )
}

/** Lo que has hecho en ese ejercicio: la gráfica (1RM estimado o mejor serie), tus marcas y los últimos días. */
function History({ core, name }: { core: WebApp; name: string }) {
  const h: ExerciseHistory = JSON.parse(core.exerciseHistoryJson(name))
  if (h.days.length === 0) return null
  return (
    <>
      <h3>Tu historial</h3>
      {h.chart && (
        <>
          <div className="row between"><span>{h.chart.label}</span><span className="mono">{fmt1(h.chart.latest)}{h.chart.unit && ` ${h.chart.unit}`}</span></div>
          <Sparkline points={h.chart.points} low={null} high={null} />
          <p className="muted small">{h.chart.explanation}</p>
        </>
      )}
      {h.records.map((r) => (
        <div key={r.title} className="row between">
          <span>{r.title}{r.date && <span className="muted small"> · {core.dateLabel(r.date)}</span>}</span><span className="mono nowrap">{r.value}</span>
        </div>
      ))}
      <table className="sets">
        <thead><tr><th>Día</th><th>Mejor serie</th><th>Series</th><th>Kilos</th></tr></thead>
        <tbody>
          {h.days.map((d) => (
            <tr key={d.date}><td className="mono nowrap" title={core.dateLabel(d.date)}>{d.date.slice(8)}/{d.date.slice(5, 7)}/{d.date.slice(2, 4)}</td><td className="mono">{d.best}</td><td className="mono">{d.sets}</td>
              <td className="mono">{d.volumeKg > 0 ? Math.round(d.volumeKg) : "—"}</td></tr>
          ))}
        </tbody>
      </table>
    </>
  )
}

/** Elegir un ejercicio de la biblioteca, buscando por nombre: para añadirlo al entreno libre. */
export function ExercisePicker({ core, close, choose }: { core: WebApp; close: () => void; choose: (name: string) => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal() }, [])
  const [query, setQuery] = useState("")
  const [mine, setMine] = useState(false)
  const owned: string[] = JSON.parse(core.myEquipmentJson())
  const found: ExerciseCard[] = JSON.parse(core.libraryByZoneJson(query, null, null, mine))
  return (
    <dialog ref={dialog} className="sheet" onClose={close} aria-label="Añadir ejercicio">
      <h2>Añadir ejercicio</h2>
      <label className="field">Buscar
        <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Sentadilla, remo, espalda…" />
      </label>
      {owned.length > 0 && (
        <label className="check"><input type="checkbox" checked={mine} onChange={(e) => setMine(e.target.checked)} /> Solo con mi material</label>
      )}
      <p className="muted small-text">{found.length} ejercicios{found.length > PICK ? `: escribe para acotar (se ven ${PICK})` : ""}</p>
      {found.slice(0, PICK).map((c) => (
        <button key={c.name} className="row option" onClick={() => choose(c.name)}>
          {core.figureJson(c.figure) && <span className="figure-box"><Figure core={core} pattern={c.figure} size={36} /></span>}
          <span className="grow">{c.name}<span className="muted small"> · {c.patternLabel}</span></span>
        </button>
      ))}
      <button className="secondary" onClick={close}>Cancelar</button>
    </dialog>
  )
}

const PICK = 40

/**
 * La ficha en un diálogo. Con `swap`, también los ejercicios por los que se puede cambiar (mismo patrón) y, si ya es
 * un cambio, volver al del plan.
 */
export function ExerciseSheet({ core, name, close, swap }: { core: WebApp; name: string; close: () => void
  swap?: { original: string | null; choose: (replacement: string) => void } }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal() }, [])
  const json = core.exerciseCardJson(name)
  const card: ExerciseCard | null = json ? JSON.parse(json) : null
  const options: ExerciseCard[] = swap ? JSON.parse(core.swapOptionsJson(swap.original ?? name)) : []
  return (
    <dialog ref={dialog} className="sheet" onClose={close} aria-label={swap ? "Cambiar ejercicio" : "Ficha del ejercicio"}>
      {card && <Card core={core} card={card} />}
      {!swap && <History core={core} name={name} />}
      {swap && (
        <>
          <h3>Cambiar por</h3>
          {swap.original && <button className="secondary" onClick={() => swap.choose(swap.original!)}>Volver a {swap.original}</button>}
          {options.filter((o) => o.name !== name).map((o) => (
            <button key={o.name} className="row option" onClick={() => swap.choose(o.name)}>
              {core.figureJson(o.figure) && <span className="figure-box"><Figure core={core} pattern={o.figure} size={36} /></span>}
              <span className="grow">{o.name}<span className="muted small"> · {o.equipment.join(", ")}</span></span>
            </button>
          ))}
          <p className="muted small">El cambio vale desde esta sesión; las anteriores siguen con el que hiciste.</p>
        </>
      )}
      <button className="primary" onClick={close}>Cerrar</button>
    </dialog>
  )
}

const PAGE = 60

/** En Entrenar: todos los ejercicios, con búsqueda y filtros de zona y de material, y el material que tienes. */
export function LibraryCard({ core }: { core: WebApp }) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState("")
  const [zone, setZone] = useState("")
  const [equipment, setEquipment] = useState("")
  const [mine, setMine] = useState(false)
  const [owned, setOwned] = useState<string[]>(() => JSON.parse(core.myEquipmentJson()))
  const [limit, setLimit] = useState(PAGE)
  const [shown, setShown] = useState<string | null>(null)
  const tags: string[] = JSON.parse(core.equipmentJson())
  const list: ExerciseCard[] = open ? JSON.parse(core.libraryByZoneJson(query, zone || null, equipment || null, mine)) : []
  const filter = <T,>(set: (v: T) => void) => (v: T) => { set(v); setLimit(PAGE) }
  const toggle = (t: string) => {
    const next = owned.includes(t) ? owned.filter((o) => o !== t) : [...owned, t]
    core.setEquipmentJson(JSON.stringify(next))
    setOwned(JSON.parse(core.myEquipmentJson()))
  }
  return (
    <section className="module">
      <h3>Biblioteca de ejercicios</h3>
      <p className="muted">Busca entre todos los ejercicios por zona del cuerpo o material.</p>
      {!open
        ? <button className="secondary" onClick={() => setOpen(true)}>Ver los ejercicios</button>
        : (
          <>
            <p className="muted small-text">Tu material (sin el peso corporal, que siempre cuenta). Brivel solo te propone variantes que puedas hacer con él.</p>
            <div className="chips">
              {tags.filter((t) => t !== "peso corporal").map((t) => (
                <button key={t} className={owned.includes(t) ? "chip selected" : "chip"} aria-pressed={owned.includes(t)} onClick={() => toggle(t)}>{t}</button>
              ))}
            </div>
            <label className="field">Buscar
              <input type="search" value={query} onChange={(e) => filter(setQuery)(e.target.value)} placeholder="Sentadilla, remo, espalda…" />
            </label>
            <label className="field">Zona
                <select value={zone} onChange={(e) => filter(setZone)(e.target.value)}>
                  <option value="">Todas</option>
                  {(JSON.parse(core.zonesJson()) as string[]).map((z) => <option key={z} value={z}>{z}</option>)}
                </select>
            </label>
            <label className="field">Material
                <select value={equipment} onChange={(e) => filter(setEquipment)(e.target.value)}>
                  <option value="">Cualquiera</option>
                  {tags.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
            </label>
            {owned.length > 0 && (
              <label className="check"><input type="checkbox" checked={mine} onChange={(e) => filter(setMine)(e.target.checked)} /> Solo con mi material</label>
            )}
            <p className="muted small-text">{list.length} ejercicios</p>
            {list.slice(0, limit).map((c) => (
              <button key={c.name} className="row option" onClick={() => setShown(c.name)}>
                {core.figureJson(c.figure) && <span className="figure-box"><Figure core={core} pattern={c.figure} size={36} /></span>}
                <span className="grow">{c.name}<span className="muted small"> · {c.patternLabel}</span></span>
              </button>
            ))}
            {list.length > limit && <button className="secondary" onClick={() => setLimit(limit + PAGE)}>Ver más</button>}
          </>
        )}
      {shown && <ExerciseSheet core={core} name={shown} close={() => setShown(null)} />}
    </section>
  )
}
