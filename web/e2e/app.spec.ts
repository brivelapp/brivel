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
  await page.getByRole("radio", { name: /Fuerza y músculo/ }).click()
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
  await expect(page.getByText(/Fuerza y músculo · bloque 1, semana 1 de 4/)).toBeVisible()
  await expect(page.getByText("Esta semana")).toBeVisible()
  await expect(page.getByText(/Fuerza de la semana por zona: /)).toBeVisible()
  for (const section of ["Hoy", "Calendario", "Entrenar", "Progreso", "Perfil"]) {
    await page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: section }).click()
    await expect(page.getByRole("main").getByRole("heading").first()).toBeVisible()  // cada sección pinta algo
  }
})

test("sin plan: «Ya tengo mi rutina» pide solo el año y el aviso, y Hoy invita a crear la rutina", async ({ page }) => {
  page.on("pageerror", (e) => errors.push(String(e)))
  await page.clock.install({ time: new Date("2026-11-16T10:00:00") })
  await page.goto("/")
  await page.getByRole("button", { name: "Empezar" }).click()
  await page.getByRole("radio", { name: /Ya tengo mi rutina/ }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await expect(page.getByRole("img", { name: "Paso 2 de 3" })).toBeVisible()  // sin deportes, semana, fuerza ni nivel
  await page.getByRole("textbox", { name: "Año de nacimiento" }).fill("1990")
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("checkbox", { name: /Lo he leído/ }).check()
  await page.getByRole("button", { name: "Empezar" }).click()
  await expect(page.getByRole("heading", { name: "Empieza por tu rutina" })).toBeVisible()
  await expect(page.getByRole("button", { name: "Quiero un plan de Brivel" })).toBeVisible()
  await expect(page.getByText("0 de 2 días de fuerza")).toBeVisible()
  await page.getByRole("button", { name: "Crear mi rutina" }).click()
  await expect(page.getByRole("heading", { name: "Entrenar" })).toBeVisible()
  await page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: "Perfil" }).click()
  await expect(page.getByRole("heading", { name: "Tus deportes" })).toHaveCount(0)
  await expect(page.getByRole("heading", { name: "Año de nacimiento" })).toBeVisible()
  // Pedir un plan después pregunta todo lo que faltaba
  await page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: "Hoy" }).click()
  await page.getByRole("button", { name: "Quiero un plan de Brivel" }).click()
  await page.getByRole("radio", { name: /Cardio y salud/ }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await expect(page.getByRole("heading", { name: "¿Qué deportes haces?" })).toBeVisible()
})

test("los datos siguen ahí al recargar la página", async ({ page }) => {
  await signUp(page)
  await page.reload()
  await expect(page.getByText(/Fuerza y músculo/)).toBeVisible()
  await expect(page.getByRole("navigation", { name: "Secciones" })).toBeVisible()  // sigue dentro, no en la portada
  await expect(page.getByRole("heading", { name: "El sistema operativo de tu cuerpo" })).toHaveCount(0)
})

test("en Perfil se escriben 45 minutos los siete días, y las conexiones están en Ajustes, tras la rueda", async ({ page }) => {
  await signUp(page)
  await page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: "Perfil" }).click()
  for (const day of ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"]) {
    await page.getByRole("spinbutton", { name: `Minutos el ${day}` }).fill("45")
  }
  await expect(page.getByText("7 días")).toBeVisible()
  await expect(page.getByText("5 h 15 min")).toBeVisible()
  await page.reload()
  await page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: "Perfil" }).click()
  await expect(page.getByRole("spinbutton", { name: "Minutos el domingo" })).toHaveValue("45")
  await expect(page.getByRole("slider", { name: "Domingo" })).toHaveValue("45")
  await expect(page.getByRole("heading", { name: "Conexiones" })).toHaveCount(0)
  await page.getByRole("button", { name: "Ajustes" }).click()
  await expect(page.getByRole("heading", { name: "Conexiones" })).toBeVisible()
  await expect(page.getByRole("button", { name: /intervals\.icu.*Sin conectar/ })).toBeVisible()
  await page.getByRole("button", { name: /intervals\.icu/ }).click()
  await expect(page.getByRole("checkbox", { name: "Renombrar mis actividades" })).toBeChecked()
  await page.getByRole("button", { name: "Volver a Ajustes" }).click()
  await page.getByRole("button", { name: "Volver a Perfil" }).click()
  await expect(page.getByRole("heading", { name: "Tu semana" })).toBeVisible()
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

