// Entrar con Google o Apple (decisión de Gabriel, 2026-11-19; docs/SINCRONIZACION.md, «Cuentas»). El servidor guarda el
// secreto de sincronización de cada cuenta cifrado con CUENTAS_CLAVE y se lo da al dispositivo que entra: con él, el
// dispositivo sincroniza como con el código. Quien entra así acepta que Brivel podría leer su copia (política v8); el
// código solo sigue siendo de extremo a extremo. Del proveedor solo se guarda un identificador (HMAC de su `sub`): ni
// correo ni nombre, que tampoco se piden (Google: scope openid; Apple: sin scope).

/** Secretos del Worker (`wrangler secret put`); sin los del proveedor, ese botón responde 503. */
export interface Secrets {
  CUENTAS_CLAVE?: string  // AES-256 en base64: cifra los secretos de sincronización
  CUENTAS_PIMIENTA?: string  // HMAC en base64: el identificador de cada cuenta
  GOOGLE_CLIENT_ID?: string
  GOOGLE_CLIENT_SECRET?: string
  APPLE_SERVICES_ID?: string  // el client_id de la web
  APPLE_TEAM_ID?: string
  APPLE_KEY_ID?: string
  APPLE_PRIVATE_KEY?: string  // la clave .p8 (PKCS#8 en PEM)
}
type E = Env & Secrets

const TICKET_MINUTES = 2
const COOKIE = "__Host-brivel-cuenta"
const SECRET_BYTES = 16
const TOKEN_LABEL = "brivel-sync-token-v1"  // el mismo que shared/.../sync/Vault.kt

const enc = new TextEncoder()
const b64url = (b: ArrayBuffer | Uint8Array) =>
  btoa(String.fromCharCode(...new Uint8Array(b))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
const unb64url = (s: string) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0))
const unb64 = (s: string) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0))
const hex = (b: ArrayBuffer) => Array.from(new Uint8Array(b), (x) => x.toString(16).padStart(2, "0")).join("")
// TextEncoder y Uint8Array.from dan Uint8Array<ArrayBufferLike>: WebCrypto pide ArrayBuffer, que es lo que hay detrás
const buf = (b: Uint8Array) => b as Uint8Array<ArrayBuffer>
const sha256 = async (b: Uint8Array) => hex(await crypto.subtle.digest("SHA-256", buf(b)))
const random = (n: number) => crypto.getRandomValues(new Uint8Array(n))

const PROVIDERS = ["google", "apple"] as const
type Provider = (typeof PROVIDERS)[number]
const configured = (env: E, p: Provider) => !!env.CUENTAS_CLAVE && !!env.CUENTAS_PIMIENTA &&
  (p === "google" ? !!env.GOOGLE_CLIENT_ID && !!env.GOOGLE_CLIENT_SECRET
    : !!env.APPLE_SERVICES_ID && !!env.APPLE_TEAM_ID && !!env.APPLE_KEY_ID && !!env.APPLE_PRIVATE_KEY)

/** /api/cuenta/*: null si la ruta no es de aquí. */
export async function cuentas(request: Request, env: E, ctx: ExecutionContext, ip: string): Promise<Response | null> {
  const url = new URL(request.url)
  const m = /^\/api\/cuenta\/(google|apple)(\/vuelta)?$/.exec(url.pathname)
  if (m && request.method === "GET") {
    const provider = m[1] as Provider
    if (!configured(env, provider)) return json(503, { error: "Entrar con esta cuenta aún no está disponible" })
    return m[2] ? back(url, request, env, provider) : start(url, env, provider)
  }
  if (url.pathname === "/api/cuenta/canjear" && request.method === "POST") return redeem(request, env, ip)
  if (url.pathname === "/api/cuenta" && request.method === "DELETE") {
    const vault = await vaultOfBearer(request)
    if (!vault) return json(401, { error: "Falta el token de sincronización o no es válido" })
    await forgetVault(env, ctx, vault)
    return new Response(null, { status: 204 })
  }
  return url.pathname.startsWith("/api/cuenta") ? json(404, { error: "No encontrado" }) : null
}

