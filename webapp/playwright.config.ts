import { defineConfig, devices } from "@playwright/test"

// BASE_URL=https://scenic-routes-miltenberg.vercel.app npm run test:e2e  → gegen Production (ohne lokalen Server)
const BASE_URL = process.env.BASE_URL
const iphone = { ...devices["iPhone 13"], viewport: { width: 390, height: 844 }, defaultBrowserType: "chromium" as const }

export default defineConfig({
  testDir: "e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  timeout: 45_000,
  expect: { timeout: 10_000, toHaveScreenshot: { maxDiffPixelRatio: 0.01, animations: "disabled", caret: "hide" } },
  reporter: [["list"], ["html", { open: "never" }]],
  snapshotPathTemplate: "{testDir}/__screenshots__/{projectName}/{testFilePath}/{arg}{ext}",
  use: { baseURL: BASE_URL ?? "http://localhost:4173", trace: "retain-on-failure", screenshot: "only-on-failure", locale: "de-DE", timezoneId: "Europe/Berlin", colorScheme: "light" },
  projects: [
    { name: "desktop-chrome", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } } },
    { name: "iphone-390", use: iphone },
    { name: "pixel-7", use: devices["Pixel 7"] },
  ],
  webServer: BASE_URL ? undefined : { command: "npm run build && npx vite preview --port 4173 --strictPort", url: "http://localhost:4173", reuseExistingServer: !process.env.CI, timeout: 180_000 },
})
