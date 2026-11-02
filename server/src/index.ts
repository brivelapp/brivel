// API de Brivel. /api/boveda guarda la copia cifrada de la sincronización (docs/SINCRONIZACION.md): el servidor no
// puede leerla. Sin CORS (la web es del mismo origen) y sin registrar cabeceras ni cuerpos: el token iría en ellos.

const MAX_BYTES = 1024 * 1024  // tope por cuenta
const UNUSED_DAYS = 365  // copias sin subir ni bajar en 12 meses: se borran (política de privacidad, apartado 4)

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url)
    const ip = request.headers.get("CF-Connecting-IP") ?? "local"
    if (!(await env.POR_IP.limit({ key: ip })).success) return error(429, "Demasiadas peticiones: espera un minuto")

    if (pathname === "/api/estado") {
      const row = await env.DB.prepare("select 1 as ok").first<{ ok: number }>()
      return Response.json({ ok: row?.ok === 1 })
    }
    if (pathname !== "/api/boveda") return error(404, "No encontrado")

    const id = await account(request)
    if (!id) return error(401, "Falta el token de sincronización o no es válido")
    switch (request.method) {
      case "GET": {
        const row = await env.DB.prepare("select version, datos from bovedas where id = ?").bind(id)
          .first<{ version: number; datos: ArrayBuffer | number[] }>()
        if (!row) return error(404, "No hay copia: el código cambió o se borró")
        // Bajar también cuenta como uso; como mucho una escritura al día por copia
        const today = new Date().toISOString().slice(0, 10)
        await env.DB.prepare("update bovedas set usado = ? where id = ? and usado < ?").bind(new Date().toISOString(), id, today).run()
        return new Response(new Uint8Array(row.datos), {
          headers: { "Content-Type": "application/octet-stream", ETag: `"${row.version}"`, "Cache-Control": "no-store" },
        })
      }
      case "PUT":
        return put(request, env, id, ip)
      case "DELETE":
        await env.DB.prepare("delete from bovedas where id = ?").bind(id).run()
        return new Response(null, { status: 204 })
      default:
        return error(405, "Método no permitido")
    }
  },

  // Cada día (wrangler.jsonc, triggers): borra las copias que ningún dispositivo ha usado en 12 meses
  async scheduled(_controller, env) {
    const cutoff = new Date(Date.now() - UNUSED_DAYS * 24 * 60 * 60 * 1000).toISOString()
    await env.DB.prepare("delete from bovedas where usado < ?").bind(cutoff).run()
  },
} satisfies ExportedHandler<Env>

/** If-None-Match: * crea la cuenta (versión 1); If-Match: "n" guarda la n + 1 solo si nadie subió antes. */
async function put(request: Request, env: Env, id: string, ip: string): Promise<Response> {
  // Content-Length obligatorio: así no se lee entero un cuerpo enorme antes de rechazarlo
  const declared = Number(request.headers.get("Content-Length"))
  if (!request.headers.has("Content-Length") || !Number.isInteger(declared)) return error(411, "Falta Content-Length")
  if (declared > MAX_BYTES) return error(413, "La copia pasa de 1 MB")
  const body = await request.arrayBuffer()
  if (body.byteLength === 0 || body.byteLength > MAX_BYTES) return error(413, "La copia está vacía o pasa de 1 MB")

  const now = new Date().toISOString()
  if (request.headers.get("If-None-Match") === "*") {
    if (!(await env.ALTAS.limit({ key: ip })).success) return error(429, "Demasiadas altas: espera un minuto")
    const created = await env.DB.prepare(
      "insert into bovedas (id, version, datos, usado) values (?, 1, ?, ?) on conflict (id) do nothing",
    ).bind(id, body, now).run()
    return created.meta.changes === 1 ? saved(201, 1) : error(409, "Ya hay una copia: descárgala y combina")
  }
  const match = /^"(\d+)"$/.exec(request.headers.get("If-Match") ?? "")
  if (!match) return error(428, "Falta If-Match o If-None-Match")
  if (!(await env.SUBIDAS.limit({ key: id })).success) return error(429, "Demasiadas subidas: espera un minuto")
  const version = Number(match[1])
  // Atómico: si otro dispositivo subió antes, la versión ya no coincide y no cambia ninguna fila
  const updated = await env.DB.prepare(
    "update bovedas set version = ?, datos = ?, usado = ? where id = ? and version = ?",
  ).bind(version + 1, body, now, id, version).run()
  return updated.meta.changes === 1 ? saved(200, version + 1) : error(409, "Otro dispositivo subió antes: descarga y combina")
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
