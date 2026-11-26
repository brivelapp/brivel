// API de Brivel. /api/boveda guarda la copia cifrada de la sincronización (docs/SINCRONIZACION.md): con el código solo,
// el servidor no puede leerla; si se entra con Google o Apple, guarda el secreto de esa cuenta (cuentas.ts). Sin CORS (la web es del mismo origen) y sin registrar cabeceras ni cuerpos: el token iría en ellos.

import { cuentas, forgetVault, type Secrets } from "./cuentas"

// Sin el tope de 1 MB (Gabriel, 2026-11-19): queda uno alto solo contra abusos; una copia normal ocupa pocos MB
const MAX_BYTES = 16 * 1024 * 1024
const CHUNK = 1024 * 1024  // D1 admite como mucho 2 MB por fila: la copia va en trozos (migrations/0002_trozos.sql)
const UNUSED_DAYS = 365  // copias sin subir ni bajar en 12 meses: se borran (política de privacidad, apartado 4)

export default {
  async fetch(request, env: Env & Secrets, ctx) {
    const { pathname } = new URL(request.url)
    // Garmin Connect va en un Worker privado (su documentación es confidencial): se le pasa tal cual, con sus propios
    // límites, porque los envíos de Garmin no deben chocar con el límite por IP de la web
    if (pathname.startsWith("/api/garmin/")) return env.GARMIN ? env.GARMIN.fetch(request) : error(404, "No encontrado")
    const ip = request.headers.get("CF-Connecting-IP") ?? "local"
    if (!(await env.POR_IP.limit({ key: ip })).success) return error(429, "Demasiadas peticiones: espera un minuto")

    if (pathname === "/api/estado") {
      const row = await env.DB.prepare("select 1 as ok").first<{ ok: number }>()
      return Response.json({ ok: row?.ok === 1 })
    }
    const handled = await cuentas(request, env, ctx, ip)
    if (handled) return handled
    if (pathname !== "/api/boveda") return error(404, "No encontrado")

    const id = await account(request)
    if (!id) return error(401, "Falta el token de sincronización o no es válido")
    switch (request.method) {
      case "GET": {
        // En un lote, que es una transacción: la versión y los trozos son siempre de la misma subida
        const [head, chunks] = await env.DB.batch<{ version: number; datos: ArrayBuffer | number[] }>([
          env.DB.prepare("select version, datos from bovedas where id = ?").bind(id),
          env.DB.prepare("select datos from trozos where id = ? order by n").bind(id),
        ])
        const row = head.results[0]
        if (!row) return error(404, "No hay copia: el código cambió o se borró")
        // Las copias de antes de los trozos siguen enteras en bovedas.datos
        const parts = chunks.results.length ? chunks.results.map((r) => new Uint8Array(r.datos)) : [new Uint8Array(row.datos)]
        // Bajar también cuenta como uso; como mucho una escritura al día por copia
        const today = new Date().toISOString().slice(0, 10)
        await env.DB.prepare("update bovedas set usado = ? where id = ? and usado < ?").bind(new Date().toISOString(), id, today).run()
        return new Response(concat(parts), {
          headers: { "Content-Type": "application/octet-stream", ETag: `"${row.version}"`, "Cache-Control": "no-store" },
        })
      }
      case "PUT":
        return put(request, env, id, ip)
      case "DELETE":
        await env.DB.batch([
          env.DB.prepare("delete from trozos where id = ?").bind(id),
          env.DB.prepare("delete from bovedas where id = ?").bind(id),
        ])
        await forgetVault(env, ctx, id)  // y las cuentas de Google o Apple que entraban en ella
        return new Response(null, { status: 204 })
      default:
        return error(405, "Método no permitido")
    }
  },

  // Cada día (wrangler.jsonc, triggers): borra las copias que ningún dispositivo ha usado en 12 meses
  async scheduled(_controller, env: Env & Secrets, ctx) {
    const cutoff = new Date(Date.now() - UNUSED_DAYS * 24 * 60 * 60 * 1000).toISOString()
    const { results } = await env.DB.prepare("select id from bovedas where usado < ?").bind(cutoff).all<{ id: string }>()
    for (const { id } of results) await forgetVault(env, ctx, id)
    await env.DB.prepare("delete from billetes where caduca < ?").bind(new Date().toISOString()).run()
    await env.DB.batch([
      env.DB.prepare("delete from trozos where id in (select id from bovedas where usado < ?)").bind(cutoff),
      env.DB.prepare("delete from bovedas where usado < ?").bind(cutoff),
    ])
  },
} satisfies ExportedHandler<Env & Secrets>