// ------------------------------------------------------------ ida y vuelta al proveedor

// Siempre https (Google y Apple solo aceptan esas); en local, wrangler dev presenta la petición como http://brivel.app
const redirectUri = (url: URL, p: Provider) => `https://${url.host}/api/cuenta/${p}/vuelta`

/** Al proveedor, con un `state` (y en Google, PKCE) que la vuelta comprueba en una cookie de 10 minutos. */
async function start(url: URL, env: E, p: Provider): Promise<Response> {
  const app = url.searchParams.get("plataforma") === "app"
  const state = b64url(random(16))
  const verifier = b64url(random(32))
  const q = p === "google"
    ? new URLSearchParams({ client_id: env.GOOGLE_CLIENT_ID!, redirect_uri: redirectUri(url, p), response_type: "code",
        scope: "openid", state, prompt: "select_account", code_challenge_method: "S256",
        code_challenge: b64url(await crypto.subtle.digest("SHA-256", enc.encode(verifier))) })
    // Sin scope (ni nombre ni correo), Apple deja volver por GET: así la cookie SameSite=Lax llega
    : new URLSearchParams({ client_id: env.APPLE_SERVICES_ID!, redirect_uri: redirectUri(url, p), response_type: "code",
        response_mode: "query", state })
  const to = p === "google" ? "https://accounts.google.com/o/oauth2/v2/auth" : "https://appleid.apple.com/auth/authorize"
  return new Response(null, { status: 302, headers: {
    Location: `${to}?${q}`, "Cache-Control": "no-store",
    "Set-Cookie": `${COOKIE}=${state}.${verifier}.${app ? "app" : "web"}; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=600`,
  } })
}

/** La vuelta: canjea el código del proveedor, saca su `sub` y deja un billete de un solo uso para el dispositivo. */
async function back(url: URL, request: Request, env: E, p: Provider): Promise<Response> {
  const cookie = new RegExp(`(?:^|; )${COOKIE}=([^.;]+)\\.([^.;]+)\\.(app|web)`).exec(request.headers.get("Cookie") ?? "")
  const app = cookie?.[3] === "app"
  const done = (q: Record<string, string>) => new Response(null, { status: 302, headers: {
    Location: app ? `brivel://cuenta?${new URLSearchParams(q)}` : `/#${new URLSearchParams(q)}`, "Cache-Control": "no-store",
    "Set-Cookie": `${COOKIE}=; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=0`,
  } })
  const code = url.searchParams.get("code")
  if (!cookie || !code || url.searchParams.get("state") !== cookie[1]) {
    return done({ "cuenta-error": url.searchParams.get("error") === "access_denied" || url.searchParams.get("error") === "user_cancelled_authorize"
      ? "Cancelado" : "No se pudo entrar: vuelve a intentarlo" })
  }
  try {
    const { sub, refresh } = p === "google" ? await google(env, code, cookie[2], redirectUri(url, p)) : await apple(env, code, redirectUri(url, p))
    const identity = await identityOf(env, p, sub)
    const ticket = b64url(random(32))
    const caduca = new Date(Date.now() + TICKET_MINUTES * 60_000).toISOString()
    await env.DB.prepare("insert into billetes (id, identidad, proveedor, revocar, caduca) values (?, ?, ?, ?, ?)")
      .bind(await sha256(enc.encode(ticket)), identity, p, refresh ? await seal(env, enc.encode(refresh), identity) : null, caduca).run()
    return done({ cuenta: ticket, proveedor: p })
  } catch {
    return done({ "cuenta-error": "No se pudo entrar: vuelve a intentarlo" })
  }
}

/** Google: el id_token llega directo de su servidor por TLS, así que basta con comprobar iss, aud y exp (OIDC §3.1.3.7). */
async function google(env: E, code: string, verifier: string, redirect: string): Promise<{ sub: string; refresh?: string }> {
  const res = await fetch("https://oauth2.googleapis.com/token", { method: "POST", body: new URLSearchParams({
    code, client_id: env.GOOGLE_CLIENT_ID!, client_secret: env.GOOGLE_CLIENT_SECRET!, redirect_uri: redirect,
    grant_type: "authorization_code", code_verifier: verifier }) })
  if (!res.ok) throw new Error(`Google ${res.status}`)
  const claims = idClaims((await res.json<{ id_token: string }>()).id_token)
  if (!["https://accounts.google.com", "accounts.google.com"].includes(claims.iss) || claims.aud !== env.GOOGLE_CLIENT_ID) throw new Error("id_token ajeno")
  return { sub: claims.sub }
}

