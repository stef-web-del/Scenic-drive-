import { cards, closeFilters, detailTitle, expandSheet, expect, isMobile, list, openFilters, test } from "./fixtures"

test.beforeEach(async ({ page }) => { await page.goto("/"); await expect(list(page)).toBeVisible() })

test("Start: 9 Familienvertreter bei 25 % Überschneidung", async ({ page, errors }) => {
  await expect(cards(page)).toHaveCount(9)
  await expect(page.getByText(/9\s*Runden · max\. 25 % Überschneidung/).first()).toBeVisible()
  void errors
})

test("Attribution sichtbar (Standbild mobil bzw. Karte)", async ({ page, errors }) => {
  const attr = isMobile(page) ? page.locator('main a[href="https://www.openstreetmap.org/copyright"]') : page.locator(".maplibregl-ctrl-attrib")
  await expect(attr.first()).toBeVisible()
  if (isMobile(page)) await expect(page.locator(".maplibregl-map")).toHaveCount(0) // Karte erst nach Interaktion
  void errors
})

test("Runde öffnen, Varianten anzeigen, zur besten Runde springen", async ({ page, errors }) => {
  const first = cards(page).first(); const title = (await first.locator(".truncate").first().textContent())!
  await first.click()
  await expect(detailTitle(page)).toContainText(title)
  await expect(page).toHaveURL(/#.*r=\d+/)
  await expandSheet(page)
  await expect(page.getByText("Beste Runde dieser Familie (Tour-Score).")).toBeVisible()
  const vb = page.getByRole("button", { name: /Varianten dieser Runde anzeigen/ })
  const n = Number((await vb.textContent())!.match(/\((\d+)\)/)![1])
  await vb.click()
  await expect(page.getByText(`${n.toLocaleString("de-DE")} Varianten · 25 % Überschneidung`).first()).toBeVisible()
  await expect(cards(page).first()).toBeVisible()
  // eine Variante (nicht Vertreter) öffnen → Link zur besten Runde
  await expandSheet(page)
  await cards(page).nth(1).click()
  await expandSheet(page)
  await page.getByRole("button", { name: "Zur besten Runde der Familie" }).click()
  await expect(detailTitle(page)).toContainText(title)
  void errors
})

test("50 % Überschneidung → 26 Runden; Experten → 319", async ({ page, errors }) => {
  await openFilters(page)
  await page.getByRole("radio", { name: "50 %" }).click()
  await expect(page.getByText(/aktuell 26 Runden/)).toBeVisible()
  await closeFilters(page)
  await expect(cards(page)).toHaveCount(26) // alle 26, unter 40 kein „Weitere anzeigen“
  await expect(page.getByText(/26\s*Runden · max\. 50 %/).first()).toBeVisible()
  await openFilters(page)
  await page.getByRole("switch", { name: "Experten: alle Runden" }).click()
  await closeFilters(page)
  await expect(page.getByText(/Experten: 319 von 319 Runden/).first()).toBeVisible()
  void errors
})

test("GPX-Download", async ({ page, errors }) => {
  await cards(page).first().click(); await expandSheet(page)
  const [dl] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "GPX" }).click()])
  expect(dl.suggestedFilename()).toMatch(/^runde-\d+\.gpx$/)
  const body = await (await dl.createReadStream()).toArray().then((c) => Buffer.concat(c).toString())
  expect(body).toContain("<gpx version=\"1.1\""); expect((body.match(/<trkpt /g) ?? []).length).toBeGreaterThan(100)
  void errors
})

test("Deep-Link mit Hash stellt Zustand wieder her", async ({ page, errors }) => {
  await page.goto("/#lvl=3"); await expect(page.getByText(/26\s*Runden · max\. 50 %/).first()).toBeVisible()
  void errors
})

test("Toast verdeckt den mobilen Header nicht", async ({ page, errors }) => {
  test.skip(!isMobile(page), "nur mobil")
  await cards(page).first().click(); await expect(detailTitle(page)).toBeVisible(); await expandSheet(page); await expect(page.getByText("Beste Runde dieser Familie (Tour-Score).")).toBeVisible()
  await page.getByRole("button", { name: /Varianten dieser Runde anzeigen/ }).click()
  const toast = page.locator("[data-sonner-toast]").first(); await expect(toast).toBeVisible()
  const header = page.locator("span.truncate", { hasText: "Scenic Routes Miltenberg" })
  const [t, h] = [await toast.boundingBox(), await header.locator("..").boundingBox()]
  expect(t!.y).toBeGreaterThanOrEqual(h!.y + h!.height)
  void errors
})

