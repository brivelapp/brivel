import { expect, test, type Page } from "@playwright/test"
import { mkdirSync, rmSync, writeFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { alta } from "./alta"
import { ANTES, DESPUES, type Pantalla } from "./mapa"

// Verificación de la web: recorre todas las pantallas del mapa (mapa.ts) en cuatro anchos, saca una captura de cada una y
// apunta lo que se sale de ancho y lo que es difícil de pulsar. Deja todo en web/verificacion/<ancho>/: las capturas y
// informe.md. Falla si una pantalla no se encuentra o si hay errores en la consola; lo demás, de momento, solo se apunta.
// Se lanza con ../scripts/verificar-web.sh (o dentro de `npm run e2e`).

const ANCHOS = [320, 375, 768, 1280]  // móvil pequeño, móvil, tableta, ordenador
const SALIDA = fileURLToPath(new URL("../verificacion/", import.meta.url))
const TOQUE_MINIMO = 44  // px: Apple pide 44 pt y Material 48 dp; WCAG 2.5.5 (AAA) 44 px

type Hallazgo = { tipo: "desborde-pagina" | "se-sale" | "toque-pequeno"; detalle: string }

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

    const pasar = async (pantalla: Pantalla, preparar: () => Promise<unknown>) => {
      await test.step(pantalla.id, async () => {
        await preparar()
        await pantalla.llegar(page)
        await page.evaluate(() => document.fonts.ready)
        await page.screenshot({ path: `${carpeta}${pantalla.id}.png`, fullPage: true })
        const hallazgos = await page.evaluate(revisar, TOQUE_MINIMO)
        informe.push(`## ${pantalla.nombre} · \`${pantalla.id}\` · ${pantalla.archivo}`, "")
        informe.push(...(hallazgos.length ? hallazgos.map((h) => `- ${h.tipo}: ${h.detalle}`) : ["- sin hallazgos"]), "")
      })
    }

    for (const pantalla of ANTES) await pasar(pantalla, () => page.goto("/"))
    await alta(page)
    for (const pantalla of DESPUES) await pasar(pantalla, () => page.reload())

    writeFileSync(`${carpeta}informe.md`, informe.join("\n"))
    expect(errores, "errores en la consola del navegador").toEqual([])
  })
}
