// Garmin Connect en la web (política de privacidad, apartado 5). Este navegador crea un par de claves X25519 y solo da
// al servidor la pública: el relevo cifra cada envío de Garmin con ella y solo este navegador puede abrirlo. La clave
// privada es una CryptoKey no exportable en IndexedDB, como la de la sincronización (sync.ts).
import type { WebApp } from "brivel-shared"
import { request } from "./sync"

/** Versión de la política que se acepta al conectar (CUMPLIMIENTO #6: fecha y versión). */
export const GARMIN_POLICY = "2026-11-03.3"

interface Stored { key: CryptoKey; pub: string; state?: string; token?: string; consentAt: string; policy: string }

const API = "/api/garmin"
const INFO = new TextEncoder().encode("brivel-garmin-1")
const b64url = (b: Uint8Array) => btoa(Array.from(b, (c) => String.fromCharCode(c)).join("")).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
const fromB64url = (s: string) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (s.length % 4)) % 4)), (c) => c.charCodeAt(0))

const load = () => request<Stored | undefined>("readonly", (s) => s.get("garmin")).catch(() => undefined)
const store = (v: Stored) => request("readwrite", (s) => s.put(v, "garmin"))
const clear = () => request("readwrite", (s) => s.delete("garmin")).catch(() => undefined)

async function fail(res: Response): Promise<never> {
  const body = await res.json().catch(() => null) as { error?: string } | null
  throw new Error(body?.error ?? "Garmin no responde: prueba más tarde")
}

export const garminConnected = async () => !!(await load())?.token

/** Crea las claves, pide la dirección de Garmin y la abre; al volver, `finishGarmin` recoge el buzón. */
export async function startGarmin(): Promise<void> {
  const keys = (await crypto.subtle.generateKey({ name: "X25519" }, false, ["deriveBits"])) as CryptoKeyPair
  const pub = b64url(new Uint8Array(await crypto.subtle.exportKey("raw", keys.publicKey)))
  const res = await fetch(`${API}/start`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ key: pub, back: "web" }) })
  if (!res.ok) await fail(res)
  const { url, state } = await res.json() as { url: string; state: string }
  await store({ key: keys.privateKey, pub, state, consentAt: new Date().toISOString(), policy: GARMIN_POLICY })
  location.assign(url)
}

/** Tras volver de Garmin (brivel.app/#garmin): recoge una sola vez el token del buzón. */
export async function finishGarmin(): Promise<void> {
  const s = await load()
  if (!s?.state) throw new Error("La conexión con Garmin caducó: vuelve a empezar")
  for (let i = 0; i < 10; i++) {
    const res = await fetch(`${API}/claim`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ state: s.state }) })
    if (res.status === 202) { await new Promise((r) => setTimeout(r, 1000)); continue }
    if (!res.ok) { await clear(); await fail(res) }
    const { token } = await res.json() as { token: string }
    await store({ ...s, state: undefined, token })
    return
  }
  throw new Error("Garmin tarda en responder: vuelve a intentarlo")
}

/** Descarga lo pendiente, lo abre aquí y lo entrega al núcleo; después el servidor lo borra. Cuántos envíos llegaron. */
export async function pullGarmin(core: WebApp): Promise<number> {
  const s = await load()
  if (!s?.token) return 0
  const auth = { Authorization: `Bearer ${s.token}` }
  let count = 0
  for (;;) {
    const res = await fetch(`${API}/inbox`, { headers: auth })
    if (res.status === 401) { await clear(); throw new Error("Garmin se desconectó: vuelve a conectar si quieres seguir") }
    if (!res.ok) await fail(res)
    const { items, more } = await res.json() as { items: { id: number; data: string }[]; more: boolean }
    if (!items.length) return count
    for (const item of items) core.importGarmin(await open(fromB64url(item.data), s.key, fromB64url(s.pub)))
    const ack = await fetch(`${API}/inbox/ack`, { method: "POST", headers: { ...auth, "Content-Type": "application/json" },
      body: JSON.stringify({ upTo: items[items.length - 1].id }) })
    if (!ack.ok) await fail(ack)
    count += items.length
    if (!more) return count
  }
}

/** Desconecta en Garmin y en el servidor; lo ya descargado sigue en este navegador. */
export async function disconnectGarmin(): Promise<void> {
  const s = await load()
  if (s?.token) {
    const res = await fetch(`${API}/disconnect`, { method: "POST", headers: { Authorization: `Bearer ${s.token}` } })
    if (!res.ok && res.status !== 401) await fail(res)
  }
  await clear()
}

// 0x01 | clave efímera (32) | nonce (12) | cifrado: X25519, HKDF-SHA256 y AES-256-GCM (brivel-garmin/src/seal.ts)
async function open(sealed: Uint8Array<ArrayBuffer>, key: CryptoKey, pub: Uint8Array<ArrayBuffer>): Promise<string> {
  if (sealed[0] !== 1) throw new Error("Este envío de Garmin es de una versión más nueva: actualiza Brivel")
  const epk = sealed.slice(1, 33)
  const peer = await crypto.subtle.importKey("raw", epk, { name: "X25519" }, false, [])
  const shared = await crypto.subtle.deriveBits({ name: "X25519", public: peer }, key, 256)
  const base = await crypto.subtle.importKey("raw", shared, "HKDF", false, ["deriveKey"])
  const salt = new Uint8Array(64)
  salt.set(epk)
  salt.set(pub, 32)
  const aes = await crypto.subtle.deriveKey({ name: "HKDF", hash: "SHA-256", salt, info: INFO }, base, { name: "AES-GCM", length: 256 }, false, ["decrypt"])
  return new TextDecoder().decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: sealed.slice(33, 45) }, aes, sealed.slice(45)))
}
