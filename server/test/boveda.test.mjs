// Tests de /api/boveda contra `wrangler dev`, con una D1 local nueva en cada ejecución. `npm test`
import { after, before, test } from "node:test"
import assert from "node:assert/strict"
import { execFileSync, spawn } from "node:child_process"
import { createHash, randomBytes } from "node:crypto"
import { mkdtempSync, rmSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"

const PORT = 8797
const URL = `http://127.0.0.1:${PORT}/api/boveda`
const state = mkdtempSync(join(tmpdir(), "brivel-test-"))
let dev

before(async () => {
  execFileSync("npx", ["wrangler", "d1", "migrations", "apply", "brivel", "--local", "--persist-to", state], { stdio: "ignore" })
  dev = spawn("npx", ["wrangler", "dev", "--port", String(PORT), "--persist-to", state, "--test-scheduled"], { stdio: "ignore", detached: true })
  for (let i = 0; i < 60; i++) {
    try {
      if ((await fetch(`http://127.0.0.1:${PORT}/api/estado`)).ok) return
    } catch {}
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error("wrangler dev no arrancó")
})

after(() => {
  process.kill(-dev.pid)  // el grupo entero: npx, wrangler y workerd
  rmSync(state, { recursive: true, force: true })
})

const newToken = () => Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString("base64url")
const call = (token, method = "GET", headers = {}, body) =>
  fetch(URL, { method, body, headers: { ...(token && { Authorization: `Bearer ${token}` }), ...headers } })
const block = (n = 4125) => new Uint8Array(randomBytes(n))

test("sin token o con uno mal formado: 401", async () => {
  assert.equal((await call(null)).status, 401)
  assert.equal((await call("corto")).status, 401)
})

test("crear, leer, actualizar y borrar", async () => {
  const token = newToken()
  assert.equal((await call(token)).status, 404)
  assert.equal((await call(token, "PUT", {}, block())).status, 428)  // sin If-Match ni If-None-Match

  const first = block()
  const created = await call(token, "PUT", { "If-None-Match": "*" }, first)
  assert.equal(created.status, 201)
  assert.equal(created.headers.get("ETag"), '"1"')
  assert.equal((await call(token, "PUT", { "If-None-Match": "*" }, block())).status, 409)  // ya existe

  const got = await call(token)
  assert.equal(got.status, 200)
  assert.equal(got.headers.get("ETag"), '"1"')
  assert.equal(got.headers.get("Access-Control-Allow-Origin"), null)  // sin CORS
  assert.deepEqual(new Uint8Array(await got.arrayBuffer()), first)

  const second = block()
  const updated = await call(token, "PUT", { "If-Match": '"1"' }, second)
  assert.equal(updated.status, 200)
  assert.equal(updated.headers.get("ETag"), '"2"')
  assert.equal((await call(token, "PUT", { "If-Match": '"1"' }, block())).status, 409)  // otro dispositivo subió antes
  assert.deepEqual(new Uint8Array(await (await call(token)).arrayBuffer()), second)

  assert.equal((await call(token, "DELETE")).status, 204)
  assert.equal((await call(token)).status, 404)
  assert.equal((await call(token, "PUT", { "If-Match": '"2"' }, block())).status, 409)  // ya no existe
})

test("cada token ve solo su copia", async () => {
  const [a, b] = [newToken(), newToken()]
  assert.equal((await call(a, "PUT", { "If-None-Match": "*" }, block())).status, 201)
  assert.equal((await call(b)).status, 404)
  assert.equal((await call(b, "DELETE")).status, 204)
  assert.equal((await call(a)).status, 200)
})

test("más de 1 MB: 413", async () => {
  const res = await call(newToken(), "PUT", { "If-None-Match": "*" }, block(1024 * 1024 + 1))
  assert.equal(res.status, 413)
})

test("sin CORS: el navegador no deja a otras webs usar la API", async () => {
  const res = await fetch(URL, { method: "OPTIONS", headers: { Origin: "https://otra.web", "Access-Control-Request-Method": "PUT" } })
  assert.equal(res.headers.get("Access-Control-Allow-Origin"), null)
})

// Lo mismo que hace el servidor: la cuenta es SHA-256 de los bytes del token
const accountOf = (token) => createHash("sha256").update(Buffer.from(token, "base64url")).digest("hex")
const sql = (command) => JSON.parse(execFileSync("npx", ["wrangler", "d1", "execute", "brivel", "--local", "--persist-to", state,
  "--json", "--command", command], { encoding: "utf8" }))[0].results

test("bajar cuenta como uso y a los 12 meses sin uso se borra", async () => {
  const [old, recent] = [newToken(), newToken()]
  for (const t of [old, recent]) assert.equal((await call(t, "PUT", { "If-None-Match": "*" }, block())).status, 201)
  sql(`update bovedas set usado = '2025-01-01T00:00:00.000Z' where id = '${accountOf(old)}'`)
  sql(`update bovedas set usado = '2026-01-01T00:00:00.000Z' where id = '${accountOf(recent)}'`)
  assert.equal((await call(recent)).status, 200)  // la baja: ya cuenta como usada hoy
  assert.ok(sql(`select usado from bovedas where id = '${accountOf(recent)}'`)[0].usado > "2026-01-02")
  assert.equal((await fetch(`http://127.0.0.1:${PORT}/__scheduled?cron=0+3+*+*+*`)).status, 200)
  assert.equal((await call(old)).status, 404)
  assert.equal((await call(recent)).status, 200)
})

// El último: agota las altas de esta IP (5 por minuto; las pruebas de arriba ya gastaron 5)
test("límite de altas por IP: 429", async () => {
  const statuses = []
  for (let i = 0; i < 6; i++) statuses.push((await call(newToken(), "PUT", { "If-None-Match": "*" }, block())).status)
  assert.ok(statuses.includes(429), statuses.join(" "))
})
