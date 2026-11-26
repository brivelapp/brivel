import { expect, type Page } from "@playwright/test"

// Mapa de funciones de la web: cada pantalla con el nombre que se le da al hablar, el archivo que la pinta y cómo
// llegar a ella. Lo usan `verificar.spec.ts` (capturas y revisión de cada pantalla en varios anchos) y quien hable de
// la app: «la pantalla de Medallas» es `progreso-medallas` en `Progress.tsx`.
// Pantalla nueva o que cambia de sitio: se añade o se corrige aquí; si no, la verificación falla al no encontrarla.

export type Pantalla = {
  id: string
  nombre: string
  archivo: string
  /** Llega desde una página recién cargada y comprueba que se ve la pantalla. */
  llegar: (page: Page) => Promise<void>
}

const seccion = (page: Page, nombre: string) =>
  page.getByRole("navigation", { name: "Secciones" }).getByRole("button", { name: nombre, exact: true }).click()
const titulo = (page: Page, texto: string) =>
  expect(page.getByRole("heading", { level: 1, name: texto, exact: true })).toBeVisible()
const fila = (page: Page, texto: string) => page.getByRole("button", { name: new RegExp(`^${texto}`) }).click()

/** Antes del alta: lo que ve quien llega sin datos. */
export const ANTES: Pantalla[] = [
  { id: "portada", nombre: "Portada", archivo: "Landing.tsx", llegar: (p) => titulo(p, "El sistema operativo de tu cuerpo") },
  { id: "iniciar-sesion", nombre: "Iniciar sesión", archivo: "App.tsx",
    llegar: async (p) => { await p.getByRole("button", { name: "Iniciar sesión" }).click(); await titulo(p, "Iniciar sesión") } },
  { id: "bienvenida", nombre: "Bienvenida (primera pregunta)", archivo: "Onboarding.tsx",
    llegar: async (p) => { await p.getByRole("button", { name: "Empezar" }).click(); await expect(p.getByRole("radio").first()).toBeVisible() } },
]

/** Después del alta (ver `alta.ts`). */
export const DESPUES: Pantalla[] = [
  { id: "hoy", nombre: "Hoy", archivo: "Today.tsx, Widgets.tsx", llegar: (p) => titulo(p, "Hoy") },
  { id: "hoy-detalle", nombre: "Detalle de la sesión de hoy", archivo: "Today.tsx",
    llegar: async (p) => {
      // En el ordenador (≥ 900 px) va a la derecha de Hoy; en el móvil, en su pantalla tras «Detalles ›»
      if ((p.viewportSize()?.width ?? 0) >= 900) return expect(p.getByRole("complementary", { name: "Detalle de la sesión" })).toBeVisible()
      await p.getByRole("button", { name: "Detalles ›" }).first().click()
      await expect(p.getByRole("button", { name: "Volver a Hoy" })).toBeVisible()
    } },
  { id: "calendario", nombre: "Calendario", archivo: "Calendar.tsx",
    llegar: async (p) => { await seccion(p, "Calendario"); await expect(p.getByRole("button", { name: "Mes siguiente" })).toBeVisible() } },
  { id: "entrenar", nombre: "Entrenar", archivo: "Train.tsx", llegar: async (p) => { await seccion(p, "Entrenar"); await titulo(p, "Entrenar") } },
  ...([["sesiones", "Sesiones"], ["rutinas", "Rutinas de fuerza"], ["actividades", "Actividades"], ["biblioteca", "Biblioteca de ejercicios"]] as const)
    .map(([id, nombre]): Pantalla => ({ id: `entrenar-${id}`, nombre: `Entrenar → ${nombre}`, archivo: "Train.tsx",
      llegar: async (p) => { await seccion(p, "Entrenar"); await fila(p, nombre); await titulo(p, nombre) } })),
  { id: "progreso", nombre: "Progreso", archivo: "Progress.tsx", llegar: async (p) => { await seccion(p, "Progreso"); await titulo(p, "Progreso") } },
  ...([["medallas", "Medallas"], ["medidas", "Tus medidas"]] as const)
    .map(([id, nombre]): Pantalla => ({ id: `progreso-${id}`, nombre: `Progreso → ${nombre}`, archivo: "Progress.tsx",
      llegar: async (p) => { await seccion(p, "Progreso"); await fila(p, nombre); await titulo(p, nombre) } })),
  { id: "perfil", nombre: "Perfil", archivo: "Profile.tsx", llegar: async (p) => { await seccion(p, "Perfil"); await titulo(p, "Perfil") } },
  ...([["semana", "Tu semana"], ["objetivo", "Objetivo"], ["material", "Zapatillas y bicis"]] as const)
    .map(([id, nombre]): Pantalla => ({ id: `perfil-${id}`, nombre: `Perfil → ${nombre}`, archivo: "Profile.tsx",
      llegar: async (p) => { await seccion(p, "Perfil"); await fila(p, nombre); await titulo(p, nombre) } })),
  { id: "ajustes", nombre: "Ajustes (la rueda de Perfil)", archivo: "Profile.tsx",
    llegar: async (p) => { await seccion(p, "Perfil"); await p.getByRole("button", { name: "Ajustes" }).click(); await titulo(p, "Ajustes") } },
  ...([["garmin", "Garmin Connect™"], ["intervals", "intervals.icu"], ["dispositivos", "Otros dispositivos"], ["aviso", "Aviso de salud"]] as const)
    .map(([id, nombre]): Pantalla => ({ id: `ajustes-${id}`, nombre: `Ajustes → ${nombre}`, archivo: "Profile.tsx, Garmin.tsx, Intervals.tsx, Sync.tsx",
      llegar: async (p) => {
        await seccion(p, "Perfil"); await p.getByRole("button", { name: "Ajustes" }).click()
        await p.locator("button.settings-row", { hasText: nombre }).click(); await titulo(p, nombre)
      } })),
  // La última: empezar un entreno cambia Entrenar («Seguir con la fuerza») para lo que venga detrás
  { id: "entreno-libre", nombre: "Sesión de fuerza (entreno libre)", archivo: "Strength.tsx",
    llegar: async (p) => { await seccion(p, "Entrenar"); await p.getByRole("button", { name: "Entrenar fuerza" }).click(); await titulo(p, "Entreno libre") } },
]