/** Apple: igual, con el client_secret firmado (ES256) y el token de refresco, que hay que revocar al borrar la cuenta. */
async function apple(env: E, code: string, redirect: string): Promise<{ sub: string; refresh?: string }> {
  const res = await fetch("https://appleid.apple.com/auth/token", { method: "POST", body: new URLSearchParams({
    client_id: env.APPLE_SERVICES_ID!, client_secret: await appleSecret(env), code, grant_type: "authorization_code",
    redirect_uri: redirect }) })
  if (!res.ok) throw new Error(`Apple ${res.status}`)
  const body = await res.json<{ id_token: string; refresh_token?: string }>()
  const claims = idClaims(body.id_token)
  if (claims.iss !== "https://appleid.apple.com" || claims.aud !== env.APPLE_SERVICES_ID) throw new Error("id_token ajeno")
  return { sub: claims.sub, refresh: body.refresh_token }
}

export function idClaims(token: string): { iss: string; aud: string; sub: string } {
  const c = JSON.parse(new TextDecoder().decode(unb64url(token.split(".")[1] ?? "")))
  if (typeof c.sub !== "string" || !c.sub || typeof c.exp !== "number" || c.exp * 1000 < Date.now()) throw new Error("id_token no válido")
  return c
}

async function appleSecret(env: E): Promise<string> {
  const der = unb64(env.APPLE_PRIVATE_KEY!.replace(/-----[^-]+-----|\s/g, ""))
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"])
  const now = Math.floor(Date.now() / 1000)
  const part = (o: object) => b64url(enc.encode(JSON.stringify(o)))
  const body = `${part({ alg: "ES256", kid: env.APPLE_KEY_ID })}.${part({ iss: env.APPLE_TEAM_ID, iat: now, exp: now + 300,
    aud: "https://appleid.apple.com", sub: env.APPLE_SERVICES_ID })}`
  return `${body}.${b64url(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, enc.encode(body)))}`
}

// ------------------------------------------------------------ canjear el billete

/**
 * POST {billete, secreto}: el dispositivo manda su secreto (el de su código o uno nuevo). Si la cuenta ya existía,
 * recibe el suyo y se une a esa copia (juntando sus datos); si no, la cuenta se queda con el que mandó.
 */
async function redeem(request: Request, env: E, ip: string): Promise<Response> {
  if (!env.CUENTAS_CLAVE || !env.CUENTAS_PIMIENTA) return json(503, { error: "Entrar con una cuenta aún no está disponible" })
  const body = await request.json<{ billete?: unknown; secreto?: unknown }>().catch(() => ({}) as { billete?: unknown; secreto?: unknown })
  if (typeof body.billete !== "string" || typeof body.secreto !== "string") return json(400, { error: "Faltan el billete o el secreto" })
  const mine = (() => { try { return unb64url(body.secreto as string) } catch { return null } })()
  if (mine?.length !== SECRET_BYTES) return json(400, { error: "El secreto no es válido" })
  // De un solo uso: se borra al leerlo, aunque luego falle algo
  const ticket = await env.DB.prepare("delete from billetes where id = ? and caduca > ? returning identidad, proveedor, revocar")
    .bind(await sha256(enc.encode(body.billete)), new Date().toISOString())
    .first<{ identidad: string; proveedor: string; revocar: ArrayBuffer | null }>()
  if (!ticket) return json(410, { error: "El inicio de sesión caducó: vuelve a intentarlo" })
  const row = await env.DB.prepare("select secreto from identidades where id = ?").bind(ticket.identidad).first<{ secreto: ArrayBuffer }>()
  if (row) return json(200, { secreto: b64url(await open(env, new Uint8Array(row.secreto), ticket.identidad)), nueva: false })
  if (!(await env.ALTAS.limit({ key: ip })).success) return json(429, { error: "Demasiadas altas: espera un minuto" })
  await env.DB.prepare("insert into identidades (id, proveedor, secreto, boveda, revocar, creada) values (?, ?, ?, ?, ?, ?) on conflict (id) do nothing")
    .bind(ticket.identidad, ticket.proveedor, await seal(env, mine, ticket.identidad), await vaultOf(mine),
      ticket.revocar, new Date().toISOString()).run()
  // Si otro dispositivo entró a la vez con la misma cuenta, gana el primero: se devuelve el que quedó guardado
  const saved = await env.DB.prepare("select secreto from identidades where id = ?").bind(ticket.identidad).first<{ secreto: ArrayBuffer }>()
  const secret = await open(env, new Uint8Array(saved!.secreto), ticket.identidad)
  return json(200, { secreto: b64url(secret), nueva: b64url(secret) === body.secreto })
}

