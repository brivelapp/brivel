import { useEffect, useState } from "react"
import type { WebApp } from "brivel-shared"
import { disconnectGarmin, finishGarmin, garminConnected, pullGarmin, startGarmin } from "./garmin"

const errorText = (e: unknown) => (e instanceof Error && e.message ? e.message : "No se pudo conectar con Garmin")

/**
 * Garmin Connect™ (política de privacidad, apartado 5). Opcional y en pruebas. Conectar pide el consentimiento
 * explícito para datos de salud (RGPD art. 9.2.a) y la edad mínima, sin marcar, como la sincronización.
 */
export function GarminCard({ core, onDone }: { core: WebApp; onDone: (message: string | null) => void }) {
  const [connected, setConnected] = useState(false)
  const [asking, setAsking] = useState(false)
  const [consent, setConsent] = useState(false)
  const [adult, setAdult] = useState(false)
  const [busy, setBusy] = useState(false)

  const run = async (f: () => Promise<string | null>) => {
    setBusy(true)
    try {
      onDone(await f())
    } catch (e) {
      onDone(errorText(e))
    }
    setConnected(await garminConnected())
    setBusy(false)
  }
  const pull = async () => {
    const n = await pullGarmin(core)
    return n ? `Datos de Garmin al día (${n} ${n === 1 ? "envío" : "envíos"})` : "No hay datos nuevos de Garmin"
  }

  useEffect(() => {
    // De vuelta de la pantalla de permisos de Garmin
    if (location.hash === "#garmin") {
      history.replaceState(null, "", location.pathname)
      run(async () => { await finishGarmin(); await pullGarmin(core); return "Garmin conectado: los datos llegan cuando sincronices el reloj" })
    } else garminConnected().then(setConnected)
  }, [])

  const since = core.garminSyncedAt()
  if (connected) {
    return (
      <section className="module">
        <h3>Garmin Connect™</h3>
        <p className="muted">Conectado. {since ? `Último envío: ${core.dateLabel(since)}.` : "Aún no ha llegado nada: sincroniza el reloj."}</p>
        <button className="primary" disabled={busy} onClick={() => run(pull)}>Descargar ahora</button>
        <button className="link" disabled={busy} onClick={() => confirm("¿Desconectar Garmin? El servidor borra al momento lo pendiente y deja de recibir tus datos. Lo ya descargado sigue en este navegador.")
          && run(async () => { await disconnectGarmin(); return "Garmin desconectado" })}>Desconectar Garmin</button>
      </section>
    )
  }
  if (!asking) {
    return (
      <section className="module">
        <h3>Garmin Connect™</h3>
        <p className="muted">Opcional y en pruebas. Tus actividades, sueño, pasos, pulso en reposo y variabilidad cardiaca
          del reloj, para adaptar tu plan.</p>
        <button className="secondary" onClick={() => setAsking(true)}>Conectar Garmin Connect™</button>
      </section>
    )
  }
  return (
    <section className="module">
      <h3>Conectar Garmin Connect™</h3>
      <p className="muted small">Garmin manda tus datos al servidor de Brivel, que los cifra al llegar para este navegador
        y los borra en cuanto los descarga (o a los 30 días). Brivel no puede leerlos ni los vende.</p>
      <label className="check">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>Acepto que Brivel reciba mis datos de Garmin, también los de salud, para adaptar mi plan. Puedo retirarlo
          cuando quiera desconectando. {" "}<a href="privacidad.html#garmin">Política de privacidad</a></span>
      </label>
      <label className="check">
        <input type="checkbox" checked={adult} onChange={(e) => setAdult(e.target.checked)} />
        <span>Tengo 14 años o más</span>
      </label>
      <div className="row wrap">
        <button className="primary" disabled={busy || !consent || !adult} onClick={() => run(async () => { await startGarmin(); return null })}>Ir a Garmin</button>
        <button className="secondary" disabled={busy} onClick={() => setAsking(false)}>Cancelar</button>
      </div>
    </section>
  )
}
