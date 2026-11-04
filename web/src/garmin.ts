// Garmin Connect en la web (política de privacidad, apartado 5), como GarminRelay.kt en las apps. La conexión (clave
// X25519 y token del buzón) va en los datos del núcleo: la sincronización la lleva cifrada a los demás dispositivos de
// la sesión, y cualquiera de ellos abre el buzón. Mientras se conecta, la clave nueva espera en IndexedDB.
import type { WebApp } from "brivel-shared"
import { request } from "./sync"

interface Pending { state: string; privateKey: string; publicKey: string }
interface Link { privateKey: string; publicKey: string; token: string }

const API = "/api/garmin"
const INFO = new TextEncoder().encode("brivel-garmin-1")
// PKCS #8 de una clave X25519 sin la clave: WebCrypto no importa la privada en bruto
const PKCS8 = Uint8Array.from([0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x04, 0x22, 0x04, 0x20])
const b64url = (b: Uint8Array) => btoa(Array.from(b, (c) => String.fromCharCode(c)).join("")).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
const fromB64url = (s: string) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (s.length % 4)) % 4)), (c) => c.charCodeAt(0))

const loadPending = () => request<Pending | undefined>("readonly", (s) => s.get("garmin-pending")).catch(() => undefined)
const savePending = (p: Pending) => request("readwrite", (s) => s.put(p, "garmin-pending"))
const clearPending = () => request("readwrite", (s) => s.delete("garmin-pending")).catch(() => undefined)
// La conexión de la primera versión (2026-11-03) guardaba una clave no exportable aquí: no se puede pasar a la sesión
const clearOld = () => request("readwrite", (s) => s.delete("garmin")).catch(() => undefined)

async function fail(res: Response): Promise<never> {
  const body = await res.json().catch(() => null) as { error?: string } | null
  throw new Error(body?.error ?? "Garmin no responde: prueba más tarde")
}

/** Crea las claves, pide la dirección de Garmin y la abre; al volver, `finishGarmin` recoge el buzón. */
export async function startGarmin(): Promise<void> {
  const keys = (await crypto.subtle.generateKey({ name: "X25519" }, true, ["deriveBits"])) as CryptoKeyPair
  const publicKey = b64url(new Uint8Array(await crypto.subtle.exportKey("raw", keys.publicKey)))
  const privateKey = b64url(new Uint8Array(await crypto.subtle.exportKey("pkcs8", keys.privateKey)).slice(-32))
  const res = await fetch(`${API}/start`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ key: publicKey, back: "web" }) })
  if (!res.ok) await fail(res)
  const { url, state } = await res.json() as { url: string; state: string }
  await savePending({ state, privateKey, publicKey })
  location.assign(url)
}

/** Tras volver de Garmin (brivel.app/#garmin): recoge una sola vez el token del buzón y lo guarda en la conexión. */
export async function finishGarmin(core: WebApp): Promise<void> {
  const p = await loadPending()
  if (!p) throw new Error("La conexión con Garmin caducó: vuelve a empezar")
  try {
    for (let i = 0; i < 10; i++) {
      const res = await fetch(`${API}/claim`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ state: p.state }) })
      if (res.status === 202) { await new Promise((r) => setTimeout(r, 1000)); continue }
      if (!res.ok) await fail(res)
      const { token } = await res.json() as { token: string }
      core.setGarminLink(p.privateKey, p.publicKey, token)
      await clearOld()
      return
    }
    throw new Error("Garmin tarda en responder: vuelve a intentarlo")
  } finally {
    await clearPending()
  }
}

/** Descarga lo pendiente, lo abre aquí y lo entrega al núcleo; después el servidor lo borra. Cuántos envíos llegaron. */
export async function pullGarmin(core: WebApp): Promise<number> {
  const json = core.garminLinkJson()
  if (!json) return 0
  const link: Link = JSON.parse(json)
  const der = new Uint8Array(48)
  der.set(PKCS8)
  der.set(fromB64url(link.privateKey), 16)
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "X25519" }, false, ["deriveBits"])
  const pub = fromB64url(link.publicKey)
  const auth = { Authorization: `Bearer ${link.token}` }
  let count = 0
  for (;;) {
    const res = await fetch(`${API}/inbox`, { headers: auth })
    if (res.status === 401) { core.clearGarminLink(); throw new Error("Garmin se desconectó: vuelve a conectar si quieres seguir") }
    if (!res.ok) await fail(res)
    const { items, more } = await res.json() as { items: { id: number; data: string }[]; more: boolean }
    if (!items.length) return count
    for (const item of items) core.importGarmin(await open(fromB64url(item.data), key, pub))
    const ack = await fetch(`${API}/inbox/ack`, { method: "POST", headers: { ...auth, "Content-Type": "application/json" },
      body: JSON.stringify({ upTo: items[items.length - 1].id }) })
    if (!ack.ok) await fail(ack)
    count += items.length
    if (!more) return count
  }
}

/** Manda al calendario de Garmin Connect las sesiones de hoy (o la de ese deporte); el relevo no las guarda. */
export async function sendToWatch(core: WebApp, sport: string): Promise<void> {
  const json = core.garminLinkJson()
  if (!json) throw new Error("Conecta Garmin en Perfil para enviar sesiones al reloj")
  const res = await fetch(`${API}/workouts`, { method: "POST", body: core.watchWorkoutsJson(sport),
    headers: { Authorization: `Bearer ${(JSON.parse(json) as Link).token}`, "Content-Type": "application/json" } })
  if (res.status === 401) { core.clearGarminLink(); throw new Error("Garmin se desconectó: vuelve a conectar si quieres seguir") }
  if (!res.ok) await fail(res)
}

/** Desconecta en Garmin y en el servidor, para todos los dispositivos de la sesión; lo descargado se queda. */
export async function disconnectGarmin(core: WebApp): Promise<void> {
  const json = core.garminLinkJson()
  if (json) {
    const res = await fetch(`${API}/disconnect`, { method: "POST", headers: { Authorization: `Bearer ${(JSON.parse(json) as Link).token}` } })
    if (!res.ok && res.status !== 401) await fail(res)
  }
  core.clearGarminLink()
  await clearOld()
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
