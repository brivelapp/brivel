import { useState } from "react"
import type { WebApp } from "brivel-shared"

const errorText = (e: unknown) => (e instanceof Error && e.message ? e.message : "No se pudo conectar con intervals.icu")

/**
 * intervals.icu: conectar abre su pantalla de permisos; la vuelta (brivel.app/intervals) la recoge App. Esa dirección
 * la sirve dist/intervals.html, copia de index.html que hace `npm run build`.
 */
export function IntervalsCard({ core, onDone }: { core: WebApp; onDone: (message: string | null) => void }) {
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

  const athlete = core.intervalsAthlete
  return (
    <section className="module">
      {athlete ? (
        <>
          <p className="ok-text">Conectado · {athlete}</p>
          <p className="muted small-text">Al cerrar la pestaña hay que volver a conectar.</p>
          <button className="link" disabled={busy}
            onClick={() => run(async () => { await core.disconnectIntervals(); return "intervals.icu desvinculado" })}>Desconectar</button>
        </>
      ) : (
        <>
          <p>Gratis; recoge los datos de cualquier reloj.</p>
          <details><summary>¿Qué relojes?</summary>
            <p className="muted small-text">intervals.icu recoge los datos de Garmin, Polar, Coros, Suunto, Wahoo y otros. Inicia
              sesión allí y da permiso a Brivel.</p></details>
          <button className="secondary" disabled={busy}
            onClick={() => run(async () => { location.assign(await core.startIntervals()); return null })}>Conectar con intervals.icu</button>
        </>
      )}
    </section>
  )
}
