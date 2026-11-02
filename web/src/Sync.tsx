import { useState } from "react"
import type { WebApp } from "brivel-shared"
import { clearKeys, saveKeys } from "./sync"

type View = "idle" | "create" | "join" | "code"

const errorText = (e: unknown) => (e instanceof Error && e.message ? e.message : "No se pudo sincronizar")

/**
 * Copia cifrada y otros dispositivos (docs/SINCRONIZACION.md). Opcional: sin activarla, todo sigue en este navegador.
 * Activar pide el consentimiento explícito para datos de salud (RGPD art. 9.2.a) y la edad mínima, sin marcar.
 */
export function SyncCard({ core, onDone }: { core: WebApp; onDone: (message: string | null) => void }) {
  const [view, setView] = useState<View>("idle")
  const [consent, setConsent] = useState(false)
  const [adult, setAdult] = useState(false)
  const [typed, setTyped] = useState("")
  const [code, setCode] = useState("")
  const [busy, setBusy] = useState(false)

  const run = async (f: () => Promise<string | null>) => {
    setBusy(true)
    try {
      onDone(await f())
    } catch (e) {
      onDone(errorText(e))
    }
    setBusy(false)
  }

  const create = () => run(async () => {
    const start = await core.createSync()
    setCode(start.code)  // antes de guardar: si IndexedDB falla, el código sigue a la vista para unirse después
    setView("code")
    await saveKeys(start.keys)
    return null
  })

  const join = () => run(async () => {
    await saveKeys(await core.joinSync(typed))
    setView("idle")
    return "Sincronizado: se han juntado los datos de este navegador con los de la copia"
  })

  const forget = () => run(async () => {
    core.forgetSync()
    await clearKeys()
    return "Este navegador ya no sincroniza. La copia sigue en el servidor"
  })

  const erase = () => confirm("¿Borrar la copia del servidor? Los demás dispositivos dejarán de sincronizar.") && run(async () => {
    await core.deleteSync()
    await clearKeys()
    return "Copia borrada del servidor. Tus datos siguen en este navegador"
  })

  if (view === "code") {
    return (
      <section className="module">
        <h3>Apunta tu código</h3>
        <p className="code mono">{code.match(/.{1,3}/g)?.join(" ")}</p>
        <p className="muted">Solo se enseña ahora. Lo necesitas para añadir otro dispositivo, y nadie puede recuperarlo.</p>
        <button className="primary" onClick={() => setView("idle")}>Ya lo he apuntado</button>
      </section>
    )
  }

  if (core.syncActive) {
    return (
      <section className="module">
        <h3>Sincronización activada</h3>
        <p className="muted">Una copia cifrada en el servidor que solo tus dispositivos pueden leer.</p>
        <button className="primary" disabled={busy}
          onClick={() => run(async () => (await core.syncNow()) ?? "Sincronizado")}>Sincronizar ahora</button>
        <div className="row wrap">
          <button className="link" disabled={busy} onClick={forget}>Dejar de sincronizar aquí</button>
          <button className="link" disabled={busy} onClick={erase}>Borrar la copia del servidor</button>
        </div>
      </section>
    )
  }

  if (view === "idle") {
    return (
      <section className="module">
        <h3>Usar en otros dispositivos</h3>
        <p className="muted">Opcional. Sin activarlo, tus datos siguen solo en este navegador.</p>
        <div className="row wrap">
          <button className="primary" onClick={() => setView("create")}>Activar</button>
          <button className="secondary" onClick={() => setView("join")}>Tengo un código</button>
        </div>
      </section>
    )
  }

  const joining = view === "join"
  return (
    <section className="module">
      <h3>{joining ? "Unirse con un código" : "Activar la sincronización"}</h3>
      {joining && (
        <>
          <label className="field">Código del otro dispositivo
            <input className="mono" autoComplete="off" autoCapitalize="characters" spellCheck={false} value={typed}
              onChange={(e) => setTyped(e.target.value)} />
          </label>
          <p className="muted small">Se juntan los datos de este navegador con los de la copia. Si solo probaste los datos de
            ejemplo, bórralos antes con «Borrar mis datos».</p>
        </>
      )}
      <label className="check">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>Acepto que Brivel guarde en su servidor una copia cifrada de mis datos, también los de salud, para usarlos
          en mis dispositivos. Puedo retirarlo cuando quiera borrando la copia.
          {" "}<a href="privacidad.html">Política de privacidad</a></span>
      </label>
      <label className="check">
        <input type="checkbox" checked={adult} onChange={(e) => setAdult(e.target.checked)} />
        <span>Tengo 14 años o más</span>
      </label>
      {!joining && (
        <p className="muted small">Nadie puede recuperar el código. Si lo pierdes y no te queda ningún dispositivo con
          Brivel, la copia se pierde; los datos de cada dispositivo siguen en él.</p>
      )}
      <div className="row wrap">
        <button className="primary" disabled={busy || !consent || !adult || (joining && !typed.trim())}
          onClick={joining ? join : create}>{joining ? "Unirse" : "Activar"}</button>
        <button className="secondary" disabled={busy} onClick={() => setView("idle")}>Cancelar</button>
      </div>
    </section>
  )
}
