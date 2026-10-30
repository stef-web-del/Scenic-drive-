import { blockTiles, cards, expect, list, openFilters, test } from "./fixtures"

// Karte/Kacheln sind nicht deterministisch → Kacheln abklemmen, Karten-Canvas und Vorschaubild maskieren
test.describe("Visuelle Regression", () => {
  test.beforeEach(async ({ page }) => { await blockTiles(page); await page.goto("/"); await expect(list(page)).toBeVisible(); await page.evaluate(() => document.fonts.ready) })
  // Maske per CSS: Karte/Vorschaubild unsichtbar (ein Overlay-Mask würde mobil das darüberliegende Sheet überdecken)
  const css = "canvas.maplibregl-canvas, main > img { visibility: hidden !important; }"
  const opts = { style: css }
  test.beforeEach(async ({ page }) => { await page.addStyleTag({ content: css }) })
  test("Start", async ({ page }) => { await expect(page).toHaveScreenshot("start.png", opts) })
  test("Detail", async ({ page }) => {
    await cards(page).first().click(); await expect(page.locator(".recharts-area").first()).toBeVisible()
    await expect(page).toHaveScreenshot("detail.png", opts)
  })
  test("Losfahren-Menü", async ({ page }) => {
    await cards(page).first().click(); await expect(page.locator(".recharts-area").first()).toBeVisible()
    await page.getByRole("button", { name: "Losfahren" }).click(); await expect(page.getByRole("menu")).toBeVisible(); await page.waitForTimeout(300)
    await expect(page).toHaveScreenshot("losfahren.png", opts)
  })
  test("Score-Aufschlüsselung", async ({ page }) => {
    await cards(page).first().click(); await expect(page.locator(".recharts-area").first()).toBeVisible()
    const sec = page.locator('section[aria-labelledby="h-sb"]'); await sec.scrollIntoViewIfNeeded()
    await expect(sec).toHaveScreenshot("score.png")
  })
  test("Filter", async ({ page }) => {
    await openFilters(page); await expect(page.getByRole("radio", { name: "25 %" })).toBeVisible(); await page.waitForTimeout(400)
    await expect(page).toHaveScreenshot("filter.png", opts)
  })
})