/** If-None-Match: * crea la cuenta (versión 1); If-Match: "n" guarda la n + 1 solo si nadie subió antes. */
async function put(request: Request, env: Env, id: string, ip: string): Promise<Response> {
  // Content-Length obligatorio: así no se lee entero un cuerpo enorme antes de rechazarlo
  const declared = Number(request.headers.get("Content-Length"))
  if (!request.headers.has("Content-Length") || !Number.isInteger(declared)) return error(411, "Falta Content-Length")
  if (declared > MAX_BYTES) return error(413, "La copia pasa de 16 MB")
  const body = await request.arrayBuffer()
  if (body.byteLength === 0 || body.byteLength > MAX_BYTES) return error(413, "La copia está vacía o pasa de 16 MB")
  const write = crypto.randomUUID()  // esta subida: solo ella escribe sus trozos

  const now = new Date().toISOString()
  if (request.headers.get("If-None-Match") === "*") {
    if (!(await env.ALTAS.limit({ key: ip })).success) return error(429, "Demasiadas altas: espera un minuto")
    const [created] = await env.DB.batch([
      env.DB.prepare("insert into bovedas (id, version, datos, usado, escritura) values (?, 1, x'', ?, ?) on conflict (id) do nothing")
        .bind(id, now, write),
      ...chunkWrites(env, id, write, body),
    ])
    return created.meta.changes === 1 ? saved(201, 1) : error(409, "Ya hay una copia: descárgala y combina")
  }
  const match = /^"(\d+)"$/.exec(request.headers.get("If-Match") ?? "")
  if (!match) return error(428, "Falta If-Match o If-None-Match")
  if (!(await env.SUBIDAS.limit({ key: id })).success) return error(429, "Demasiadas subidas: espera un minuto")
  const version = Number(match[1])
  // Atómico (un lote es una transacción): si otro dispositivo subió antes, la versión ya no coincide, no cambia
  // ninguna fila y los trozos, que exigen la marca de esta subida, tampoco
  const [updated] = await env.DB.batch([
    env.DB.prepare("update bovedas set version = ?, datos = x'', usado = ?, escritura = ? where id = ? and version = ?")
      .bind(version + 1, now, write, id, version),
    ...chunkWrites(env, id, write, body),
  ])
  return updated.meta.changes === 1 ? saved(200, version + 1) : error(409, "Otro dispositivo subió antes: descarga y combina")
}

/** Cambia los trozos de la cuenta por los de `body`, solo si la fila de bovedas lleva la marca de esta subida. */
function chunkWrites(env: Env, id: string, write: string, body: ArrayBuffer): D1PreparedStatement[] {
  const mine = "exists (select 1 from bovedas where id = ? and escritura = ?)"
  const out = [env.DB.prepare(`delete from trozos where id = ? and ${mine}`).bind(id, id, write)]
  for (let n = 0; n * CHUNK < body.byteLength; n++) {
    out.push(env.DB.prepare(`insert into trozos (id, n, datos) select ?, ?, ? where ${mine}`)
      .bind(id, n, body.slice(n * CHUNK, (n + 1) * CHUNK), id, write))
  }
  return out
}

function concat(parts: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(parts.reduce((sum, p) => sum + p.byteLength, 0))
  let at = 0
  for (const p of parts) { out.set(p, at); at += p.byteLength }
  return out
}

/** La cuenta es SHA-256 del token (32 bytes en base64url sin relleno); el token no se guarda. */
async function account(request: Request): Promise<string | null> {
  const token = /^Bearer ([A-Za-z0-9_-]{43})$/.exec(request.headers.get("Authorization") ?? "")?.[1]
  if (!token) return null
  const bytes = Uint8Array.from(atob(token.replace(/-/g, "+").replace(/_/g, "/") + "="), (c) => c.charCodeAt(0))
  const hash = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes))
  return Array.from(hash, (b) => b.toString(16).padStart(2, "0")).join("")
}

const saved = (status: number, version: number) =>
  new Response(null, { status, headers: { ETag: `"${version}"`, "Cache-Control": "no-store" } })

const error = (status: number, message: string) =>
  Response.json({ error: message }, { status, headers: { "Cache-Control": "no-store" } })
