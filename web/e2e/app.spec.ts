import { expect, test, type Page } from "@playwright/test"

// El recorrido de un usuario nuevo en la web, de la portada a la biblioteca, con el reloj del navegador fijado en un lunes.

const errors: string[] = []

/** Alta completa: fuerza en gimnasio, corre, entrena a menudo, nacido en 1990. */
async function signUp(page: Page) {
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()) })
  page.on("pageerror", (e) => errors.push(String(e)))
  await page.clock.install({ time: new Date("2026-11-16T10:00:00") })  // un lunes fijo: el plan no depende de cuándo corra la prueba
  await page.goto("/")
  await page.getByRole("button", { name: "Empezar" }).click()
  await page.getByRole("radio", { name: /Ganar fuerza/ }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("button", { name: "Correr" }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await expect(page.getByRole("slider", { name: "Lunes" })).toBeVisible()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("radio", { name: /Gimnasio/ }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("radio", { name: /A menudo/ }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("textbox", { name: "Año de nacimiento" }).fill("1990")
  await page.getByRole("button", { name: "Siguiente" }).click()
  await expect(page.getByRole("button", { name: "Ver mi plan" })).toBeDisabled()  // sin aceptar el aviso no se sigue
  await page.getByRole("checkbox", { name: /Lo he leído/ }).check()
  await page.getByRole("button", { name: "Ver mi plan" }).click()
  await expect(page.getByRole("navigation", { name: "Secciones" })).toBeVisible()
}

async function openLibrary(page: Page) {
  await page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: "Entrenar" }).click()
  await page.getByRole("button", { name: "Ver los ejercicios" }).click()
  return page.locator("section.module", { hasText: "Biblioteca de ejercicios" })
}

const count = async (library: ReturnType<Page["locator"]>) =>
  Number((await library.getByText(/^\d+ ejercicios$/).innerText()).split(" ")[0])

test.beforeEach(() => { errors.length = 0 })
test.afterEach(() => { expect(errors, "errores en la consola del navegador").toEqual([]) })

test("la portada explica qué es y enlaza lo legal", async ({ page }) => {
  await page.goto("/")
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("El sistema operativo de tu cuerpo")
  await expect(page.getByRole("link", { name: "Privacidad" })).toBeVisible()
  await expect(page.getByRole("link", { name: "Términos" })).toBeVisible()
  await expect(page.getByRole("link", { name: "Código fuente" })).toBeVisible()
})

test("el alta lleva a Hoy con el objetivo, el plan de la semana y las cinco secciones", async ({ page }) => {
  await signUp(page)
  await expect(page.getByText(/Ganar fuerza y músculo · bloque 1, semana 1 de 4/)).toBeVisible()
  await expect(page.getByText("Esta semana")).toBeVisible()
  await expect(page.getByText(/Fuerza de la semana por zona: /)).toBeVisible()
  for (const section of ["Hoy", "Calendario", "Entrenar", "Progreso", "Perfil"]) {
    await page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: section }).click()
    await expect(page.getByRole("main").getByRole("heading").first()).toBeVisible()  // cada sección pinta algo
  }
})

test("los datos siguen ahí al recargar la página", async ({ page }) => {
  await signUp(page)
  await page.reload()
  await expect(page.getByText(/Ganar fuerza y músculo/)).toBeVisible()
  await expect(page.getByRole("navigation", { name: "Secciones" })).toBeVisible()  // sigue dentro, no en la portada
  await expect(page.getByRole("heading", { name: "El sistema operativo de tu cuerpo" })).toHaveCount(0)
})

test("la biblioteca filtra por zona y búsqueda, y enseña la ficha con músculos y descripción", async ({ page }) => {
  await signUp(page)
  const library = await openLibrary(page)
  const all = await count(library)
  expect(all).toBeGreaterThan(1300)
  await expect(library.locator("button.option")).toHaveCount(60)  // por tramos, no los 1.310 de golpe
  await library.getByRole("button", { name: "Ver más" }).click()
  await expect(library.locator("button.option")).toHaveCount(120)

  await library.getByLabel("Zona").selectOption("espalda")
  const back = await count(library)
  expect(back).toBeGreaterThan(50)
  expect(back).toBeLessThan(all)
  await expect(library.locator("button.option")).toHaveCount(60)  // el filtro vuelve al primer tramo

  await library.getByLabel("Zona").selectOption("")
  await library.getByLabel("Buscar").fill("curl femoral tumbado")
  await expect(library.locator("button.option").first()).toContainText("Curl femoral tumbado")
  await library.locator("button.option").first().click()
  const sheet = page.getByRole("dialog")
  await expect(sheet.getByRole("heading", { name: "Curl femoral tumbado" })).toBeVisible()
  await expect(sheet.getByText("Zona: pierna · Músculos: isquiotibiales")).toBeVisible()
  await expect(sheet.getByText(/Trabaja sobre todo isquiotibiales/)).toBeVisible()
  await sheet.getByRole("button", { name: "Cerrar" }).click()

  await library.getByLabel("Buscar").fill("zzzz")
  await expect(library.getByText("0 ejercicios")).toBeVisible()
})

