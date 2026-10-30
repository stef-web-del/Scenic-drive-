import { test as base, expect, type Page } from "@playwright/test"

/** Sammelt Konsolenfehler und unbehandelte Exceptions; Test schlägt am Ende fehl, wenn welche auftreten. */
export const test = base.extend<{ errors: string[] }>({
  errors: async ({ page }, use) => {
    const errors: string[] = []
    page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()) })
    page.on("pageerror", (e) => errors.push(String(e)))
    await use(errors)
    expect(errors, "Konsolenfehler").toEqual([])
  },
})
export { expect }
export const isMobile = (page: Page) => page.viewportSize()!.width < 768
export const list = (page: Page) => page.getByRole("list", { name: "Runden" })
export const cards = (page: Page) => list(page).getByRole("listitem").getByRole("button").filter({ hasNotText: /^Weitere anzeigen/ })
export async function openFilters(page: Page) {
  if (isMobile(page)) await page.getByRole("button", { name: "Filter öffnen" }).click()
  else await page.getByRole("tab", { name: "Filter" }).click()
}
export async function closeFilters(page: Page) {
  if (isMobile(page)) await page.getByRole("button", { name: /Runden anzeigen$/ }).click()
  else await page.getByRole("tab", { name: "Runden" }).click()
}
/** Kartenkacheln vom Netz abklemmen → deterministische Screenshots (Karte bleibt leer). */
export async function blockTiles(page: Page) {
  await page.route(/tiles\.openfreemap\.org/, (r) => r.fulfill({ status: 204, body: "" }))
}
/** Mobil: Bottom-Sheet über den Griff ganz aufziehen (wie ein Nutzer). */
export async function expandSheet(page: Page) {
  if (!isMobile(page)) return
  const box = (await page.getByRole("dialog").first().boundingBox())!
  const x = box.x + box.width / 2, y = box.y + 8
  await page.mouse.move(x, y); await page.mouse.down(); await page.mouse.move(x, y - 150, { steps: 5 }); await page.mouse.move(x, 20, { steps: 10 }); await page.mouse.up()
  await page.waitForTimeout(600)
}
export const detailTitle = (page: Page) => page.locator("#det-title")