// ------------------------------------------------------------ borrar

/** Quita las cuentas de Google o Apple de esa copia; las de Apple, revocadas en Apple (lo exige su guía 5.1.1). */
export async function forgetVault(env: E, ctx: ExecutionContext, vault: string): Promise<void> {
  const { results } = await env.DB.prepare("delete from identidades where boveda = ? returning id, revocar").bind(vault)
    .all<{ id: string; revocar: ArrayBuffer | null }>()
  const apple = results.filter((r) => r.revocar)
  if (apple.length && configured(env, "apple")) ctx.waitUntil(Promise.all(apple.map(async (r) => {
    const token = new TextDecoder().decode(await open(env, new Uint8Array(r.revocar!), r.id))
    await fetch("https://appleid.apple.com/auth/revoke", { method: "POST", body: new URLSearchParams({
      client_id: env.APPLE_SERVICES_ID!, client_secret: await appleSecret(env), token, token_type_hint: "refresh_token" }) })
  })).catch(() => {}))
}

// ------------------------------------------------------------ claves

async function identityOf(env: E, p: Provider, sub: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", unb64(env.CUENTAS_PIMIENTA!), { name: "HMAC", hash: "SHA-256" }, false, ["sign"])
  return hex(await crypto.subtle.sign("HMAC", key, enc.encode(`${p}:${sub}`)))
}

const aes = (env: E) => crypto.subtle.importKey("raw", unb64(env.CUENTAS_CLAVE!), "AES-GCM", false, ["encrypt", "decrypt"])

async function seal(env: E, plain: Uint8Array, id: string): Promise<Uint8Array> {
  const iv = random(12)
  const sealed = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: enc.encode(id) }, await aes(env), buf(plain)))
  const out = new Uint8Array(12 + sealed.length)
  out.set(iv); out.set(sealed, 12)
  return out
}

async function open(env: E, blob: Uint8Array<ArrayBuffer>, id: string): Promise<Uint8Array<ArrayBuffer>> {
  return new Uint8Array(await crypto.subtle.decrypt({ name: "AES-GCM", iv: blob.slice(0, 12), additionalData: enc.encode(id) },
    await aes(env), blob.slice(12)))
}

/** La copia de ese secreto: SHA-256 del token, que sale del secreto con HKDF-SHA256 como en Vault.kt. */
export async function vaultOf(secret: Uint8Array): Promise<string> {
  const key = await crypto.subtle.importKey("raw", buf(secret), "HKDF", false, ["deriveBits"])
  const token = await crypto.subtle.deriveBits({ name: "HKDF", hash: "SHA-256", salt: new Uint8Array(32), info: enc.encode(TOKEN_LABEL) }, key, 256)
  return sha256(new Uint8Array(token))
}

async function vaultOfBearer(request: Request): Promise<string | null> {
  const token = /^Bearer ([A-Za-z0-9_-]{43})$/.exec(request.headers.get("Authorization") ?? "")?.[1]
  return token ? sha256(unb64url(token)) : null
}

const json = (status: number, body: object) => Response.json(body, { status, headers: { "Cache-Control": "no-store" } })
