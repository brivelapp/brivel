// Raíz de la web: portada, bienvenida e inicio de sesión para quien no tiene datos aquí; después, Hoy y Perfil
import { useEffect, useMemo, useRef, useState } from "react"
import { WebApp } from "brivel-shared"
import { Footer, Today, type Act } from "./Today"
import { Landing, Pulse } from "./Landing"
import { Onboarding } from "./Onboarding"
import { Profile } from "./Profile"
import { SyncCard } from "./Sync"
import { loadKeys } from "./sync"

type Entry = "landing" | "onboarding" | "login"
type Tab = "today" | "profile"

export function App() {
  const core = useMemo(() => new WebApp(), [])
  const [, setVersion] = useState(0)
  const [message, setMessage] = useState<string | null>(null)
  const [entry, setEntry] = useState<Entry>("landing")
  const [tab, setTab] = useState<Tab>("today")
  const refresh = () => setVersion((v) => v + 1)

  // Con la sincronización activada, cada cambio se sube poco después (varios seguidos, de una vez)
  const syncTimer = useRef<number | undefined>(undefined)
  const syncSoon = () => {
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

  return (
    <>
      <nav className="topbar" aria-label="Secciones">
        <span className="brand"><span className="brand-mark"><Pulse size={20} /></span>brivel</span>
        <div className="tabs">
          <button aria-current={tab === "today" ? "page" : undefined} onClick={() => setTab("today")}>Hoy</button>
          <button aria-current={tab === "profile" ? "page" : undefined} onClick={() => setTab("profile")}>Perfil</button>
        </div>
      </nav>
      {tab === "today" ? <Today core={core} act={act} synced={synced} /> : <Profile core={core} act={act} synced={synced} />}
      {status}
      <Footer core={core} act={act} />
    </>
  )
}
