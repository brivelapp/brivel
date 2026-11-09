// Biblioteca de ejercicios (Library.kt): buscar y filtrar, la ficha de cada uno y cambiar un ejercicio de la sesión
import { useEffect, useRef, useState } from "react"
import type { WebApp } from "brivel-shared"
import type { ExerciseCard } from "./types"
import { Figure } from "./Strength"

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
      <ul>{card.cues.map((c) => <li key={c}>{c}</li>)}</ul>
      <p className="muted small source">{card.cueSource}</p>
    </>
  )
}

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

/** En Perfil: todos los ejercicios, con búsqueda y filtros de patrón y de material. */
export function LibraryCard({ core }: { core: WebApp }) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState("")
  const [pattern, setPattern] = useState("")
  const [equipment, setEquipment] = useState("")
  const [shown, setShown] = useState<string | null>(null)
  const patterns: [string, string][] = Object.entries(JSON.parse(core.patternLabelsJson()))
  const list: ExerciseCard[] = open ? JSON.parse(core.libraryJson(query, pattern || null, equipment || null)) : []
  return (
    <section className="module">
      <h3>Biblioteca de ejercicios</h3>
      <p className="muted">Busca entre todos los ejercicios por tipo o material.</p>
      {!open
        ? <button className="secondary" onClick={() => setOpen(true)}>Ver los ejercicios</button>
        : (
          <>
            <label className="field">Buscar
              <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Sentadilla, remo…" />
            </label>
            <label className="field">Patrón
                <select value={pattern} onChange={(e) => setPattern(e.target.value)}>
                  <option value="">Todos</option>
                  {patterns.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                </select>
            </label>
            <label className="field">Material
                <select value={equipment} onChange={(e) => setEquipment(e.target.value)}>
                  <option value="">Cualquiera</option>
                  {(JSON.parse(core.equipmentJson()) as string[]).map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
            </label>
            {list.length === 0 && <p className="muted">Ningún ejercicio con esos filtros.</p>}
            {list.map((c) => (
              <button key={c.name} className="row option" onClick={() => setShown(c.name)}>
                {core.figureJson(c.figure) && <span className="figure-box"><Figure core={core} pattern={c.figure} size={36} /></span>}
                <span className="grow">{c.name}<span className="muted small"> · {c.patternLabel}</span></span>
              </button>
            ))}
          </>
        )}
      {shown && <ExerciseSheet core={core} name={shown} close={() => setShown(null)} />}
    </section>
  )
}
