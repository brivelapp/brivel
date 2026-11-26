#!/usr/bin/env node
// Reglas de Brivel que se comprueban solas (CI y antes de cada commit): cada corrección que Gabriel ha tenido que repetir
// se convierte aquí en un fallo con su porqué y cómo arreglarlo, en vez de en otra línea de la memoria. Sin dependencias.
//   node scripts/reglas.mjs            revisa el repo (sale con 1 si algo incumple)
//   node scripts/reglas.mjs --probar   comprueba que cada regla caza su ejemplo malo y deja pasar el bueno
// Regla nueva: añade un objeto a REGLAS con `mal` y `bien`, y su origen (fecha y qué se corrigió).
import { execFileSync } from "node:child_process"
import { readFileSync } from "node:fs"

/** Una cadena de texto en el código (entre comillas o entre etiquetas JSX), no un comentario. */
const enTexto = (frase) => new RegExp(`["'\`>][^"'\`<\\n]*${frase}`, "i")
const INTERFAZ = /^(web\/src|androidApp\/src\/main|iosApp\/iosApp|shared\/src\/commonMain)\/.*\.(tsx?|kt|swift)$/

const REGLAS = [
  {
    id: "color-suelto-web",
    origen: "Diseño Pulso: colores solo de los tokens, sin morado, con contraste revisado (2026-11-19)",
    archivos: /^web\/src\/.*\.tsx?$/,
    patron: /#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3}\b(?![\w-])/,
    mensaje: "Color escrito a mano. Usa una variable de web/src/styles.css (:root); si no existe, créala allí con su versión oscura.",
    mal: `style={{ color: "#FFFFFF" }}`, bien: `style={{ color: "var(--on-ok)" }}`,
  },
  {
    id: "color-suelto-css",
    origen: "Diseño Pulso (2026-11-19)",
    archivos: /^web\/src\/.*\.css$/,
    patron: /^(?!\s*--)(?=.*#[0-9a-fA-F]{3,8}\b)/,
    mensaje: "Color escrito a mano fuera de los tokens. Defínelo como variable en :root (y en el tema oscuro) y usa var(--…).",
    mal: `.chip { background: #ECE7DC; }`, bien: `  --chip: #ECE7DC;`,
  },
  {
    id: "color-suelto-android",
    origen: "Diseño Pulso (2026-11-19)",
    archivos: /^androidApp\/src\/main\/.*\.kt$/, excepto: /\/Theme\.kt$/,
    patron: /Color\(0x/,
    mensaje: "Color escrito a mano. Los colores viven en Theme.kt; usa el de MaterialTheme o añade el token allí.",
    mal: `color = Color(0xFFFFFFFF)`, bien: `color = Pulse.surface`,
  },
  {
    id: "color-suelto-ios",
    origen: "Diseño Pulso (2026-11-19)",
    archivos: /^iosApp\/iosApp\/.*\.swift$/, excepto: /\/Components\.swift$/,
    patron: /0x[0-9a-fA-F]{6}\b/,
    mensaje: "Color escrito a mano. Los colores viven en la paleta de Components.swift; añade el token allí.",
    mal: `Color(light: 0xFFFFFF, dark: 0x000000)`, bien: `Palette.surface`,
  },
  {
    id: "letra-en-px",
    origen: "Letra grande: la web en rem para crecer con la letra del navegador (ciclo-13, 2026-11-23)",
    archivos: /^web\/src\/.*\.css$/,
    patron: /font(-size)?:[^;]*\b\d+(\.\d+)?px/,
    mensaje: "Tamaño de letra en px: no crece con la letra del navegador. Ponlo en rem (16 px = 1rem).",
    mal: `.x { font-size: 14px; }`, bien: `.x { font-size: 0.875rem; }`,
  },
  {
    id: "clic-sin-boton",
    origen: "«Algunos botones no se distinguen de labels sin acción» (2026-11-09)",
    archivos: /^web\/src\/.*\.tsx$/,
    // Una fila de tabla con su <button> dentro sí vale: el botón da el teclado y la fila, una zona de toque mayor
    patron: /<(div|span|p|li|strong|em|section|article|header|h[1-6]|img|label|td)\b[^>]*\sonClick=/,
    mensaje: "Clic en algo que no es un botón: no se ve que se pulsa ni llega con el teclado. Usa <button> (className \"text\", \"link\" o RowButton).",
    mal: `<span className="name" onClick={open}>`, bien: `<button className="link" onClick={open}>`,
  },
  {
    id: "texto-oms-entre-parentesis",
    origen: "«Quita el texto de (OMS) del inicio» (2026-11-23)",
    archivos: INTERFAZ,
    patron: enTexto("\\(OMS\\)"), soloTexto: true,
    mensaje: "«(OMS)» en un texto de la app: Gabriel lo quitó. Di la cifra sin la etiqueta (p. ej. «Cardio 25 de 150 min»).",
    mal: `"Cardio (OMS) 25 de 150 min"`, bien: `// minutos equivalentes (OMS)`,
  },
  {
    id: "texto-ritmo-comodo",
    origen: "«No me gusta el texto de ritmo cómodo»: ritmo en min/km, y pulso o vatios en bici (2026-11-18)",
    archivos: INTERFAZ,
    patron: enTexto("ritmo cómodo"), soloTexto: true,
    mensaje: "«Ritmo cómodo» no dice nada: da el ritmo en min/km (carrera) o el pulso/vatios (bici), del test inicial.",
    mal: `"Rodaje a ritmo cómodo"`, bien: `// nunca "recuperación" arriba y ritmo cómodo abajo`,
  },
]

function revisar(regla, ruta, texto) {
  if (!regla.archivos.test(ruta) || regla.excepto?.test(ruta)) return []
  const comentario = (linea) => regla.soloTexto && /^\s*(\/\/|\/?\*)/.test(linea)  // los textos vetados, solo en lo que ve el usuario
  return texto.split("\n").flatMap((linea, i) => regla.patron.test(linea) && !comentario(linea) ? [`${ruta}:${i + 1}: [${regla.id}] ${regla.mensaje}\n    ${linea.trim().slice(0, 140)}\n    Origen: ${regla.origen}`] : [])
}

if (process.argv.includes("--probar")) {
  let mal = 0
  for (const r of REGLAS) {
    const ruta = { "color-suelto-android": "androidApp/src/main/X.kt", "color-suelto-ios": "iosApp/iosApp/X.swift" }[r.id]
      ?? (r.archivos.test("web/src/x.css") ? "web/src/x.css" : r.archivos.test("web/src/X.tsx") ? "web/src/X.tsx" : "shared/src/commonMain/X.kt")
    if (revisar(r, ruta, r.mal).length !== 1) { console.error(`✗ ${r.id} no caza su ejemplo malo: ${r.mal}`); mal++ }
    if (revisar(r, ruta, r.bien).length !== 0) { console.error(`✗ ${r.id} se queja del ejemplo bueno: ${r.bien}`); mal++ }
  }
  console.log(mal ? `${mal} reglas mal hechas` : `${REGLAS.length} reglas probadas`)
  process.exit(mal ? 1 : 0)
}

const archivos = execFileSync("git", ["ls-files"], { encoding: "utf8" }).split("\n").filter(Boolean)
const fallos = archivos.flatMap((ruta) => {
  const reglas = REGLAS.filter((r) => r.archivos.test(ruta) && !r.excepto?.test(ruta))
  return reglas.length ? reglas.flatMap((r) => revisar(r, ruta, readFileSync(ruta, "utf8"))) : []
})
console.log(fallos.length ? fallos.join("\n\n") + `\n\n${fallos.length} incumplimientos de las reglas de Brivel` : `Reglas de Brivel: ${REGLAS.length} reglas, todo en orden`)
process.exit(fallos.length ? 1 : 0)
