// Actividades propias (importar un archivo FIT, GPX o TCX y ver su detalle), material con sus kilómetros y medallas: lo que calcula el
// núcleo (Activities.kt, Gear.kt, Badges.kt) y aquí solo se pinta
import { useEffect, useRef, useState } from "react"
import type { WebApp } from "brivel-shared"
import { Icon, SPORT_ICON, type Act } from "./Today"
import { Choice } from "./ProfileParts"
import type { ActivityRow, ActivityScreen, BadgeView, BadgesScreen, Curve, GearRow, GearScreen, XY } from "./types"

/** Una línea del núcleo (puntos dentro de un cuadrado de lado 1) como SVG; `height`: alto del dibujo sobre 100 de ancho. */
function Line({ points, height, label }: { points: XY[]; height: number; label: string }) {
  return (
    <svg viewBox={`0 0 100 ${height}`} preserveAspectRatio="none" role="img" aria-label={label}>
      <polyline points={points.map((p) => `${(p.x * 100).toFixed(2)},${(p.y * height).toFixed(2)}`).join(" ")}
        fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

/** El detalle de una actividad propia, en un diálogo. */
export function ActivityDialog({ core, act, id, onClose }: { core: WebApp; act: Act; id: string; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  // Al abrirse, el navegador enfoca el primer botón, que está al final, y baja hasta él: se vuelve arriba
  useEffect(() => { dialog.current?.showModal(); dialog.current?.scrollTo(0, 0) }, [])
  const json = core.activityJson(id)
  if (!json) return null
  const a: ActivityScreen = JSON.parse(json)
  const total = a.zoneMinutes.reduce((x, y) => x + y, 0)
  return (
    <dialog ref={dialog} className="sheet activity" onClose={onClose} aria-label={a.title}>
      <div><h2>{a.title}</h2><p className="muted small-text">{a.dateLabel} · {a.source}</p></div>
      <div className="stats">
        {a.stats.map((s) => <div key={s.label}><span className="muted small">{s.label}</span><br /><span className="mono">{s.value}</span></div>)}
      </div>
      {a.track.length > 0 && (
        <div className="track"><Line points={a.track} height={100} label="Recorrido" />
          <p className="muted small">El recorrido, sin mapa de fondo. Solo está en este dispositivo.</p></div>
      )}
      {a.charts.map((c) => (
        <div key={c.label} className="chart">
          <div className="row between"><span>{c.label}</span><span className="muted small mono">{c.low} – {c.high}</span></div>
          <Line points={c.line} height={24} label={`${c.label}: de ${c.low} a ${c.high}`} />
        </div>
      ))}
      {a.charts.length === 0 && <p className="muted small-text">Las gráficas y el recorrido se quedan en el dispositivo que importó o grabó la actividad; aquí está su resumen.</p>}
      {total > 0 && (
        <div>
          <span className="zones-bar">{a.zoneMinutes.map((m, i) => m > 0 && <span key={i} style={{ flex: m, background: `var(--z${Math.min(i + 1, 6)})` }} />)}</span>
          <span className="muted small">{a.zoneMinutes.map((m, i) => (m > 0 ? `Z${i + 1} ${m}′` : null)).filter(Boolean).join(" · ")}</span>
        </div>
      )}
      {a.laps.length > 0 && (
        <table className="laps">
          <thead><tr><th>Vuelta</th><th>Tiempo</th><th>Distancia</th><th>{a.sport === "cycling" ? "Velocidad" : "Ritmo"}</th><th>Pulso</th></tr></thead>
          <tbody>{a.laps.map((l) => <tr key={l.n}><td>{l.n}</td><td>{l.time}</td><td>{l.distance}</td><td>{l.pace}</td><td>{l.hr}</td></tr>)}</tbody>
        </table>
      )}
      {a.bests.length > 0 && (
        <div>
          <p className="muted small-text">Tu mejor media en cada duración</p>
          {a.bests.map((b) => <div key={b.label} className="row between"><span>{b.label}</span><span className="mono">{b.value}</span></div>)}
        </div>
      )}
      {a.gear && <p className="muted small-text">Material: {a.gear}</p>}
      <button className="primary" onClick={() => dialog.current?.close()}>Cerrar</button>
      <button className="link" onClick={() => confirm("¿Borrar esta actividad? Dejará de contar en tu plan y en Progreso.")
        && act(() => { core.deleteActivity(id); dialog.current?.close() }, "Actividad borrada")}>Borrar la actividad</button>
    </dialog>
  )
}

/** Entrenar: las actividades propias y el botón de importar un archivo de actividad. */
export function ActivitiesCard({ core, act }: { core: WebApp; act: Act }) {
  const rows: ActivityRow[] = JSON.parse(core.activitiesJson())
  const [open, setOpen] = useState<string | null>(null)
  const [all, setAll] = useState(false)
  const file = useRef<HTMLInputElement>(null)
  const load = async (f: File | undefined) => {
    if (!f) return
    const bytes = new Int8Array(await f.arrayBuffer())
    act(() => setOpen(core.importFit(bytes)), "Actividad importada")
    if (file.current) file.current.value = ""
  }
  return (
    <section className="module">
      <h3>Tus actividades</h3>
      <p className="muted small-text">Importa el archivo FIT de tu reloj o tu ciclocomputador, o un GPX o TCX de otra app, o graba con el GPS desde la app del móvil. Cuentan
        en tu plan y en Progreso como las del reloj.</p>
      {(all ? rows : rows.slice(0, 5)).map((r) => (
        <button key={r.id} className="metric" onClick={() => setOpen(r.id)}>
          <span className="row">
            <span className="badge"><Icon src={SPORT_ICON[r.sport] ?? SPORT_ICON.strength} size={18} /></span>
            <span className="grow"><span className="metric-label">{r.title}</span><br /><span className="muted small">{core.dateLabel(r.date)}{r.detail && ` · ${r.detail}`}</span></span>
          </span>
        </button>
      ))}
      {!all && rows.length > 5 && <button className="link" onClick={() => setAll(true)}>Ver las {rows.length}</button>}
      <input ref={file} type="file" accept=".fit,.gpx,.tcx" hidden aria-label="Archivo de actividad" onChange={(e) => load(e.target.files?.[0])} />
      <button className="secondary" onClick={() => file.current?.click()}>Importar un archivo FIT, GPX o TCX</button>
      {open && <ActivityDialog core={core} act={act} id={open} onClose={() => setOpen(null)} />}
    </section>
  )
}

const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }

/** Perfil: zapatillas y bicis con sus kilómetros. */
export function GearCard({ core, act }: { core: WebApp; act: Act }) {
  const screen: GearScreen = JSON.parse(core.gearJson())
  const sports: [string, string][] = JSON.parse(core.gearSportsJson())
  const empty = { id: null as string | null, name: "", sport: sports[0][0], since: today(), startKm: "", limitKm: "" }
  const [form, setForm] = useState<typeof empty | null>(null)
  const edit = (g: GearRow) => setForm({ id: g.id, name: g.name, sport: g.sport, since: g.since,
    startKm: g.startKm ? String(g.startKm).replace(".", ",") : "", limitKm: g.limitKm ? String(g.limitKm).replace(".", ",") : "" })
  const km = (v: string) => v.replace(/[^\d.,]/g, "")
  return (
    <section className="module">
      <h3>Zapatillas y bicis</h3>
      <p className="muted small-text">Brivel apunta los kilómetros de cada actividad al material que tengas en uso en ese deporte.</p>
      {screen.garminNote && <p className="muted small source">{screen.garminNote}</p>}
      {screen.rows.map((g) => (
        <div key={g.id} className="gear">
          <div className="row between"><span><strong>{g.name}</strong><span className="muted small"> · {g.label}{g.retired ? " · retirado" : g.inUse ? " · en uso" : ""}</span></span>
            <span className="mono nowrap">{g.km}</span></div>
          <span className="muted small">{g.detail}</span>
          {g.warning && <p className="caution-text">{g.warning}</p>}
          {g.uses.length > 0 && screen.rows.some((o) => o.id !== g.id && o.sport === g.sport) && (
            <details><summary className="muted small">Últimas actividades</summary>
              {g.uses.map((u) => (
                <label key={u.activity} className="row between small-text">{u.text}
                  <select aria-label={`Material de la actividad del ${u.text}`} value={g.id} onChange={(e) => act(() => core.moveGearUse(u.activity, e.target.value), "Actividad cambiada de material")}>
                    {screen.rows.filter((o) => o.sport === g.sport).map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
                  </select>
                </label>
              ))}
            </details>
          )}
          <div className="row wrap">
            {!g.inUse && <button className="link" onClick={() => act(() => core.useGear(g.id), `${g.name} está en uso`)}>Poner en uso</button>}
            {!g.retired && <button className="link" onClick={() => act(() => core.retireGear(g.id), `${g.name} está retirado: conserva sus kilómetros`)}>Retirar</button>}
            <button className="link" onClick={() => edit(g)}>Cambiar</button>
            <button className="link" onClick={() => confirm(`¿Borrar «${g.name}» y sus kilómetros apuntados?`) && act(() => core.deleteGear(g.id), "Material borrado")}>Borrar</button>
          </div>
        </div>
      ))}
      {form ? (
        <>
          <label className="field">Nombre<input className="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
          {form.id == null && (
            <Choice label="Qué es" options={sports} value={form.sport} onChange={(sport) => setForm({ ...form, sport })} />
          )}
          <label className="field">Cuenta las actividades desde<input type="date" value={form.since} max={today()} onChange={(e) => setForm({ ...form, since: e.target.value })} /></label>
          <label className="field">Kilómetros que ya llevaba<input inputMode="decimal" value={form.startKm} onChange={(e) => setForm({ ...form, startKm: km(e.target.value) })} /></label>
          <label className="field">Avisar al pasar de (km, opcional)<input inputMode="decimal" value={form.limitKm} onChange={(e) => setForm({ ...form, limitKm: km(e.target.value) })} /></label>
          <div className="row">
            <button className="primary" onClick={() => act(() => { core.saveGear(form.id, form.name, form.sport, form.since, form.startKm, form.limitKm); setForm(null) }, "Material guardado")}>Guardar</button>
            <button className="link" onClick={() => setForm(null)}>Cancelar</button>
          </div>
        </>
      ) : <button className="secondary" onClick={() => setForm(empty)}>Añadir zapatillas o bici</button>}
    </section>
  )
}

function Badge({ b }: { b: BadgeView }) {
  return (
    <div className="row badge-row">
      <span className={b.earned ? "medal on" : "medal"} aria-hidden="true">{b.points}</span>
      <span className="grow"><strong>{b.title}</strong><span className="muted small"> · {b.group}</span><br />
        <span className="muted small">{b.earned ? `${b.text} Ganada el ${b.earned}.` : b.text}</span>
        {!b.earned && b.progress && (
          <span className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(b.fraction * 100)} aria-label={b.progress}>
            <span style={{ width: `${Math.round(b.fraction * 100)}%` }} /></span>
        )}
        {!b.earned && b.progress && <span className="muted small mono">{b.progress}</span>}
      </span>
    </div>
  )
}

/** Progreso: nivel, puntos, medallas ganadas y las siguientes más cercanas. */
export function BadgesCard({ badges }: { badges: BadgesScreen }) {
  const [all, setAll] = useState(false)
  return (
    <section className="module">
      <div className="row between"><h3>Medallas</h3><span className="mono">Nivel {badges.level} · {badges.points} {badges.points === 1 ? "punto" : "puntos"}</span></div>
      {badges.garminNote && <p className="muted small source">{badges.garminNote}</p>}
      {badges.nextLevel && <p className="muted small-text">{badges.nextLevel}. Cada medalla vale los puntos de su círculo.</p>}
      {badges.earned.slice(0, all ? undefined : 3).map((b) => <Badge key={b.id} b={b} />)}
      {badges.earned.length === 0 && <p className="muted small-text">Aún no tienes ninguna. Estas son las que tienes más cerca:</p>}
      {badges.earned.length > 0 && <p className="muted small-text">Las siguientes</p>}
      {badges.next.slice(0, all ? undefined : 3).map((b) => <Badge key={b.id} b={b} />)}
      {!all && badges.earned.length + badges.next.length > 6 && <button className="link" onClick={() => setAll(true)}>Ver todas</button>}
    </section>
  )
}

/** Progreso: curvas de potencia y de ritmo de las actividades propias, como tabla. */
export function Curves({ curves }: { curves: Curve[] }) {
  return (
    <>
      {curves.map((c) => (
        <section key={c.title} className="module">
          <h3>{c.title}</h3>
          <p className="muted small-text">Tu mejor media en cada duración, con las actividades que has importado o grabado.</p>
          <table className="laps">
            <thead><tr><th>Duración</th><th>Últimos 90 días</th><th>Tu mejor</th></tr></thead>
            <tbody>{c.rows.map((r) => <tr key={r.duration}><td>{r.duration}</td><td>{r.recent}</td><td>{r.best}</td></tr>)}</tbody>
          </table>
          {c.note && <p className="muted small-text">{c.note}</p>}
        </section>
      ))}
    </>
  )
}
