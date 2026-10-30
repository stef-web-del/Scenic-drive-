/**
 * „Ab meinem Standort einsteigen“ (Web): gleiche Regeln wie die App (shared/nav/LoopEntry.kt), vereinfacht für Navi-Links.
 * Standort auf die Runde projizieren, Fahrtrichtung beachten, kürzeste Anfahrt wählen; Lenkpunkte so rotieren,
 * dass die Runde am Einstieg beginnt und dort endet (offizieller Start wird zum Zwischenziel).
 */
import { LIMITS, type App, type LL } from "@/lib/nav"

export const DIRECT_START_M = 150
const R = 6371008.8
const rad = (d: number) => (d * Math.PI) / 180
export function dist(a: LL, b: LL): number {
  const p1 = rad(a[1]), p2 = rad(b[1]), dp = p2 - p1, dl = rad(b[0] - a[0])
  const h = Math.sin(dp / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)))
}
export function bearing(a: LL, b: LL): number {
  const p1 = rad(a[1]), p2 = rad(b[1]), dl = rad(b[0] - a[0])
  return ((Math.atan2(Math.sin(dl) * Math.cos(p2), Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)) * 180) / Math.PI + 360) % 360
}
export interface Entry { point: LL; alongM: number; distM: number; totalM: number; official: boolean }

function cumulative(g: LL[]): number[] { const c = [0]; for (let i = 1; i < g.length; i++) c.push(c[i - 1] + dist(g[i - 1], g[i])); return c }

/** Projektion je Segment; lokale Minima (mehrfache Passagen) als Kandidaten, Richtungsstrafe bei > 100° Abweichung. */
export function bestEntry(g: LL[], pos: LL, headingDeg?: number | null): Entry {
  const c = cumulative(g), total = c[c.length - 1]
  const kx = 111320 * Math.cos(rad(pos[1])), ky = 110540
  const seg = g.slice(0, -1).map((a, i) => {
    const b = g[i + 1]
    const ax = (a[0] - pos[0]) * kx, ay = (a[1] - pos[1]) * ky, dx = (b[0] - a[0]) * kx, dy = (b[1] - a[1]) * ky
    const l2 = dx * dx + dy * dy, t = l2 ? Math.min(1, Math.max(0, -(ax * dx + ay * dy) / l2)) : 0
    const d = Math.hypot(ax + t * dx, ay + t * dy)
    return { i, t, d, p: [a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])] as LL, along: c[i] + t * (c[i + 1] - c[i]) }
  })
  const W = 300
  const cands = seg.filter((s) => seg.every((o) => Math.abs(o.along - s.along) > W || o.d >= s.d - 1e-9 || o === s))
  const score = (s: (typeof seg)[number]) => {
    let v = s.d
    if (headingDeg != null) { const b = bearing(g[s.i], g[s.i + 1]); const diff = Math.abs(((b - headingDeg + 540) % 360) - 180); if (diff > 100) v += 2000 }
    return v
  }
  const best = (cands.length ? cands : seg).reduce((a, b) => (score(b) < score(a) - 1e-9 ? b : a))
  // Kurz vor dem Ziel oder am Anfang → offizieller Start
  const official = best.along < 30 || total - best.along < 80
  return { point: official ? g[0] : best.p, alongM: official ? 0 : best.along, distM: official ? dist(pos, g[0]) : best.d, totalM: total, official }
}

/** Lenkpunkte rotieren: Einstieg → Ende, offizieller Start als Zwischenziel, Anfang → Einstieg. Ziel = Einstieg. */
export function rotateWaypoints(app: App, wp: LL[], g: LL[], e: Entry): { wp: LL[]; dest: LL } {
  if (e.official) return { wp, dest: g[0] }
  const c = cumulative(g)
  const alongOf = (p: LL) => { let bi = 0, bd = Infinity; g.forEach((q, i) => { const d = dist(p, q); if (d < bd) { bd = d; bi = i } }); return c[bi] }
  const tagged = wp.map((p) => ({ p, a: alongOf(p) }))
  const after = tagged.filter((t) => t.a > e.alongM + 50), before = tagged.filter((t) => t.a < e.alongM - 50)
  let out = [...after.map((t) => ({ ...t, a: t.a - e.alongM })), { p: g[0], a: c[c.length - 1] - e.alongM, keep: true }, ...before.map((t) => ({ ...t, a: t.a + c[c.length - 1] - e.alongM }))] as { p: LL; a: number; keep?: boolean }[]
  // Limit einhalten: Lenkpunkt mit kleinster Lücke zu den Nachbarn entfernen (offizieller Start bleibt)
  while (out.length > LIMITS[app]) {
    let bi = -1, bg = Infinity
    out.forEach((t, i) => { if (t.keep) return; const prev = i ? out[i - 1].a : 0, next = i < out.length - 1 ? out[i + 1].a : c[c.length - 1]; if (next - prev < bg) { bg = next - prev; bi = i } })
    out = out.filter((_, i) => i !== bi)
  }
  return { wp: out.map((t) => t.p), dest: e.point }
}
