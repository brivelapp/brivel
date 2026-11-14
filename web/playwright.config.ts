import { defineConfig } from "@playwright/test"

// Pruebas de navegador sobre la web ya construida (`npm run build`). BASE_URL: una web servida aparte; CHANNEL: un
// navegador del sistema (por ejemplo `chrome`) en vez del Chromium de Playwright.
const external = process.env.BASE_URL
export default defineConfig({
  testDir: "e2e",
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: external ?? "http://127.0.0.1:4173",
    viewport: { width: 420, height: 900 },  // la web se usa sobre todo en el móvil
    locale: "es-ES",
    channel: process.env.CHANNEL,
    trace: "retain-on-failure",
  },
  webServer: external ? undefined : { command: "npx vite preview --host 127.0.0.1 --port 4173 --strictPort", url: "http://127.0.0.1:4173", reuseExistingServer: !process.env.CI },
})
