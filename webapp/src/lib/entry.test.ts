import { describe, expect, it } from "vitest"
import { bestEntry, rotateWaypoints, dist } from "./entry"
import { LIMITS, type LL } from "./nav"

// Rechteck-Runde gegen den Uhrzeigersinn ~ 4 × 1,1 km
const sq: LL[] = []
const corners: LL[] = [[9.2, 49.7], [9.215, 49.7], [9.215, 49.71], [9.2, 49.71], [9.2, 49.7]]
for (let k = 0; k < 4; k++) for (let i = 0; i < 20; i++) { const a = corners[k], b = corners[k + 1]; sq.push([a[0] + ((b[0] - a[0]) * i) / 20, a[1] + ((b[1] - a[1]) * i) / 20]) }
sq.push(corners[4])

describe("Einstieg", () => {
  it("Punkt auf der Runde → Abstand ~0, Lage passt", () => {
    const e = bestEntry(sq, [9.215, 49.705])
    expect(e.distM).toBeLessThan(1); expect(e.official).toBe(false)
    expect(e.alongM).toBeGreaterThan(1000); expect(e.alongM).toBeLessThan(1700)
  })
  it("1 km daneben → nächster Punkt, Distanz ~1 km", () => {
    const e = bestEntry(sq, [9.229, 49.705]); expect(e.distM).toBeGreaterThan(950); expect(e.distM).toBeLessThan(1050)
  })
  it("nahe Start/Ziel → offizieller Start", () => expect(bestEntry(sq, [9.2001, 49.7001]).official).toBe(true))
  it("Fahrtrichtung: Gegenrichtung wird bestraft", () => {
    // Zwischen zwei parallelen Seiten (Süd: Ost-Richtung, Nord: West-Richtung), etwas näher am Süden, Kurs West
    const e = bestEntry(sq, [9.2075, 49.7045], 270)
    expect(e.point[1]).toBeGreaterThan(49.7001) // nicht die näher liegende Südseite (Gegenrichtung)
  })
})

describe("Lenkpunkte rotieren", () => {
  const wp: LL[] = [[9.215, 49.7], [9.215, 49.71], [9.2, 49.71]]
  it("Einstieg an der Ostseite: Reihenfolge Nordost, Nordwest, Start, Südost; Ziel = Einstieg", () => {
    const e = bestEntry(sq, [9.215, 49.705])
    const r = rotateWaypoints("google", wp, sq, e)
    expect(r.wp).toEqual([[9.215, 49.71], [9.2, 49.71], [9.2, 49.7], [9.215, 49.7]])
    expect(dist(r.dest, [9.215, 49.705])).toBeLessThan(1)
  })
  it("Limit wird eingehalten, offizieller Start bleibt", () => {
    const many: LL[] = sq.filter((_, i) => i % 8 === 4)
    const e = bestEntry(sq, [9.215, 49.705])
    const r = rotateWaypoints("google", many, sq, e)
    expect(r.wp.length).toBeLessThanOrEqual(LIMITS.google); expect(r.wp).toContainEqual([9.2, 49.7])
  })
  it("offizieller Start → unverändert", () => {
    const e = bestEntry(sq, [9.2, 49.7]); expect(rotateWaypoints("apple", wp, sq, e)).toEqual({ wp, dest: sq[0] })
  })
})
