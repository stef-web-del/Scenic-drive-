import { describe, expect, it } from "vitest"
import { appleMultiStopOk, appleUrl, appOrder, detectPlatform, googleUrl, LIMITS, MAX_URL, navLinks, pct, type LL, type NavPlan } from "./nav"

const S: LL = [9.2248586, 49.7009613]
const pts = (n: number): LL[] => Array.from({ length: n }, (_, i) => [9.2 + i * 0.01234567, 49.7 + i * 0.00765432] as LL)

describe("Google Maps URL", () => {
  it("api=1, driving, navigate, Ziel = Start, ohne origin (Standort)", () => {
    const u = new URL(googleUrl(pts(2), S))
    expect(u.origin + u.pathname).toBe("https://www.google.com/maps/dir/")
    expect(u.searchParams.get("api")).toBe("1"); expect(u.searchParams.get("travelmode")).toBe("driving"); expect(u.searchParams.get("dir_action")).toBe("navigate")
    expect(u.searchParams.get("destination")).toBe("49.700961,9.224859"); expect(u.searchParams.has("origin")).toBe(false)
    expect(u.searchParams.get("waypoints")).toBe("49.7,9.2|49.707654,9.212346")
  })
  it("Encoding: Komma %2C, Pipe %7C", () => {
    const s = googleUrl(pts(2), S); expect(s).toContain("waypoints=49.7%2C9.2%7C49.707654%2C9.212346"); expect(s).not.toMatch(/[|,]/)
  })
  it("Limit 9 Wegpunkte, 9 passen unter 2.048 Zeichen", () => {
    expect(googleUrl(pts(LIMITS.google), S, S).length).toBeLessThanOrEqual(MAX_URL)
    expect(() => googleUrl(pts(10), S)).toThrow(/max\. 9/)
  })
  it("ohne Wegpunkte kein leerer Parameter", () => expect(new URL(googleUrl([], S)).searchParams.has("waypoints")).toBe(false))
})

describe("Apple Karten URL", () => {
  it("/directions, wiederholtes waypoint, mode=driving", () => {
    const u = new URL(appleUrl(pts(3), S))
    expect(u.origin + u.pathname).toBe("https://maps.apple.com/directions")
    expect(u.searchParams.getAll("waypoint")).toEqual(["49.7,9.2", "49.707654,9.212346", "49.715309,9.224691"])
    expect(u.searchParams.get("destination")).toBe("49.700961,9.224859"); expect(u.searchParams.get("mode")).toBe("driving"); expect(u.searchParams.has("source")).toBe(false)
  })
  it("Limit 13", () => { expect(() => appleUrl(pts(13), S)).not.toThrow(); expect(() => appleUrl(pts(14), S)).toThrow(/max\. 13/) })
})

describe("Etappen", () => {
  const plan: NavPlan = { f: 0.8, wp: pts(9), st: { f: 0.97, legs: [{ wp: pts(2), end: [9.3, 49.75], f: 0.98 }, { wp: pts(1), end: [9.35, 49.72], f: 0.96 }, { wp: [], end: S, f: 0.97 }] } }
  it("Etappe 1 ab Standort, folgende ab vorigem Etappenende, letzte endet am Start", () => {
    const { whole, legs, stagesF } = navLinks("google", plan, S)
    expect(stagesF).toBe(0.97); expect(whole.f).toBe(0.8); expect(legs.map((l) => l.label)).toEqual(["Etappe 1 von 3", "Etappe 2 von 3", "Etappe 3 von 3"])
    const u = legs.map((l) => new URL(l.href).searchParams)
    expect(u[0].has("origin")).toBe(false); expect(u[1].get("origin")).toBe("49.75,9.3"); expect(u[2].get("origin")).toBe("49.72,9.35")
    expect(u[2].get("destination")).toBe("49.700961,9.224859")
    expect(new URL(navLinks("apple", plan, S).legs[1].href).searchParams.get("source")).toBe("49.75,9.3")
  })
  it("ohne Etappen: nur ganze Runde", () => expect(navLinks("apple", { f: 0.99, wp: pts(3) }, S).legs).toEqual([]))
})

describe("Plattform", () => {
  const iphone = (v: string) => `Mozilla/5.0 (iPhone; CPU iPhone OS ${v} like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1`
  it("iOS-Version und Apple zuerst", () => {
    const p = detectPlatform(iphone("18_4")); expect(p).toEqual({ ios: true, iosVersion: 18.04, mobile: true }); expect(appOrder(p)).toEqual(["apple", "google"]); expect(appleMultiStopOk(p)).toBe(true)
    expect(appleMultiStopOk(detectPlatform(iphone("17_6")))).toBe(false); expect(appleMultiStopOk(detectPlatform(iphone("26_0")))).toBe(true)
  })
  it("iPadOS als Mac mit Touch", () => expect(detectPlatform("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", 5).ios).toBe(true))
  it("Android/Desktop: Google zuerst", () => {
    const a = detectPlatform("Mozilla/5.0 (Linux; Android 14; Pixel 7) Mobile Safari/537.36"); expect(a).toMatchObject({ ios: false, mobile: true }); expect(appOrder(a)[0]).toBe("google")
    expect(detectPlatform("Mozilla/5.0 (X11; Linux x86_64)").mobile).toBe(false)
  })
  it("Prozent abgerundet (nie schöner als gemessen)", () => expect(pct(0.949)).toBe("94 %"))
})