test("el material del usuario se guarda y «solo con mi material» reduce la lista", async ({ page }) => {
  await signUp(page)
  const library = await openLibrary(page)
  const all = await count(library)
  await expect(library.getByLabel("Solo con mi material")).toHaveCount(0)  // sin material indicado no hay nada que filtrar
  await library.getByRole("button", { name: "mancuernas", exact: true }).click()
  await expect(library.getByRole("button", { name: "mancuernas", exact: true })).toHaveAttribute("aria-pressed", "true")
  await library.getByLabel("Solo con mi material").check()
  const mine = await count(library)
  expect(mine).toBeGreaterThan(100)
  expect(mine).toBeLessThan(all)

  await page.reload()
  const again = await openLibrary(page)
  await expect(again.getByRole("button", { name: "mancuernas", exact: true })).toHaveAttribute("aria-pressed", "true")
})

test("la sesión de fuerza de hoy dice cómo calentar y se puede empezar", async ({ page }) => {
  await signUp(page)
  await expect(page.getByText("Lunes 16 de noviembre")).toBeVisible()
  await expect(page.getByText(/^Calentamiento \(10 min\): /)).toBeVisible()
  await page.getByRole("button", { name: "Empezar" }).click()
  await expect(page.getByText(/^0\/\d+ series$/)).toBeVisible()
  await expect(page.getByRole("button", { name: "Serie hecha" })).toBeVisible()
  await expect(page.getByRole("button", { name: "Cómo se hace" })).toBeVisible()
  await page.getByRole("radiogroup", { name: "Vista" }).getByRole("radio", { name: "Sesión" }).click()
  await expect(page.getByText(/^Calentamiento \(10 min\): /)).toBeVisible()  // también dentro de la sesión
})

/** Añade un ejercicio al entreno libre abierto, buscándolo por su nombre exacto. */
async function addExercise(page: Page, name: string) {
  await page.getByRole("button", { name: "Añadir ejercicio" }).click()
  const picker = page.getByRole("dialog", { name: "Añadir ejercicio" })
  await picker.getByLabel("Buscar").fill(name)
  await picker.getByRole("button", { name: new RegExp(`^${name} ·`) }).click()
}

