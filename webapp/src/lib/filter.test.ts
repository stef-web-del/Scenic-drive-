import { describe, expect, it } from "vitest"
import { readFileSync } from "node:fs"
import { familyInfo, representatives, sortLoops, sortRegions, visibleLoops } from "./filter"
import { DEFAULT } from "./state"
import type { Loop, Meta } from "./data"
import { loop } from "@/test/fixtures"

const meta: Meta = JSON.parse(readFileSync("public/data/meta.json", "utf8"))
const all: Loop[] = [...meta.loops, ...JSON.parse(readFileSync("public/data/all.json", "utf8"))]

describe("visibleLoops – Filter", () => {
  const ls = [loop({ id: 1, kl: "A", min: 44 }), loop({ id: 2, kl: "A", min: 45 }), loop({ id: 3, kl: "C", min: 60, s: 0.16 }), loop({ id: 4, kl: "B", min: 61 }), loop({ id: 5, rg: "Spessart", r: [0, 0, 0, 0, 0] })]
  it("Fahrzeitfenster inklusive Grenzen", () => expect(visibleLoops(ls, { ...DEFAULT, rep: false }, null).map((l) => l.id)).toEqual([2, 3, 5]))
  it("Klasse", () => expect(visibleLoops(ls, { ...DEFAULT, kl: ["C"] }, null).map((l) => l.id)).toEqual([3]))
  it("Region", () => expect(visibleLoops(ls, { ...DEFAULT, rep: false, rg: ["Spessart"] }, null).map((l) => l.id)).toEqual([5]))
  it("Mindest-Score mit Float-Toleranz", () => {
    expect(visibleLoops([loop({ s: 0.18 - 1e-12 })], { ...DEFAULT, s: 0.18 }, null)).toHaveLength(1)
    expect(visibleLoops([loop({ s: 0.17 })], { ...DEFAULT, s: 0.18 }, null)).toHaveLength(0)
  })
  it("nur Vertreter bei rep=true", () => expect(visibleLoops(ls, DEFAULT, null).map((l) => l.id)).toEqual([2, 3]))
  it("Varianten ignorieren Filter und zeigen ganze Familie", () => {
    const a = loop({ id: 10, f: [0, 7, 0, 0, 0] }), b = loop({ id: 11, f: [0, 7, 0, 0, 0], min: 90, r: [0, 0, 0, 0, 0] }), c = loop({ id: 12, f: [0, 8, 0, 0, 0] })
    expect(visibleLoops([a, b, c], DEFAULT, a).map((l) => l.id)).toEqual([10, 11])
  })
})

describe("Familienlogik auf echten Daten (max_jaccard)", () => {
  it("Standard ist 25 % (Stufe 1)", () => { expect(DEFAULT.lvl).toBe(1); expect(meta.levels[1]).toBe(0.25) })
  it("Gesamtzahl 342 Runden", () => { expect(meta.total).toBe(342); expect(all).toHaveLength(342) })
  it.each([[0, 4], [1, 9], [2, 13], [3, 29], [4, 93]])("Stufe %i → %i Familien", (lvl, n) => {
    expect(visibleLoops(all, { ...DEFAULT, lvl }, null)).toHaveLength(n)
    expect(representatives(all, lvl)).toBe(n)
  })
  it("jede Familie hat genau einen Vertreter mit dem besten Tour-Score; fn = Familiengröße", () => {
    for (let lvl = 0; lvl < meta.levels.length; lvl++) {
      const fam = new Map<number, Loop[]>(); for (const l of all) fam.set(l.f[lvl], [...(fam.get(l.f[lvl]) ?? []), l])
      for (const m of fam.values()) {
        const reps = m.filter((l) => l.r[lvl]); expect(reps).toHaveLength(1)
        expect(reps[0].t).toBe(Math.max(...m.map((l) => l.t)))
        for (const l of m) expect(l.fn[lvl]).toBe(m.length)
      }
    }
  })
  it("Startdaten enthalten alle Vertreter aller Stufen", () => {
    const ids = new Set(meta.loops.map((l) => l.id))
    for (const l of all) if (l.r.some(Boolean)) expect(ids.has(l.id)).toBe(true)
  })
  it("familyInfo liefert Größe und Vertreter", () => {
    const v = all.find((l) => l.r[1] === 0)!; const info = familyInfo(v, all, 1)
    expect(info.n).toBe(v.fn[1]); expect(all.find((l) => l.id === info.rep)!.r[1]).toBe(1)
    expect(familyInfo(null, all, 1)).toEqual({ n: 0, rep: null })
  })
})

describe("Sortierung", () => {
  const ls = [loop({ id: 1, s: 0.2, t: 0.3, min: 50, hm: 400 }), loop({ id: 2, s: 0.22, t: 0.25, min: 46, hm: 900 }), loop({ id: 3, s: 0.18, t: 0.4, min: 58, hm: 600 })]
  it.each([["score", [2, 1, 3]], ["tour", [3, 1, 2]], ["kurz", [2, 1, 3]], ["lang", [3, 1, 2]], ["hm", [2, 3, 1]]] as const)("%s", (s, ids) =>
    expect(sortLoops(ls, s).map((l) => l.id)).toEqual(ids))
  it("verändert Eingabe nicht", () => { sortLoops(ls, "hm"); expect(ls.map((l) => l.id)).toEqual([1, 2, 3]) })
  it("Regionen nach Häufigkeit", () => expect(sortRegions([loop({ rg: "X" }), loop({ rg: "Y" }), loop({ rg: "Y" })])).toEqual(["Y", "X"]))
})
