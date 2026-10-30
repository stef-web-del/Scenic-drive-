import { describe, expect, it } from "vitest"
import { BARS, METRICS, bestSegs, sliceGeom, strengthLabels } from "./score"
import { sb } from "@/test/fixtures"
import meta from "../../public/data/meta.json"
import shard from "../../public/data/r/s1.json"

describe("score", () => {
  it("bestSegs: ohne Verbindungen und < 1 km, absteigend", () => {
    const b = bestSegs(sb().sg); expect(b.map((x) => x.i)).toEqual([2, 1])
  })
  it("sliceGeom schneidet nach Distanz", () => {
    const g: [number, number][] = [[9, 49], [9.01, 49], [9.02, 49], [9.03, 49]]
    const s = sliceGeom(g, 0.5, 1000); expect(s[0][0]).toBeGreaterThan(9.006); expect(s[s.length - 1][0]).toBeLessThan(9.022); expect(s.length).toBeGreaterThanOrEqual(3)
  })
  it("Stärken-Labels nur für bekannte Schlüssel", () => { expect(strengthLabels(["kurv", "xx", "dram"])).toEqual(["Sehr kurvig", "Gute Dramaturgie"]) })
  it("Echte Daten: Beiträge summieren zum Tour-Score, Perzentile 0–100, alle Balken vorhanden", () => {
    const byId = new Map(meta.loops.map((l) => [String(l.id), l]))
    let n = 0
    for (const [id, d] of Object.entries(shard as Record<string, { sb: { c: Record<string, number>; p: Record<string, number>; v: Record<string, number>; sg: unknown[] } }>)) {
      const sum = Object.values(d.sb.c).reduce((a, b) => a + b, 0); const l = byId.get(id)
      if (l) { expect(Math.abs(sum - l.t)).toBeLessThan(0.002); n++ }
      for (const k of BARS) { expect(d.sb.p[k]).toBeGreaterThanOrEqual(0); expect(d.sb.p[k]).toBeLessThanOrEqual(100) }
      for (const m of METRICS) expect(Number.isFinite(d.sb.v[m.k])).toBe(true)
      expect(d.sb.sg.length).toBeGreaterThan(0)
    }
    expect(n).toBeGreaterThan(0); expect(meta.sbm.n).toBe(319)
  })
})