test("entreno libre: se monta sobre la marcha, con calentamiento, discos y superserie, y se puede descartar", async ({ page }) => {
  await signUp(page)
  const nav = page.getByRole("navigation", { name: "Secciones" })
  await nav.getByRole("button", { name: "Entrenar" }).click()
  await page.getByRole("button", { name: "Empezar entreno vacío" }).click()
  await expect(page.getByRole("heading", { level: 1, name: "Entreno libre" })).toBeVisible()
  await expect(page.getByText(/Tu entreno está vacío/)).toBeVisible()

  await addExercise(page, "Sentadilla con barra")
  await expect(page.getByRole("heading", { name: "Sentadilla con barra" })).toBeVisible()
  await expect(page.getByText("Serie 1 de 3 · 8–12 repeticiones")).toBeVisible()
  await page.getByLabel("kg", { exact: true }).fill("60")
  await expect(page.getByText("Por lado: 20 kg (barra de 20 kg)")).toBeVisible()  // (60 − 20) / 2

  // Una serie de calentamiento se suma a las tres de trabajo
  await page.getByLabel("Tipo de serie").selectOption("warmup")
  await page.getByRole("button", { name: "Serie hecha" }).click()
  await expect(page.getByText("1/4 series")).toBeVisible()
  await page.getByRole("button", { name: "Saltar" }).click()
  await expect(page.getByText("Serie 2 de 4 · 8–12 repeticiones")).toBeVisible()

  // Superserie con un segundo ejercicio: toca el que lleva menos series (el remo) y se descansa al acabar la ronda
  await addExercise(page, "Remo con mancuerna")
  await page.getByRole("radiogroup", { name: "Vista" }).getByRole("radio", { name: "Sesión" }).click()
  await page.getByLabel("En superserie con el anterior").check()
  await expect(page.getByText("Superserie A", { exact: true })).toHaveCount(2)
  await page.getByRole("radiogroup", { name: "Vista" }).getByRole("radio", { name: "Serie" }).click()
  await expect(page.getByRole("heading", { name: "Remo con mancuerna" })).toBeVisible()
  await page.getByRole("button", { name: "Serie hecha" }).click()  // el remo cierra la ronda: descanso
  await page.getByRole("button", { name: "Saltar" }).click()
  await expect(page.getByRole("heading", { name: "Sentadilla con barra" })).toBeVisible()
  await page.getByRole("button", { name: "Serie hecha" }).click()  // de la sentadilla al remo, sin descanso
  await expect(page.getByRole("heading", { name: "Remo con mancuerna" })).toBeVisible()
  await expect(page.getByRole("button", { name: "Saltar" })).toHaveCount(0)

  // Hoy enseña el entreno libre en lugar de la fuerza del plan, y al descartarlo vuelve el plan
  await page.getByRole("button", { name: "Cerrar la sesión" }).click()
  await nav.getByRole("button", { name: "Hoy" }).click()
  await expect(page.getByRole("heading", { name: "Entreno libre" })).toBeVisible()
  await nav.getByRole("button", { name: "Entrenar" }).click()
  await expect(page.getByText("2 ejercicios")).toBeVisible()
  page.once("dialog", (d) => d.accept())
  await page.getByRole("button", { name: "Descartar el entreno" }).click()
  await expect(page.getByRole("button", { name: "Empezar entreno vacío" })).toBeVisible()
  await nav.getByRole("button", { name: "Hoy" }).click()
  await expect(page.getByRole("heading", { name: "Entreno libre" })).toHaveCount(0)
  await expect(page.getByText(/^Calentamiento \(10 min\): /)).toBeVisible()
})

test("la ficha de un ejercicio enseña su historial y sus marcas tras apuntar una serie", async ({ page }) => {
  await signUp(page)
  await page.getByRole("button", { name: "Empezar" }).click()
  await page.getByLabel("kg", { exact: true }).fill("40")
  await page.getByRole("button", { name: "Serie hecha" }).click()
  await page.getByRole("button", { name: "Saltar" }).click()
  // De vuelta a la serie apuntada (la primera del primer ejercicio), por la tabla de la sesión
  await page.getByRole("radiogroup", { name: "Vista" }).getByRole("radio", { name: "Sesión" }).click()
  await page.getByRole("button", { name: "Abrir la serie 1" }).first().click()
  await page.getByRole("button", { name: "Cómo se hace" }).click()
  const sheet = page.getByRole("dialog", { name: "Ficha del ejercicio" })
  await expect(sheet.getByRole("heading", { name: "Tu historial" })).toBeVisible()
  await expect(sheet.getByText("Más peso")).toBeVisible()
  await expect(sheet.getByText(/^40 kg × \d+$/).first()).toBeVisible()
})

test("Progreso guarda las medidas corporales y rechaza las imposibles", async ({ page }) => {
  await signUp(page)
  await page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: "Progreso" }).click()
  const measures = page.locator("section.module", { hasText: "Tus medidas" })
  await measures.getByLabel("Medida").selectOption("waist")
  await measures.getByLabel("Valor (cm)").fill("82,5")
  await measures.getByRole("button", { name: "Apuntar" }).click()
  await expect(page.getByRole("status")).toHaveText("Medida apuntada")
  await expect(measures.getByRole("button", { name: /Cintura.*82,5 cm/ })).toBeVisible()
  await measures.getByLabel("Valor (cm)").fill("5")
  await measures.getByRole("button", { name: "Apuntar" }).click()
  await expect(page.getByRole("status")).toHaveText("Cintura: entre 30 y 250 cm")
  await page.reload()
  await page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: "Progreso" }).click()
  await expect(page.locator("section.module", { hasText: "Tus medidas" }).getByRole("button", { name: /Cintura.*82,5 cm/ })).toBeVisible()
})