test("sesión propia: se monta por pasos, se guarda, se pone hoy y sale en Hoy", async ({ page }) => {
  await signUp(page)
  const nav = page.getByRole("navigation", { name: "Secciones" })
  await nav.getByRole("button", { name: "Entrenar" }).click()
  const card = page.locator("section.module", { hasText: "Tus sesiones" })
  await card.getByRole("button", { name: "Nueva sesión" }).click()
  const editor = page.getByRole("dialog", { name: "Nueva sesión" })
  await editor.getByRole("button", { name: "Guardar" }).click()  // sin nombre no se guarda, y dice por qué
  await expect(editor.getByRole("alert")).toHaveText("Ponle un nombre a la sesión (60 letras como mucho)")
  await editor.getByLabel("Nombre", { exact: true }).fill("Series de 3 minutos")
  await editor.getByLabel("Minutos").fill("15")
  await editor.getByRole("button", { name: "Añadir paso" }).click()
  const step = editor.locator(".routine-exercise").nth(1)
  await step.getByLabel("Intensidad").selectOption("Z4")
  await step.getByLabel("Minutos").fill("3")
  await step.getByLabel("Veces").fill("5")
  await step.getByLabel("Descanso (min)").fill("2")
  await expect(editor.getByText(/^Total: 40 min/)).toBeVisible()  // 15 + 5 × (3 + 2)
  await editor.getByRole("button", { name: "Guardar" }).click()
  await expect(page.getByRole("status")).toHaveText("Sesión guardada")
  await expect(card.getByRole("button", { name: /Series de 3 minutos · Carrera · 40 min · sin días fijos/ })).toBeVisible()

  await card.getByRole("button", { name: "Hacer Series de 3 minutos hoy" }).click()
  await nav.getByRole("button", { name: "Hoy" }).click()
  await expect(page.getByRole("heading", { name: "Series de 3 minutos" })).toBeVisible()
  await expect(page.getByText("Tu sesión")).toBeVisible()
  await page.reload()
  await expect(page.getByRole("heading", { name: "Series de 3 minutos" })).toBeVisible()  // sigue ahí al recargar
})

test("el Calendario enseña el plan entero, semana a semana", async ({ page }) => {
  await signUp(page)
  await page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: "Calendario" }).click()
  const plan = page.locator("section.module", { hasText: "El plan entero" })
  await expect(plan.getByText(/^Bloque 1: lo que queda de sus cuatro semanas/)).toBeVisible()
  await plan.getByRole("button", { name: "Ver semana a semana" }).click()
  await expect(plan.getByText("Esta semana")).toBeVisible()
  await expect(plan.getByText("Semana 1 de 4", { exact: false }).first()).toBeVisible()
  await expect(plan.getByText(/Semana 4 de 4 \(más suave\)/)).toBeVisible()
  await expect(plan.getByText(/^Semana del \d+ de (noviembre|diciembre)/)).toHaveCount(3)  // las tres que quedan del bloque
})

