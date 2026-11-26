// Hoy a gusto de cada uno (decidido el 2026-11-23), como TodayWidgets.kt y TodayWidgets.swift: la sesión va fija arriba
// (Today.tsx) y, debajo, widgets medios o anchos. Mantener pulsado, el clic derecho o «⋯» abren su menú: Medio, Ancho,
// Ordenar los widgets y Quitar de Hoy (opción B). «Añadir» enseña los dos tamaños antes de ponerlo (opción C).
import { useEffect, useRef, useState, type ReactNode } from "react"
import type { WebApp } from "brivel-shared"
import type { TodayScreen, WidgetChoice, WidgetView } from "./types"
import { Icon, type Act } from "./Today"
import check from "../../iosApp/iosApp/Assets.xcassets/check.imageset/check.svg"

const LONG_PRESS_MS = 500

/** La semana: puntos, minutos de cardio y fuerza hecha. Ancha lleva además los avisos. */
function Week({ screen, wide }: { screen: TodayScreen; wide: boolean }) {
  return (
    <>
      <ol className="week">
        {screen.weekDots.map((d) => (
          <li key={d.date} aria-label={`${d.letter}: ${d.done ? "hecho" : d.planned ? "planificado" : "libre"}`}>
            <span className={`dot${d.done ? " done" : ""}${d.isToday ? " today" : ""}`}>{d.planned && !d.done && <i />}</span>
            <span className="muted small" aria-hidden>{d.letter}</span>
          </li>
        ))}
      </ol>
      {/* Sin plan, la fuerza hecha frente a los 2 días; con plan, los minutos de cardio y la fuerza hecha */}
      {screen.noPlan
        ? <p className="muted">{screen.strengthDone} de 2 días de fuerza</p>
        : <p className="muted">Cardio {screen.weekDone} de {screen.weekTarget} min</p>}
      {screen.noPlan
        ? (screen.strengthDone >= 2 ? <p className="ok-text"><Icon src={check} size={18} />Cumples los 2 días de fuerza</p> : null)
        : <p className="muted">{screen.strengthLine}</p>}
      {wide && screen.warnings.map((w) => <p key={w} className="muted small-text">{w}</p>)}
      {wide && screen.info.map((t) => <p key={t} className="muted small-text">{t}</p>)}
      {!wide && screen.warnings.length > 0 && <p className="caution-text small-text">{screen.warnings.length === 1 ? "1 aviso" : `${screen.warnings.length} avisos`}: ábrelo en ancho</p>}
    </>
  )
}

/** Un dato con su valor grande, o cómo tenerlo si aún no hay. */
function Value({ w }: { w: WidgetView }) {
  if (w.value == null) return <p className="muted small-text">{w.empty}</p>
  return (
    <>
      <p><span className="mono widget-value">{w.value}</span>{w.unit && <span className="mono muted">&nbsp;{w.unit}</span>}</p>
      {w.detail && <p className="muted small-text">{w.detail}</p>}
    </>
  )
}

/** Menú de un widget: los dos tamaños dibujados, ordenar y quitar. */
function Menu({ w, act, core, close, reorder }: { w: WidgetView; act: Act; core: WebApp; close: () => void; reorder: () => void }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    ref.current?.querySelector("button")?.focus()
    const away = (e: Event) => { if (!ref.current?.contains(e.target as Node)) close() }
    const esc = (e: KeyboardEvent) => { if (e.key === "Escape") close() }
    document.addEventListener("pointerdown", away)
    document.addEventListener("keydown", esc)
    return () => { document.removeEventListener("pointerdown", away); document.removeEventListener("keydown", esc) }
  }, [close])
  const size = (wide: boolean) => { act(() => core.setWidgetWide(w.key, wide)); close() }
  return (
    <div className="widget-menu" role="menu" aria-label={`Opciones de ${w.title}`} ref={ref}>
      <button role="menuitemradio" aria-checked={!w.wide} onClick={() => size(false)}><span className="shape half" />Medio{!w.wide && <span className="menu-tick">✓</span>}</button>
      <button role="menuitemradio" aria-checked={w.wide} onClick={() => size(true)}><span className="shape full" />Ancho{w.wide && <span className="menu-tick">✓</span>}</button>
      <button role="menuitem" onClick={() => { reorder(); close() }}>Ordenar los widgets</button>
      <button role="menuitem" className="danger" onClick={() => { act(() => core.removeWidget(w.key), `${w.title}: quitado de Hoy`); close() }}>Quitar de Hoy</button>
    </div>
  )
}

