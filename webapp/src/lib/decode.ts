/** Dekodiert das Kompaktformat lines.bin (siehe scripts/compact.py): int32 n, int32 0, je Runde int32 id, cnt, x0, y0, int16-Deltas (1e-4°). */
export function decodeLines(buf: ArrayBuffer): Record<string, [number, number][]> {
  const v = new DataView(buf), n = v.getInt32(0, true), out: Record<string, [number, number][]> = {}
  let o = 8
  for (let i = 0; i < n; i++) {
    const id = v.getInt32(o, true), cnt = v.getInt32(o + 4, true); let x = v.getInt32(o + 8, true), y = v.getInt32(o + 12, true); o += 16
    const g: [number, number][] = new Array(cnt); g[0] = [x / 1e4, y / 1e4]
    for (let k = 1; k < cnt; k++) { x += v.getInt16(o, true); y += v.getInt16(o + 2, true); o += 4; g[k] = [x / 1e4, y / 1e4] }
    out[id] = g
  }
  return out
}