test("actividad propia: se importa un FIT, se apunta al material, sale su detalle y da medallas", async ({ page }) => {
  await signUp(page)
  const nav = page.getByRole("navigation", { name: "Secciones" })
  // Antes, una bici en Perfil: la actividad se le apuntará a ella
  await nav.getByRole("button", { name: "Perfil" }).click()
  const gear = page.locator("section.module", { hasText: "Zapatillas y bicis" })
  await gear.getByRole("button", { name: "Añadir zapatillas o bici" }).click()
  await gear.getByLabel("Nombre").fill("Bici de carretera")
  await gear.getByLabel("Qué es").selectOption("cycling")
  await gear.getByLabel("Cuenta las actividades desde").fill("2026-10-01")
  await gear.getByLabel("Kilómetros que ya llevaba").fill("1000")
  await gear.getByRole("button", { name: "Guardar" }).click()
  await expect(page.getByRole("status")).toHaveText("Material guardado")
  await expect(gear.getByText("1000 km")).toBeVisible()

  // El archivo es sintético (e2e/fixtures): 10 min de bici a 210 W y 140 ppm, 4,8 km, escrito con otra librería
  await nav.getByRole("button", { name: "Entrenar" }).click()
  const activities = page.locator("section.module", { hasText: "Tus actividades" })
  await activities.getByLabel("Archivo de actividad").setInputFiles("e2e/fixtures/salida.fit")
  await expect(page.getByRole("status")).toHaveText("Actividad importada")
  const detail = page.getByRole("dialog", { name: "Bici" })
  await expect(detail.getByText("4,80 km")).toBeVisible()
  await expect(detail.getByText("210 W").first()).toBeVisible()
  await expect(detail.getByText("140 ppm").first()).toBeVisible()
  await expect(detail.getByRole("img", { name: "Recorrido" })).toBeVisible()
  await expect(detail.getByRole("img", { name: /^Potencia/ })).toBeVisible()
  await expect(detail.getByText("Material: Bici de carretera")).toBeVisible()
  await detail.getByRole("button", { name: "Cerrar" }).click()
  await expect(activities.getByRole("button", { name: /Bici.*4,8 km/ })).toBeVisible()

  // Un archivo que no es FIT se rechaza con su motivo
  await activities.getByLabel("Archivo de actividad").setInputFiles({ name: "nota.fit", mimeType: "application/octet-stream", buffer: Buffer.from("esto no es un FIT, es texto") })
  await expect(page.getByRole("status")).toHaveText("No es un archivo de actividad: Brivel lee FIT, GPX y TCX")

  // Progreso: sin reloj, la actividad propia ya da datos, su curva de potencia y la primera medalla
  await nav.getByRole("button", { name: "Progreso" }).click()
  const curve = page.locator("section.module", { hasText: "Curva de potencia (bici)" })
  await expect(curve.getByRole("row", { name: /5 min.*210 W.*210 W/ })).toBeVisible()
  const badges = page.locator("section.module", { hasText: "Medallas" })
  await expect(badges.getByText("Primer paso")).toBeVisible()
  await expect(badges.getByText(/Nivel 1 · 1 punto/)).toBeVisible()
  await page.locator("section.module", { hasText: "Tus últimos entrenamientos" }).getByRole("button", { name: /Bici/ }).first().click()
  await page.getByRole("button", { name: "Ver la actividad" }).click()
  await expect(page.getByRole("dialog", { name: "Bici" })).toBeVisible()
  await page.getByRole("dialog", { name: "Bici" }).getByRole("button", { name: "Cerrar" }).click()

  // Perfil: los 4,8 km se sumaron a la bici, y siguen tras recargar
  await page.reload()
  await nav.getByRole("button", { name: "Perfil" }).click()
  await expect(page.locator("section.module", { hasText: "Zapatillas y bicis" }).getByText("1005 km")).toBeVisible()
  expect(errors).toEqual([])
})

test("con una carrera de objetivo se añade una preparatoria, como en las apps", async ({ page }) => {
  await page.clock.install({ time: new Date("2026-11-16T10:00:00") })
  await page.goto("/")
  await page.getByRole("button", { name: "Empezar" }).click()
  await page.getByRole("radio", { name: /Preparar una carrera/ }).click()
  await page.getByLabel("Nombre de la carrera").fill("Maratón de Valencia")
  await page.getByLabel("Fecha").fill("2027-03-14")
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("radio", { name: /Gimnasio/ }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("radio", { name: /A menudo/ }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("textbox", { name: "Año de nacimiento" }).fill("1990")
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("checkbox", { name: /Lo he leído/ }).check()
  await page.getByRole("button", { name: "Ver mi plan" }).click()
  await page.getByText("Otras carreras antes de Maratón de Valencia").click()
  await page.getByRole("button", { name: "Añadir otra carrera" }).click()
  await page.getByLabel("Nombre de la carrera").fill("Media de Santa Pola")
  await page.getByLabel("Fecha").fill("2027-01-24")
  await page.getByRole("radio", { name: "Importante" }).click()
  await page.getByRole("button", { name: "Añadir", exact: true }).click()
  await expect(page.getByText("Media de Santa Pola")).toBeVisible()
  await expect(page.getByText("Domingo 24 de enero · importante")).toBeVisible()
})
