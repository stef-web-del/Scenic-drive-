import AxeBuilder from "@axe-core/playwright"
import { cards, expect, list, openFilters, test } from "./fixtures"

const axe = (page: import("@playwright/test").Page) => new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).exclude(".maplibregl-canvas")

test.describe("Barrierefreiheit (axe, WCAG 2.1 AA)", () => {
  test.beforeEach(async ({ page }) => { await page.goto("/"); await expect(list(page)).toBeVisible() })
  test("Startansicht", async ({ page }) => expect((await axe(page).analyze()).violations).toEqual([]))
  test("Rundendetail", async ({ page }) => {
    await cards(page).first().click(); await expect(page.getByText("Höhenprofil")).toBeVisible()
    expect((await axe(page).analyze()).violations).toEqual([])
  })
  test("Navi-Menü (Losfahren)", async ({ page }) => {
    await cards(page).first().click(); await expect(page.getByText("Höhenprofil")).toBeVisible()
    await page.getByRole("button", { name: "Losfahren" }).click(); await expect(page.getByRole("menu")).toBeVisible()
    // Radix-Menü ist modal und setzt den Hintergrund bewusst auf aria-hidden; geprüft wird das offene Menü selbst
    expect((await axe(page).include('[role="menu"]').analyze()).violations).toEqual([])
  })
  test("Score-Erklärung (Popover)", async ({ page }) => {
    await cards(page).first().click(); await expect(page.getByText("Höhenprofil")).toBeVisible()
    await page.getByRole("button", { name: "Erklärung: Kurvigkeit" }).click(); await expect(page.locator('[data-slot="popover-content"]')).toBeVisible()
    expect((await axe(page).analyze()).violations).toEqual([])
  })
  test("Karte im Vollbild", async ({ page }) => {
    await cards(page).first().click(); await expect(page.getByText("Höhenprofil")).toBeVisible()
    await page.getByRole("button", { name: "Karte im Vollbild" }).click(); await expect(page.getByTestId("map-fullscreen")).toBeVisible()
    await page.waitForTimeout(500)
    expect((await axe(page).include('[data-testid="map-fullscreen"]').analyze()).violations).toEqual([])
  })
  test("Filter", async ({ page }) => {
    await openFilters(page); await expect(page.getByRole("radio", { name: "25 %" })).toBeVisible()
    expect((await axe(page).analyze()).violations).toEqual([])
  })
})
