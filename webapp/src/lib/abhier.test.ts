import { describe, expect, it } from "vitest"
import * as A from "./abhier"

const idx: A.GridIndex = { v: 1, generated: "", center: [49.703, 9.2575], radius_km: 12, spacing_km: 5, minutes: 45,
  points: [{ pid: 0, name: "Bürgstadt", lat: 49.7067, lon: 9.2641, n: 20 }, { pid: 1, name: "leer", lat: 49.70, lon: 9.25, n: 0 }, { pid: 2, name: "Süd", lat: 49.62, lon: 9.25, n: 20 }] }
const loop = (id: string, kl = "A", g: A.LL[] = [[9.26, 49.70], [9.30, 49.72], [9.26, 49.70]]): A.GLoop => ({ id, ti: id, km: 40, min: 45, t: .3, s: .2, kl, hm: 500, rg: "", tags: [], g, prof: [1, 2, 3], pstep: 500,
  hl: [{ km: 10, len: 2000, s: .4 }], orte: ["A", "B"], pausen: [{ n: "P", k: "cafe", km: 5, ll: [9, 49] }], m: {}, nav: true, gurl: "https://www.google.com/maps/dir/?api=1&destination=1,1&waypoints=1,2%7C3,4" })
const data = (): A.PointData => ({ v: 1, pid: 0, name: "x", start: { lat: 49.7, lon: 9.26, name: "x" }, minutes: 45, valid: 9,
  profiles: { sportlich: Array.from({ length: 14 }, (_, i) => ({ id: i + 1, rank: i + 1, score: 1 - i / 20, maxov: 0 })), geniesser: [], entdecker: [] },
  loops: Object.fromEntries(Array.from({ length: 14 }, (_, i) => [String(i + 1), loop(String(i + 1), i === 2 ? "C" : "A", [[9.2 + i * 0.05, 49.7], [9.21 + i * 0.05, 49.71]])])) })
const mem = () => { const m = new Map<string, string>(); return { getItem: (k: string) => m.get(k) ?? null, setItem: (k: string, v: string) => void m.set(k, v) } as unknown as Storage }

describe("Runde ab hier", () => {
  it("wählt den nächsten Rasterpunkt mit Runden und schätzt die Anfahrt", () => {
    const r = A.nearest(idx, { lat: 49.701, lon: 9.251 })!
    expect(r.point.pid).toBe(0); expect(r.km).toBeGreaterThan(0.5); expect(r.approachMin).toBeGreaterThanOrEqual(1)
    expect(A.inArea(idx, { lat: 49.7, lon: 9.26 })).toBe(true); expect(A.inArea(idx, { lat: 50.1, lon: 8.6 })).toBe(false)
  })
  it("kehrt eine Runde vollständig um", () => {
    const r = A.reverseLoop(loop("1"))
    expect(r.g[0]).toEqual([9.26, 49.70]); expect(r.prof).toEqual([3, 2, 1]); expect(r.hl[0].km).toBe(28); expect(r.pausen[0].km).toBe(35)
    expect(new URL(r.gurl!).searchParams.get("waypoints")).toBe("3,4|1,2")
  })
  it("berechnet den Sonnenuntergang plausibel (Miltenberg, 29.10.2026 – Referenz astral: 17:05 MEZ)", () => {
    const s = A.sunset(new Date(2026, 9, 29), 49.70, 9.26)!
    const min = s.getUTCHours() * 60 + s.getUTCMinutes(); expect(Math.abs(min - (16 * 60 + 5))).toBeLessThan(4)
  })
  it("zeigt 6 Runden, mehr seitenweise, Qualitätsfilter und gefahren", () => {
    const p0 = A.loadProfile(mem()); const d = data()
    let s = A.shownLoops(d, "sportlich", { hideDriven: false, relaxQuality: false, lessOverlap: false }, p0, [], 1)
    expect(s.list).toHaveLength(6); expect(s.more).toBe(true); expect(s.total).toBe(13)
    s = A.shownLoops(d, "sportlich", { hideDriven: false, relaxQuality: true, lessOverlap: false }, p0, [], 3); expect(s.total).toBe(14); expect(s.more).toBe(false)
    const p1 = A.toggleDriven(p0, "1"); expect(A.isDriven(p1, "1")).toBe(true)
    s = A.shownLoops(d, "sportlich", { hideDriven: true, relaxQuality: true, lessOverlap: false }, p1, [], 3); expect(s.list.map((x) => x.l.id)).not.toContain("1")
    const p2 = A.toggleDriven(p1, "1"); expect(A.isDriven(p2, "1")).toBe(false); expect(p2.driven[0].deleted).toBe(true)
    s = A.shownLoops(d, "sportlich", { hideDriven: false, relaxQuality: true, lessOverlap: true }, p1, [d.loops["2"].g], 3); expect(s.list.map((x) => x.l.id)).not.toContain("2")
  })
  it("speichert das lokale Profil sync-fähig", () => {
    const st = mem(); let p = A.loadProfile(st); expect(p.id).toMatch(/^[0-9a-f-]{36}$/)
    p = A.addReport(A.setProfileKind(p, "geniesser"), "7", "Baustelle / gesperrt", "x".repeat(900)); A.saveProfile(p, st)
    const q = A.loadProfile(st); expect(q.profile).toBe("geniesser"); expect(q.reports[0].text).toHaveLength(500); expect(q.reports[0].deleted).toBe(false); expect(q.reports[0].createdAt).toBeTruthy()
  })
})
