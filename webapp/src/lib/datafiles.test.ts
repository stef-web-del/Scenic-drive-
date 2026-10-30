import { describe, expect, it } from "vitest"
import { readdirSync, readFileSync } from "node:fs"
import type { Detail, Loop, Meta } from "./data"

// Datenvertrag: alle ausgelieferten JSON-Dateien müssen striktes JSON sein (kein NaN/Infinity) und zu jeder Runde Details liefern
const D = "public/data/"
const strict = (f: string) => JSON.parse(readFileSync(D + f, "utf8"))
describe("Datendateien", () => {
  const files = ["meta.json", "all.json", "layers.json", ...readdirSync(D + "r").map((f) => "r/" + f)]
  it.each(files)("%s ist gültiges JSON", (f) => expect(() => strict(f)).not.toThrow())
  it("jede der 342 Runden hat Details im richtigen Shard mit Geometrie, Profil und benannten Pausen", () => {
    const meta: Meta = strict("meta.json"); const loops: Loop[] = [...meta.loops, ...strict("all.json")]
    const shards = new Map<number, Record<string, Detail>>()
    for (const l of loops) {
      const k = l.id % 32; if (!shards.has(k)) shards.set(k, strict(`r/s${k}.json`))
      const d = shards.get(k)![String(l.id)]; expect(d, `Runde ${l.id}`).toBeDefined()
      expect(d.g.length).toBeGreaterThan(10); expect(d.prof.length).toBeGreaterThan(5)
      for (const p of d.pauses) expect(typeof p.n === "string" && p.n.length > 0, `Pause in Runde ${l.id}`).toBe(true)
    }
  })
})
