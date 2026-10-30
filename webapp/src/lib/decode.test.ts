import { expect, it } from "vitest"
import { readFileSync } from "node:fs"
import { decodeLines } from "./decode"

function encode(lines: Record<number, [number, number][]>) {
  const parts: number[][] = []; let size = 8
  for (const [id, g] of Object.entries(lines)) { parts.push([+id, g.length, ...g.flat()]); size += 16 + 4 * (g.length - 1) }
  const v = new DataView(new ArrayBuffer(size)); v.setInt32(0, parts.length, true); let o = 8
  for (const [id, cnt, ...xy] of parts) {
    v.setInt32(o, id, true); v.setInt32(o + 4, cnt, true); v.setInt32(o + 8, xy[0], true); v.setInt32(o + 12, xy[1], true); o += 16
    for (let k = 1; k < cnt; k++) { v.setInt16(o, xy[2 * k] - xy[2 * k - 2], true); v.setInt16(o + 2, xy[2 * k + 1] - xy[2 * k - 1], true); o += 4 }
  }
  return v.buffer
}
it("Roundtrip Kompaktformat (1e-4°, Int16-Deltas)", () => {
  const out = decodeLines(encode({ 9: [[92248, 497009], [92260, 496990], [92100, 497100]], 12: [[90000, 490000]] }))
  expect(out[9]).toEqual([[9.2248, 49.7009], [9.226, 49.699], [9.21, 49.71]]); expect(out[12]).toEqual([[9, 49]])
})
it("echte lines.bin: 83 Startrunden, Koordinaten im Raum Miltenberg", () => {
  const b = readFileSync("public/data/lines.bin"); const out = decodeLines(b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength))
  expect(Object.keys(out)).toHaveLength(83)
  for (const g of Object.values(out)) for (const [x, y] of g) { expect(x).toBeGreaterThan(8.8); expect(x).toBeLessThan(9.8); expect(y).toBeGreaterThan(49.4); expect(y).toBeLessThan(50) }
})
