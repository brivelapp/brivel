// Tests de /api/cuenta (entrar con Google o Apple) contra `wrangler dev`, con claves de prueba y una D1 local nueva.
// La vuelta con un código real de Google o Apple no se puede probar aquí: el billete se mete a mano en la D1. `npm test`
import { after, before, test } from "node:test"
import assert from "node:assert/strict"
import { execFileSync, spawn } from "node:child_process"
import { createHash, hkdfSync, randomBytes } from "node:crypto"
import { mkdtempSync, rmSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"

const PORT = 8798
const BASE = `http://127.0.0.1:${PORT}`
const state = mkdtempSync(join(tmpdir(), "brivel-cuentas-"))
const b64 = (n) => randomBytes(n).toString("base64")
let dev

before(async () => {
  execFileSync("npx", ["wrangler", "d1", "migrations", "apply", "brivel", "--local", "--persist-to", state], { stdio: "ignore" })
  const vars = { CUENTAS_CLAVE: b64(32), CUENTAS_PIMIENTA: b64(32), GOOGLE_CLIENT_ID: "cliente-de-prueba", GOOGLE_CLIENT_SECRET: "secreto" }
  dev = spawn("npx", ["wrangler", "dev", "--port", String(PORT), "--persist-to", state,
    ...Object.entries(vars).flatMap(([k, v]) => ["--var", `${k}:${v}`])], { stdio: "ignore", detached: true })
  for (let i = 0; i < 60; i++) {
    try {
      if ((await fetch(`${BASE}/api/estado`)).ok) return
    } catch {}
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error("wrangler dev no arrancó")
})

after(() => {
  process.kill(-dev.pid)
  rmSync(state, { recursive: true, force: true })
})

const sql = (command) => JSON.parse(execFileSync("npx", ["wrangler", "d1", "execute", "brivel", "--local", "--persist-to", state,
  "--json", "--command", command], { encoding: "utf8" }))[0].results
// shortcut: wrangler dev a veces cierra la conexión tras escribir en la D1 local desde otro proceso; un reintento
const again = (url, init) => fetch(url, init).catch(() => new Promise((r) => setTimeout(r, 1000)).then(() => fetch(url, init)))

// Como Vault.kt: token = HKDF-SHA256(secreto, sal de ceros, "brivel-sync-token-v1"); la copia es SHA-256(token)
const tokenOf = (secret) => Buffer.from(hkdfSync("sha256", secret, Buffer.alloc(32), "brivel-sync-token-v1", 32))
const vaultOf = (secret) => createHash("sha256").update(tokenOf(secret)).digest("hex")
const newSecret = () => randomBytes(16)
const identity = () => randomBytes(32).toString("hex")

/** El billete que dejaría la vuelta de Google para esa identidad. */
function ticket(id, minutes = 2) {
  const t = randomBytes(32).toString("base64url")
  const hash = createHash("sha256").update(t).digest("hex")
  sql(`insert into billetes (id, identidad, proveedor, caduca) values ('${hash}', '${id}', 'google', '${new Date(Date.now() + minutes * 60_000).toISOString()}')`)
  return t
}

const redeem = (billete, secret) => again(`${BASE}/api/cuenta/canjear`, { method: "POST",
  headers: { "Content-Type": "application/json" }, body: JSON.stringify({ billete, secreto: secret.toString("base64url") }) })

test("sin las claves de Apple, su botón responde 503", async () => {
  assert.equal((await fetch(`${BASE}/api/cuenta/apple`, { redirect: "manual" })).status, 503)
})

test("ir a Google: solo openid, con state y PKCE en una cookie de la propia web", async () => {
  const res = await fetch(`${BASE}/api/cuenta/google?plataforma=app`, { redirect: "manual" })
  assert.equal(res.status, 302)
  const to = new URL(res.headers.get("Location"))
  assert.equal(to.origin + to.pathname, "https://accounts.google.com/o/oauth2/v2/auth")
  assert.equal(to.searchParams.get("scope"), "openid")  // ni correo ni nombre
  assert.equal(to.searchParams.get("code_challenge_method"), "S256")
  assert.equal(to.searchParams.get("redirect_uri"), "https://brivel.app/api/cuenta/google/vuelta")  // wrangler dev usa la ruta de wrangler.jsonc
  const cookie = res.headers.get("Set-Cookie")
  assert.match(cookie, new RegExp(`^__Host-brivel-cuenta=${to.searchParams.get("state")}\\.[\\w-]+\\.app;`))
  assert.match(cookie, /HttpOnly/)
  assert.match(cookie, /SameSite=Lax/)
})

test("una vuelta sin su cookie o con otro state no entra", async () => {
  const none = await fetch(`${BASE}/api/cuenta/google/vuelta?code=x&state=y`, { redirect: "manual" })
  assert.equal(none.status, 302)
  assert.match(none.headers.get("Location"), /^\/#cuenta-error=/)
  const other = await fetch(`${BASE}/api/cuenta/google/vuelta?code=x&state=y`, { redirect: "manual",
    headers: { Cookie: "__Host-brivel-cuenta=z.v.app" } })
  assert.match(other.headers.get("Location"), /^brivel:\/\/cuenta\?cuenta-error=/)  // vuelve a la app
})

test("la primera vez la cuenta se queda con el secreto del dispositivo; después, todos reciben ese", async () => {
  const id = identity()
  const [mine, other] = [newSecret(), newSecret()]
  const first = await redeem(ticket(id), mine)
  assert.equal(first.status, 200)
  assert.deepEqual(await first.json(), { secreto: mine.toString("base64url"), nueva: true })
  const row = sql(`select proveedor, boveda, hex(secreto) as s from identidades where id = '${id}'`)[0]
  assert.equal(row.proveedor, "google")
  assert.equal(row.boveda, vaultOf(mine))
  assert.ok(!row.s.includes(mine.toString("hex").toUpperCase()))  // guardado cifrado
  const second = await redeem(ticket(id), other)
  assert.deepEqual(await second.json(), { secreto: mine.toString("base64url"), nueva: false })
})

test("el billete vale una vez, caduca y exige un secreto de 16 bytes", async () => {
  const id = identity()
  const t = ticket(id)
  assert.equal((await redeem(t, Buffer.alloc(8))).status, 400)
  assert.equal((await redeem(t, newSecret())).status, 200)
  assert.equal((await redeem(t, newSecret())).status, 410)  // ya usado
  assert.equal((await redeem(ticket(id, -1), newSecret())).status, 410)  // caducado
  assert.equal((await redeem("inventado", newSecret())).status, 410)
})

test("borrar la copia o dejar de entrar con la cuenta quita la cuenta", async () => {
  const bearer = (s) => ({ Authorization: `Bearer ${tokenOf(s).toString("base64url")}` })
  let id
  for (const path of ["/api/boveda", "/api/cuenta"]) {
    id = identity()
    const secret = newSecret()
    await redeem(ticket(id), secret)
    assert.equal((await again(`${BASE}${path}`, { method: "DELETE", headers: bearer(secret) })).status, 204)
    assert.equal(sql(`select count(*) as n from identidades where id = '${id}'`)[0].n, 0, path)
  }
  // Al volver a entrar, es una cuenta nueva (la quinta alta de esta IP: el límite es de 5 por minuto)
  assert.equal((await (await redeem(ticket(id), newSecret())).json()).nueva, true)
  assert.equal((await fetch(`${BASE}/api/cuenta`, { method: "DELETE" })).status, 401)
})