function Widget({ w, index, count, core, act, screen, reordering, setReordering, readiness }:
  { w: WidgetView; index: number; count: number; core: WebApp; act: Act; screen: TodayScreen; reordering: boolean;
    setReordering: (v: boolean) => void; readiness: ReactNode }) {
  const [menu, setMenu] = useState(false)
  const timer = useRef<number | undefined>(undefined)
  const press = () => { timer.current = window.setTimeout(() => setMenu(true), LONG_PRESS_MS) }
  const release = () => window.clearTimeout(timer.current)
  const body = w.key === "readiness" ? readiness
    : w.key === "week" ? <Week screen={screen} wide={w.wide} />
    : <Value w={w} />
  return (
    <section className={`module widget${w.wide ? " wide" : ""}${reordering ? " reordering" : ""}`} aria-label={w.title}
      onContextMenu={(e) => { e.preventDefault(); setMenu(true) }}
      onPointerDown={(e) => { if (e.pointerType !== "mouse") press() }} onPointerUp={release} onPointerLeave={release} onPointerCancel={release}>
      <div className="row">
        <h3 className="grow">{w.title}</h3>
        {reordering ? (
          <span className="row">
            <button className="icon-button" aria-label={`Subir ${w.title}`} disabled={index === 0} onClick={() => act(() => core.moveWidget(w.key, index - 1))}>↑</button>
            <button className="icon-button" aria-label={`Bajar ${w.title}`} disabled={index === count - 1} onClick={() => act(() => core.moveWidget(w.key, index + 1))}>↓</button>
          </span>
        ) : (
          <button className="icon-button" aria-label={`Opciones de ${w.title}`} aria-haspopup="menu" aria-expanded={menu} onClick={() => setMenu(!menu)}>⋯</button>
        )}
      </div>
      {body}
      {menu && <Menu w={w} act={act} core={core} close={() => setMenu(false)} reorder={() => setReordering(true)} />}
    </section>
  )
}

/** Añadir a Hoy: la lista de los que faltan y, al tocar uno, sus dos tamaños para elegir. */
function AddWidget({ core, act, close }: { core: WebApp; act: Act; close: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const [picked, setPicked] = useState<WidgetChoice | null>(null)
  useEffect(() => { ref.current?.showModal() }, [])
  const choices: WidgetChoice[] = JSON.parse(core.widgetChoicesJson()).filter((c: WidgetChoice) => !c.inUse)
  const add = (wide: boolean) => { act(() => core.addWidget(picked!.key, wide), `${picked!.title}: añadido a Hoy`); close() }
  return (
    <dialog className="sheet" ref={ref} onClose={close} aria-label="Añadir a Hoy">
      <div className="row"><h2 className="grow">{picked ? picked.title : "Añadir a Hoy"}</h2><button className="text" onClick={close}>Cerrar</button></div>
      {picked ? (
        <div className="stack">
          <p className="muted">{picked.description}. Elige el tamaño:</p>
          <div className="size-previews">
            <button className="size-preview half" onClick={() => add(false)}><strong>{picked.title}</strong><span className="muted small-text">Medio · dos por fila</span></button>
            <button className="size-preview full" onClick={() => add(true)}><strong>{picked.title}</strong><span className="muted small-text">Ancho · toda la fila</span></button>
          </div>
          <button className="text" onClick={() => setPicked(null)}>Elegir otro</button>
        </div>
      ) : choices.length === 0 ? (
        <p className="muted">Ya tienes todos en Hoy.</p>
      ) : (
        <div className="stack tight">
          {choices.map((c) => (
            <button key={c.key} className="option" onClick={() => setPicked(c)}>
              <span className="grow"><strong>{c.title}</strong><br /><span className="muted small-text">{c.description}</span></span>
            </button>
          ))}
        </div>
      )}
    </dialog>
  )
}

/** Los widgets de Hoy, debajo de la sesión. */
export function TodayWidgets({ core, act, screen, readiness }: { core: WebApp; act: Act; screen: TodayScreen; readiness: ReactNode }) {
  const widgets: WidgetView[] = JSON.parse(core.todayWidgetsJson())
  const [reordering, setReordering] = useState(false)
  const [adding, setAdding] = useState(false)
  return (
    <>
      <div className="row">
        <span className="grow" />
        {reordering
          ? <button className="text" onClick={() => setReordering(false)}>Listo</button>
          : <button className="text" onClick={() => setAdding(true)}>Añadir a Hoy</button>}
      </div>
      <div className="widgets">
        {widgets.map((w, i) => (
          <Widget key={w.key} w={w} index={i} count={widgets.length} core={core} act={act} screen={screen}
            reordering={reordering} setReordering={setReordering} readiness={readiness} />
        ))}
      </div>
      {widgets.length === 0 && <p className="muted small-text">Sin widgets. Con «Añadir a Hoy» pones los que quieras.</p>}
      {adding && <AddWidget core={core} act={act} close={() => setAdding(false)} />}
    </>
  )
}
