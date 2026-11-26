import { expect, type Page } from "@playwright/test"

/** Alta completa con el reloj fijado en un lunes: fuerza en gimnasio, corre, entrena a menudo, nacido en 1990. */
export async function alta(page: Page) {
  await page.clock.install({ time: new Date("2026-11-16T10:00:00") })  // un lunes fijo: el plan no depende de cuándo corra la prueba
  await page.goto("/")
  await page.getByRole("button", { name: "Empezar" }).click()
  await page.getByRole("radio", { name: /Fuerza y músculo/ }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await page.getByRole("button", { name: "Correr" }).click()
  await page.getByRole("button", { name: "Siguiente" }).click()
  await expect(page.getByRole("spinbutton", { name: "Minutos el lunes" })).toBeVisible()  // − y +, sin deslizador
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
