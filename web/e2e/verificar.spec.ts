import { expect, test, type Page } from "@playwright/test"
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs"
import { dirname } from "node:path"
import { fileURLToPath } from "node:url"
import { alta } from "./alta"
import { ANTES, DESPUES, type Pantalla } from "./mapa"

// Verificación de la web: recorre todas las pantallas del mapa (mapa.ts) en cuatro anchos, saca una captura de cada una y
// apunta lo que se sale de ancho, lo que es difícil de pulsar y las filas desiguales. Deja todo en
// web/verificacion/<ancho>/: las capturas e informe.md. Falla si una pantalla no se encuentra, si hay errores en la
// consola, si algo se sale, o si aparece un control pequeño o una fila desigual que no estaba en e2e/pendientes/.
// Se lanza con ../scripts/verificar-web.sh (o dentro de `npm run e2e`).

const ANCHOS = [320, 375, 768, 1280]  // móvil pequeño, móvil, tableta, ordenador
const SALIDA = fileURLToPath(new URL("../verificacion/", import.meta.url))
const TOQUE_MINIMO = 44  // px: Apple pide 44 pt y Material 48 dp; WCAG 2.5.5 (AAA) 44 px

type Hallazgo = { tipo: "desborde-pagina" | "se-sale" | "toque-pequeno" | "filas-desiguales"; detalle: string }

/** Lo que se revisa en cada pantalla, dentro del navegador. */
function revisar(minimo: number): Hallazgo[] {
  const visible = (el: Element) => {
    const r = el.getBoundingClientRect()
    return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== "hidden"
  }
  const nombre = (el: Element) => (el.getAttribute("aria-label") || el.textContent || el.tagName).trim().replace(/\s+/g, " ").slice(0, 50)
  const hallazgos: Hallazgo[] = []
  const doc = document.documentElement
  if (doc.scrollWidth > doc.clientWidth) hallazgos.push({ tipo: "desborde-pagina", detalle: `${doc.scrollWidth} px de contenido en ${doc.clientWidth} px` })
  // Lo que se sale de su caja, quedándose con el más interno (si se sale un hijo, el padre también lo parece)
  const salen = [...document.querySelectorAll("body *")].filter((el) => {
    if (!visible(el) || !el.textContent?.trim() || el instanceof SVGElement) return false
    const s = getComputedStyle(el)
    return !["auto", "scroll"].includes(s.overflowX) && s.display !== "inline" && el.clientWidth > 0 && el.scrollWidth > el.clientWidth + 1
  })
  for (const el of salen) if (!salen.some((otro) => otro !== el && el.contains(otro))) hallazgos.push({ tipo: "se-sale", detalle: nombre(el) })
  // Lo que se pulsa: su caja (o la de su etiqueta) de al menos el mínimo; los enlaces dentro de una frase no cuentan
  for (const el of document.querySelectorAll("button, a[href], input:not([type=hidden]), select, textarea, summary, [role=button]")) {
    if (!visible(el) || (el.parentElement?.tagName === "P" && el.matches("a, button.link"))) continue
    const r = (el.closest("label") ?? el).getBoundingClientRect()
    if (r.width < minimo || r.height < minimo) hallazgos.push({ tipo: "toque-pequeno", detalle: `${nombre(el)} (${Math.round(r.width)}×${Math.round(r.height)})` })
  }
  // Filas de una misma lista (las que abren pantalla y las que se despliegan, cerradas) con la misma altura
  for (const lista of new Set([...document.querySelectorAll(".row-button, details.fold:not([open])")].map((f) => f.parentElement!))) {
    const filas = [...lista.children].filter((f) => f.matches(".row-button, details.fold:not([open])") && visible(f))
    const altos = [...new Set(filas.map((f) => Math.round(f.getBoundingClientRect().height)))]
    if (altos.length > 1) hallazgos.push({ tipo: "filas-desiguales", detalle: `lista que empieza por «${filas[0].querySelector("strong")?.textContent ?? nombre(filas[0])}» (${altos.join(", ")} px)` })
  }
  return hallazgos
}

