// Raíz de la web: portada, bienvenida e inicio de sesión para quien no tiene datos aquí; después, Hoy y Perfil
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { WebApp } from "brivel-shared"
import { Footer, Today, type Act } from "./Today"
import { Landing, Pulse } from "./Landing"
import { Onboarding } from "./Onboarding"
import { Profile } from "./Profile"
import { Calendar } from "./Calendar"
import { Progress } from "./Progress"
import { Strength } from "./Strength"
import { SyncCard } from "./Sync"
import { loadKeys } from "./sync"
import { pullGarmin } from "./garmin"

type Entry = "landing" | "onboarding" | "login"
type Tab = "today" | "calendar" | "progress" | "profile"
const TABS: [Tab, string][] = [["today", "Hoy"], ["calendar", "Calendario"], ["progress", "Progreso"], ["profile", "Perfil"]]

export function App() {
  const core = useMemo(() => new WebApp(), [])
  const [, setVersion] = useState(0)
  const [message, setMessage] = useState<string | null>(null)
  const [entry, setEntry] = useState<Entry>("landing")
  // De vuelta de Garmin (#garmin) o de intervals.icu (/intervals?code=…), a Perfil
  const [tab, setTab] = useState<Tab>(() => (location.hash === "#garmin" || location.pathname === "/intervals" ? "profile" : "today"))
  const [strengthDate, setStrengthDate] = useState<string | null>(null)  // sesión de fuerza abierta, a pantalla completa
  const closeStrength = useCallback(() => setStrengthDate(null), [])
  // Al cerrar la sesión, el foco vuelve al botón que la abrió (para quien usa el teclado o un lector de pantalla)
  const lastStrength = useRef<string | null>(null)
  useEffect(() => {
    if (strengthDate) lastStrength.current = strengthDate
    else if (lastStrength.current) document.querySelector<HTMLElement>(`[data-strength="${lastStrength.current}"]`)?.focus()
  }, [strengthDate])
  const refresh = () => setVersion((v) => v + 1)

  // Con la sincronización activada, cada cambio se sube poco después (varios seguidos, de una vez)
  const syncTimer = useRef<number | undefined>(undefined)
  const syncSoon = () => {
    core.retireFromWatch().catch(() => {})  // sin red, se reintenta en el próximo cambio o al abrir
    if (!core.syncActive) return
    clearTimeout(syncTimer.current)
    syncTimer.current = window.setTimeout(() => core.syncNow().then(
      (conflict) => { if (conflict) setMessage(conflict); refresh() },
      (e) => setMessage(e instanceof Error && e.message ? e.message : "No se pudo sincronizar"),
    ), 1500)
  }
  useEffect(() => {
    loadKeys().then((keys) => {
      if (!keys) return
      core.useSync(keys)
      refresh()
      syncSoon()
    })
    // Lo que Garmin dejó en el buzón: al abrir y cada vez que se vuelve a la pestaña; sin conexión no hace nada
    const pull = () => {
      pullGarmin(core).then((n) => { if (n) { refresh(); syncSoon() } }, () => {})
      core.retireFromWatch().catch(() => {})
      if (core.intervalsAthlete) core.syncIntervals().then(refresh, () => {})
    }
    if (location.pathname === "/intervals") {
      const back = location.href
      history.replaceState(null, "", "/")  // el código no se queda en el historial
      core.finishIntervals(back).then(() => { setMessage("intervals.icu conectado"); refresh(); syncSoon() },
        (e) => setMessage(e instanceof Error && e.message ? e.message : "No se pudo conectar con intervals.icu"))
    } else if (location.hash !== "#garmin") pull()
    const onVisible = () => { if (document.visibilityState === "visible") pull() }
    document.addEventListener("visibilitychange", onVisible)
    return () => document.removeEventListener("visibilitychange", onVisible)
  }, [core])
  const act: Act = (f, success) => {
    try {
      f()
      setMessage(success ?? null)
      syncSoon()
    } catch (e) {
      setMessage(e instanceof Error && e.message ? e.message : "No se pudo completar")
    }
    refresh()
  }
  const synced = (m: string | null) => {
    setMessage(m)
    refresh()
  }
  const status = <p className="status" role="status">{message}</p>

  if (core.needsOnboarding) {
    // Con perfil (cambiar de objetivo) va directo a la pregunta del objetivo
    if (core.hasProfile || entry === "onboarding") {
      return (
        <>
          <Onboarding core={core} onExit={() => setEntry("landing")}
            onDone={(error) => { if (error) setMessage(error); else { setTab("today"); syncSoon() } refresh() }} />
          {status}
        </>
      )
    }
    if (entry === "login") {
      return (
        <main className="question">
          <button className="back" aria-label="Atrás" onClick={() => setEntry("landing")}>‹</button>
          <h1 className="question-title">Iniciar sesión</h1>
          <p className="muted">Brivel no tiene cuentas con contraseña: en el móvil, abre Perfil → «Usar en otros dispositivos» → «Ver código» y escríbelo aquí.</p>
          <SyncCard core={core} onDone={synced} joinOnly />
          {status}
        </main>
      )
    }
    return (
      <>
        <Landing onStart={() => setEntry("onboarding")} onLogin={() => setEntry("login")} />
        <Footer core={core} act={act} />
      </>
    )
  }

  if (strengthDate) {
    return (
      <>
        <Strength core={core} date={strengthDate} act={act} close={closeStrength} />
        {status}
      </>
    )
  }

  return (
    <>
      <nav className="topbar" aria-label="Secciones">
        <span className="brand"><span className="brand-mark"><Pulse size={20} /></span>brivel</span>
        <div className="tabs">
          {TABS.map(([t, label]) => (
            <button key={t} aria-current={tab === t ? "page" : undefined} onClick={() => setTab(t)}>{label}</button>
          ))}
        </div>
      </nav>
      {tab === "today" && <Today core={core} act={act} synced={synced} openStrength={setStrengthDate} />}
      {tab === "calendar" && <Calendar core={core} act={act} openStrength={setStrengthDate} />}
      {tab === "progress" && <Progress core={core} openProfile={() => setTab("profile")} />}
      {tab === "profile" && <Profile core={core} act={act} synced={synced} />}
      {status}
      <Footer core={core} act={act} />
    </>
  )
}
