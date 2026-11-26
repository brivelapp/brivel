import { useState } from "react"
import type { WebApp } from "brivel-shared"
import { clearKeys, loadKeys, saveKeys } from "./sync"
// El logo «G» de Google con sus cuatro colores, tal cual lo pide su guía de marca (Sign in with Google branding)
import googleLogo from "../../iosApp/iosApp/Assets.xcassets/google_g.imageset/google_g.svg"

type View = "idle" | "create" | "join" | "code" | "google" | "apple"
type Provider = "google" | "apple"

const NAMES: Record<Provider, string> = { google: "Google", apple: "Apple" }
/** Con qué cuenta se entra en este navegador (App.tsx lo apunta al volver del proveedor). */
export const ACCOUNT_KEY = "brivel.account"
const savedAccount = (): Provider | null => {
  try { const v = localStorage.getItem(ACCOUNT_KEY); return v === "google" || v === "apple" ? v : null } catch { return null }
}

/** Los botones de Google y Apple, con los colores y el logo que piden sus guías de marca. */
function AccountButtons({ onPick }: { onPick: (p: Provider) => void }) {
  return (
    <div className="account-buttons">
      <button type="button" className="account google" onClick={() => onPick("google")}>
        <img src={googleLogo} alt="" width={18} height={18} />
        Continuar con Google
      </button>
      <button type="button" className="account apple" onClick={() => onPick("apple")}>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M17.05 20.28c-.98.95-2.05.8-3.08.35-1.09-.46-2.09-.48-3.24 0-1.44.62-2.2.44-3.06-.35C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.8 1.18-.24 2.31-.93 3.57-.84 1.51.12 2.65.72 3.4 1.8-3.12 1.87-2.38 5.98.48 7.13-.57 1.5-1.31 2.99-2.54 4.09zM12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z"/></svg>
        Continuar con Apple
      </button>
    </div>
  )
}

const errorText = (e: unknown) => (e instanceof Error && e.message ? e.message : "No se pudo sincronizar")

/**
 * Copia cifrada y otros dispositivos (docs/SINCRONIZACION.md). Opcional: sin activarla, todo sigue en este navegador.
 * Activar pide el consentimiento explícito para datos de salud (RGPD art. 9.2.a) y la edad mínima, sin marcar.
 */
export function SyncCard({ core, onDone, joinOnly = false }:
  { core: WebApp; onDone: (message: string | null) => void; joinOnly?: boolean }) {
  // joinOnly: «Iniciar sesión» de la portada: entrar con una cuenta o escribir el código
  const [view, setView] = useState<View>("idle")
  const [account, setAccount] = useState<Provider | null>(savedAccount)
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

  const signOut = () => run(async () => {
    const keys = await loadKeys()
    if (keys) await core.signOutAccount(keys)
    try { localStorage.removeItem(ACCOUNT_KEY) } catch { /* sin almacenamiento: no pasa nada */ }
    setAccount(null)
    return "Ya no entras con tu cuenta en ningún dispositivo. La sincronización con el código sigue"
  })

  const forget = () => run(async () => {
    try { localStorage.removeItem(ACCOUNT_KEY) } catch { /* sin almacenamiento: no pasa nada */ }
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

  if (core.syncNeedsConsent) {
    return (
      <section className="module">
        <h3>Sincronización en pausa</h3>
        <p className="muted">{core.syncPolicyChanged}</p>
        <button className="primary" disabled={busy}
          onClick={() => run(async () => { core.acceptSyncPolicy(); return (await core.syncNow()) ?? "Sincronizado" })}>Acepto y sigo sincronizando</button>
        <div className="row wrap">
          <button className="link" disabled={busy} onClick={forget}>Dejar de sincronizar aquí</button>
          <button className="link" disabled={busy} onClick={erase}>Borrar la copia del servidor</button>
        </div>
      </section>
    )
  }

  if (core.syncActive) {
    return (
      <section className="module">
        <h3>Sincronización activada</h3>
        <p className="muted">{account ? `Entras con ${NAMES[account]}: en otro dispositivo, entra con la misma cuenta.`
          : "Una copia cifrada en el servidor que solo tus dispositivos pueden leer."}</p>
        <button className="primary" disabled={busy}
          onClick={() => run(async () => (await core.syncNow()) ?? "Sincronizado")}>Sincronizar ahora</button>
        <div className="row wrap">
          {account && <button className="link" disabled={busy} onClick={signOut}>Dejar de entrar con {NAMES[account]}</button>}
          <button className="link" disabled={busy} onClick={forget}>Dejar de sincronizar aquí</button>
          <button className="link" disabled={busy} onClick={erase}>Borrar la copia del servidor</button>
        </div>
      </section>
    )
  }

  if (view === "idle") {
    return (
      <section className="module">
        <h3>{joinOnly ? "Entra con tu cuenta" : "Usar en otros dispositivos"}</h3>
        <p className="muted">Entra con Google o Apple y tendrás tus datos en todos tus dispositivos, sin códigos.
          {!joinOnly && " Opcional: sin activarlo, tus datos siguen solo en este navegador."}</p>
        <AccountButtons onPick={setView} />
        <p className="muted small">O con un código, si prefieres que solo tus dispositivos puedan leer la copia:</p>
        <div className="row wrap">
          {!joinOnly && <button className="secondary" onClick={() => setView("create")}>Activar con un código</button>}
          <button className="secondary" onClick={() => setView("join")}>Tengo un código</button>
        </div>
      </section>
    )
  }

  if (view === "google" || view === "apple") {
    const provider = view
    return (
      <section className="module">
        <h3>Continuar con {NAMES[provider]}</h3>
        <label className="check">
          <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
          <span>Acepto que Brivel guarde en su servidor de la UE una copia de mis datos, también los de salud, y la clave
            para abrirla, para recuperarlos entrando con mi cuenta. De {NAMES[provider]} solo recibe un identificador, sin
            correo ni nombre. Puedo retirarlo cuando quiera borrando la copia.
            {" "}<a href="privacidad">Política de privacidad</a></span>
        </label>
        <label className="check">
          <input type="checkbox" checked={adult} onChange={(e) => setAdult(e.target.checked)} />
          <span>Tengo 14 años o más</span>
        </label>
        <p className="muted small">Con un código, en cambio, la copia va cifrada de forma que solo tus dispositivos pueden leerla.</p>
        <div className="row wrap">
          <button className="primary" disabled={!consent || !adult}
            onClick={() => { location.href = `/api/cuenta/${provider}?plataforma=web` }}>Continuar</button>
          <button className="secondary" onClick={() => setView("idle")}>Cancelar</button>
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
          {" "}<a href="privacidad">Política de privacidad</a></span>
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