for (const ancho of ANCHOS) {
  test(`verificar todas las pantallas a ${ancho} px`, async ({ page }) => {
    test.setTimeout(240_000)
    const errores: string[] = []
    page.on("console", (m) => { if (m.type() === "error") errores.push(m.text()) })
    page.on("pageerror", (e) => errores.push(String(e)))
    await page.setViewportSize({ width: ancho, height: 900 })
    const carpeta = `${SALIDA}${ancho}/`
    rmSync(carpeta, { recursive: true, force: true })  // sin capturas de pantallas que ya no existen
    mkdirSync(carpeta, { recursive: true })
    const informe: string[] = [`# Verificación a ${ancho} px`, ""]
    const graves: string[] = []  // desbordes: fallan siempre
    const vistos = new Map<string, string[]>()  // controles pequeños y filas desiguales (sin medidas) → pantallas

    const pasar = async (pantalla: Pantalla, preparar: () => Promise<unknown>) => {
      await test.step(pantalla.id, async () => {
        await preparar()
        await pantalla.llegar(page)
        await page.evaluate(() => document.fonts.ready)
        await page.screenshot({ path: `${carpeta}${pantalla.id}.png`, fullPage: true })
        const hallazgos = await page.evaluate(revisar, TOQUE_MINIMO)
        informe.push(`## ${pantalla.nombre} · \`${pantalla.id}\` · ${pantalla.archivo}`, "")
        informe.push(...(hallazgos.length ? hallazgos.map((h) => `- ${h.tipo}: ${h.detalle}`) : ["- sin hallazgos"]), "")
        for (const h of hallazgos) {
          if (h.tipo === "desborde-pagina" || h.tipo === "se-sale") graves.push(`${pantalla.id}: ${h.tipo}: ${h.detalle}`)
          else {
            const clave = `${h.tipo}: ${h.detalle.replace(/ \([^)]*\)$/, "")}`
            vistos.set(clave, [...(vistos.get(clave) ?? []), pantalla.id])
          }
        }
      })
    }

    for (const pantalla of ANTES) await pasar(pantalla, () => page.goto("/"))
    await alta(page)
    for (const pantalla of DESPUES) await pasar(pantalla, () => page.reload())

    writeFileSync(`${carpeta}informe.md`, informe.join("\n"))
    expect(errores, "errores en la consola del navegador").toEqual([])
    expect(graves, "algo se sale de la pantalla o de su caja: que quepa (que baje de fila, no que se corte)").toEqual([])

    // Trinquete: lo pequeño o desigual que ya había está en e2e/pendientes/<ancho>.txt; lo nuevo falla y lo arreglado
    // obliga a quitarlo de la lista, que así solo puede menguar. PENDIENTES=1 la reescribe con lo que hay ahora.
    const lista = fileURLToPath(new URL(`./pendientes/${ancho}.txt`, import.meta.url))
    const actuales = [...vistos.keys()].sort()
    if (process.env.PENDIENTES) {
      mkdirSync(dirname(lista), { recursive: true })
      writeFileSync(lista, actuales.join("\n") + "\n")
      return
    }
    const pendientes = existsSync(lista) ? readFileSync(lista, "utf8").split("\n").filter(Boolean) : []
    const nuevos = actuales.filter((c) => !pendientes.includes(c)).map((c) => `${c} (en ${vistos.get(c)!.join(", ")})`)
    expect.soft(nuevos, `nuevo a ${ancho} px: los controles miden al menos ${TOQUE_MINIMO}×${TOQUE_MINIMO} px (min-height/min-width o más padding) ` +
      "y las filas de una lista, la misma altura").toEqual([])
    const arreglados = pendientes.filter((c) => !actuales.includes(c))
    expect(arreglados, `ya arreglado: quítalo de e2e/pendientes/${ancho}.txt (o vuelve a lanzar con PENDIENTES=1)`).toEqual([])
  })
}
