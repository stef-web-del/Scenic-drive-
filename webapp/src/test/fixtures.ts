import type { Detail, Loop } from "@/lib/data"
let id = 1
export function loop(p: Partial<Loop> = {}): Loop {
  const i = p.id ?? id++
  return { id: i, km: 50, min: 50, s: 0.2, t: 0.3, kl: "B", rg: "Odenwald (N)", hm: 500, tags: ["Panorama"], ti: `Runde ${i} Titel`,
    fn: [1, 1, 1, 1, 1], f: [i, i, i, i, i], r: [1, 1, 1, 1, 1], bb: [9, 49, 10, 50], ...p }
}
export function detail(p: Partial<Detail> = {}): Detail {
  return { id: 1, g: [[9.2, 49.7], [9.3, 49.71]], prof: [120, 180, 260, 200], step: 500, pauses: [{ n: "Gasthof Linde", k: "restaurant", km: 12.3, ll: [9.25, 49.705] }],
    hl: [], orte: ["Amorbach", "Schneeberg"], nav: { g: { f: 0.97, wp: [[9.25, 49.7], [9.3, 49.71]] }, a: { f: 0.83, wp: [[9.25, 49.7]], st: { f: 0.96, legs: [{ wp: [[9.25, 49.7]], end: [9.3, 49.71], f: 0.95 }, { wp: [], end: [9.22, 49.7], f: 0.97 }] } } }, tx: "Kurvige Runde mit Panorama.", ...p }
}
import type { ScoreBreak } from "@/lib/score"
export function sb(p: Partial<ScoreBreak> = {}): ScoreBreak {
  return {
    c: { route: 0.08, topk: 0.1, flow: 0.1, dram: 0.05, view: 0.06, interrupts: -0.04 },
    v: { kurv: 0.2, kehr: 18, rhy: 0.66, rel: 0.42, hmkm: 13, aus: 0.17, fern: 1300, wald: 0.3, offen: 0.4, ort: 0.18, ortn: 8, b: 0.02, hl: 0.3, tr: 0.25, unt: 4, amp: 10, dram: 0.5, top: 0.4 },
    p: { kurv: 90, kehr: 81, rhy: 43, rel: 61, aus: 17, ort: 44, b: 31, unt: 27, dram: 92, hl: 71 },
    sg: [[0, 4950, 0.004, "t", "Breitendieler Straße", 0.1], [4.95, 3225, 0.19, "n", "St 507", 0.6], [8.18, 2000, 0.41, "h", "Kehrenstrecke", 4.2], [10.2, 500, 0.5, "h", "Kurz", 3]],
    ...p,
  }
}
