import { afterEach, describe, expect, it, vi } from "vitest"
import { fmt, gpx, kindLabel, loadDetail, loadMeta, scoreColor, SCORE_STOPS } from "./data"
import { detail, loop } from "@/test/fixtures"

describe("Formatierung", () => {
  it("deutsches Zahlenformat", () => { expect(fmt(1306)).toBe("1.306"); expect(fmt(54.44, 1)).toBe("54,4"); expect(fmt(0.2, 3)).toBe("0,200") })
  it("Scorefarbe (Viridis-Stufen)", () => {
    expect(scoreColor(0.1)).toBe(SCORE_STOPS[0][1]); expect(scoreColor(0.17)).toBe(SCORE_STOPS[1][1]); expect(scoreColor(0.2)).toBe(SCORE_STOPS[2][1]); expect(scoreColor(0.3)).toBe(SCORE_STOPS[3][1])
  })
  it("Pausenart-Label mit Fallback", () => { expect(kindLabel("cafe")).toBe("Café"); expect(kindLabel("xyz")).toBe("xyz") })
})

describe("GPX-Export", () => {
  const g = gpx(loop({ id: 9, ti: 'A & B <"x">', km: 54.4 }), detail({ pauses: [{ n: "Wirt & Co", k: "biergarten", km: 1, ll: [9.1, 49.5] }] }))
  it("gültiges GPX 1.1 mit Track und Wegpunkten", () => {
    const doc = new DOMParser().parseFromString(g, "application/xml")
    expect(doc.getElementsByTagName("parsererror")).toHaveLength(0)
    expect(doc.getElementsByTagName("trkpt")).toHaveLength(2)
    expect(doc.getElementsByTagName("trkpt")[0].getAttribute("lat")).toBe("49.7")
    expect(doc.querySelector("wpt name")!.textContent).toBe("Wirt & Co")
    expect(doc.querySelector("wpt type")!.textContent).toBe("Biergarten")
    expect(doc.querySelector("metadata name")!.textContent).toBe('A & B <"x">')
    expect(doc.querySelector("trk name")!.textContent).toBe("Runde 9 · 54,4 km")
  })
  it("ODbL-Lizenzhinweis", () => expect(g).toContain("opendatacommons.org/licenses/odbl"))
})

it("GPX: Route mit Lenkpunkten (Start, Lenkpunkte, Ziel) zusätzlich zum Track", () => {
  const doc = new DOMParser().parseFromString(gpx(loop({ id: 9 }), detail()), "application/xml")
  const rp = [...doc.querySelectorAll("rte rtept")]
  expect(rp.map((e) => e.querySelector("name")!.textContent)).toEqual(["Start", "Lenkpunkt 1", "Ziel"])
  expect(rp[1].getAttribute("lat")).toBe("49.7"); expect(doc.querySelectorAll("trk trkpt").length).toBe(2)
  expect(gpx(loop(), detail({ nav: undefined }))).not.toContain("<rte>")
})

describe("Laden", () => {
  afterEach(() => vi.unstubAllGlobals())
  it("Meta-Fehler wird mit HTTP-Status gemeldet", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 404 })))
    await expect(loadMeta()).rejects.toThrow("meta.json: HTTP 404")
  })
  it("Detail-Shard id % 32, unbekannte Runde → Fehler", async () => {
    const f = vi.fn(async () => Response.json({ 33: detail({ id: 33 }) })); vi.stubGlobal("fetch", f)
    expect((await loadDetail(33)).id).toBe(33)
    expect(f).toHaveBeenCalledWith(expect.stringMatching(/data\/r\/s1\.json$/))
    await expect(loadDetail(65)).rejects.toThrow("Runde 65 nicht gefunden")
    expect(f).toHaveBeenCalledTimes(1) // Shard gecacht
  })
})