test("Losfahren: Navi-Übergabe mit Google Maps und Apple Karten", async ({ page, errors }) => {
  await cards(page).first().click(); await expandSheet(page)
  await page.getByRole("button", { name: "Losfahren" }).click()
  const menu = page.getByRole("menu"); await expect(menu).toBeVisible()
  const labels = await menu.locator('[role="group"] > div[data-slot="dropdown-menu-label"]').allTextContents()
  // iOS (iPhone-Projekt) → Apple zuerst, sonst Google zuerst
  expect(labels).toEqual(test.info().project.name === "iphone-390" ? ["Apple Karten", "Google Maps"] : ["Google Maps", "Apple Karten"])
  const hrefs = await page.getByRole("menuitem").evaluateAll((a) => a.map((e) => e.getAttribute("href")).filter((h): h is string => !!h))
  const g = hrefs.filter((h) => h.startsWith("https://www.google.com/maps/dir/?api=1&")), ap = hrefs.filter((h) => h.startsWith("https://maps.apple.com/directions?"))
  expect(g.length).toBeGreaterThanOrEqual(1); expect(ap.length).toBeGreaterThanOrEqual(1); expect(g.length + ap.length).toBe(hrefs.length)
  for (const h of g) {
    const u = new URL(h); expect(u.searchParams.get("travelmode")).toBe("driving"); expect(u.searchParams.get("dir_action")).toBe("navigate")
    expect((u.searchParams.get("waypoints") ?? "").split("|").filter(Boolean).length).toBeLessThanOrEqual(9); expect(h.length).toBeLessThanOrEqual(2048)
  }
  for (const h of ap) { const u = new URL(h); expect(u.searchParams.get("mode")).toBe("driving"); expect(u.searchParams.getAll("waypoint").length).toBeLessThanOrEqual(13) }
  await expect(menu.getByText(/ca\. \d+ % originalgetreu/).first()).toBeVisible()
  // Ganze Runde endet am Start (OBI Miltenberg)
  expect(new URL(g[0]).searchParams.get("destination")).toMatch(/^49\.70\d+,9\.22\d+$/)
  await page.keyboard.press("Escape"); await expect(menu).toBeHidden()
  void errors
})

test("Ab meinem Standort einsteigen: Links ab Einstieg, Ziel = Einstieg", async ({ page, context, errors }) => {
  await context.grantPermissions(["geolocation"]); await context.setGeolocation({ latitude: 49.75, longitude: 9.3 })
  await cards(page).first().click(); await expandSheet(page)
  await page.getByRole("button", { name: "Losfahren" }).click()
  await page.getByRole("menuitem", { name: /Ab meinem Standort einsteigen/ }).click()
  const g = page.getByRole("menuitem", { name: /Google Maps ab Einstieg/ }); await expect(g).toBeVisible()
  const h = (await g.getAttribute("href"))!; const u = new URL(h)
  expect(u.searchParams.get("dir_action")).toBe("navigate"); expect((u.searchParams.get("waypoints") ?? "").split("|").length).toBeLessThanOrEqual(9)
  await expect(page.getByRole("menuitem", { name: /Apple Karten ab Einstieg/ })).toHaveAttribute("href", /^https:\/\/maps\.apple\.com\/directions\?/)
  await expect(page.getByTestId("entry-info")).toContainText(/Luftlinie|an der Runde/)
  void errors
})

test("Score-Aufschlüsselung: Teilwerte, Erklärung, Abschnitt auf Karte", async ({ page, errors }) => {
  await expect(cards(page).first().getByText(/Sehr kurvig|Flüssiger Rhythmus|Viele enge Kurven|Bergig|Weite Aussicht|Viele Highlights|Gute Dramaturgie|Wenig Ortsdurchfahrten|Kaum Bundesstraße|Wenig Stopps/).first()).toBeVisible()
  await cards(page).first().click(); await expandSheet(page)
  const sec = page.getByRole("region", { name: "Warum diese Runde?" }).or(page.locator('section[aria-labelledby="h-sb"]'))
  await expect(sec.first()).toBeVisible()
  await expect(page.getByRole("list", { name: "Teilwerte im Vergleich" }).getByRole("listitem")).toHaveCount(9)
  await page.getByRole("button", { name: "Erklärung: Rhythmus" }).click()
  await expect(page.locator('[data-slot="popover-content"]').getByText(/gleichmäßig/)).toBeVisible(); await page.keyboard.press("Escape")
  const seg = page.locator('section[aria-labelledby="h-sb"] li button[aria-pressed]').first()
  await seg.scrollIntoViewIfNeeded(); await seg.click(); await expect(seg).toHaveAttribute("aria-pressed", "true")
  await expect(page.locator(".maplibregl-map")).toHaveCount(1)
  await page.getByRole("switch", { name: "Karte nach Abschnitt färben" }).click()
  await expect(page.getByText(/gelb \(schwach\) bis violett/)).toBeVisible()
  void errors
})

test("Karte im Vollbild: öffnen, Esc schließt, Fokus zurück, X schließt", async ({ page, errors }) => {
  await cards(page).first().click(); await expect(page.getByText("Höhenprofil")).toBeVisible()
  const btn = page.getByRole("button", { name: "Karte im Vollbild" })
  await expect(btn).toBeEnabled()
  const box = (await btn.boundingBox())!; expect(box.width).toBeGreaterThanOrEqual(48); expect(box.height).toBeGreaterThanOrEqual(48)
  await btn.click()
  const ov = page.getByTestId("map-fullscreen")
  await expect(ov).toBeVisible()
  await expect(ov.locator(".maplibregl-ctrl-compass")).toHaveCount(1)
  await expect(ov.locator(".maplibregl-ctrl-geolocate")).toHaveCount(1)
  const close = page.getByRole("button", { name: "Vollbild schließen" })
  await expect(close).toBeFocused()
  const vp = page.viewportSize()!; const ob = (await ov.boundingBox())!
  expect(ob.width).toBe(vp.width); expect(ob.height).toBe(vp.height)
  await page.keyboard.press("Escape")
  await expect(ov).toHaveCount(0)
  await expect(btn).toBeFocused()
  await btn.click(); await expect(ov).toBeVisible()
  await close.click(); await expect(ov).toHaveCount(0)
  void errors
})
