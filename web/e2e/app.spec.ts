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
  await page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: "Perfil" }).click()
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

test("el alta lleva a Hoy con el objetivo, el plan de la semana y las cuatro secciones", async ({ page }) => {
  await signUp(page)
  await expect(page.getByText(/Ganar fuerza y músculo · bloque 1, semana 1 de 4/)).toBeVisible()
  await expect(page.getByText("Esta semana")).toBeVisible()
  await expect(page.getByText(/Fuerza de la semana por zona: /)).toBeVisible()
  for (const section of ["Hoy", "Calendario", "Progreso", "Perfil"]) {
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
})
