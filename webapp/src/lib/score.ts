/** Score-Aufschlüsselung: Kennzahlen, Labels, Stärken-Chips, Abschnitts-Geometrie. Datenformat siehe README. */
export type MetricKey = "kurv" | "kehr" | "rhy" | "rel" | "hmkm" | "aus" | "fern" | "wald" | "offen" | "ort" | "ortn" | "b" | "hl" | "tr" | "unt" | "amp" | "dram" | "top"
export type ContribKey = "route" | "topk" | "flow" | "dram" | "view" | "interrupts"
/** Abschnitt: [km-Start, Länge m, Score, Art h|n|t, Straßenname, Steigung %] */
export type Seg = [number, number, number, "h" | "n" | "t", string, number]
export interface ScoreBreak { c: Record<ContribKey, number>; v: Record<MetricKey, number>; p: Partial<Record<MetricKey, number>>; sg: Seg[] }
export interface ScoreMeta { avg: Record<MetricKey, number>; med: Record<MetricKey, number>; c: Record<ContribKey, number>; n: number }

const pct = (x: number) => Math.round(x * 100) + " %"
const de = (x: number, d: number) => x.toLocaleString("de-DE", { minimumFractionDigits: d, maximumFractionDigits: d })
export interface MetricDef { k: MetricKey; label: string; chip?: string; help: string; fmt: (x: number) => string; better: 1 | -1 | 0 }
export const METRICS: MetricDef[] = [
  { k: "kurv", label: "Kurvigkeit", chip: "Sehr kurvig", help: "Mittlere Kurvigkeit entlang der Runde (Richtungswechsel je Strecke, 0–1).", fmt: (x) => de(x, 2), better: 1 },
  { k: "kehr", label: "Enge Kurven", chip: "Viele enge Kurven", help: "Richtungsänderung in engen Radien, Grad je km.", fmt: (x) => de(x, 0) + " °/km", better: 1 },
  { k: "rhy", label: "Rhythmus", chip: "Flüssiger Rhythmus", help: "Wie gleichmäßig die Kurvigkeit innerhalb der Abschnitte ist (1 = sehr gleichmäßig, kein ständiger Wechsel zwischen gerade und eng).", fmt: (x) => de(x, 2), better: 1 },
  { k: "rel", label: "Relief", chip: "Bergig", help: "Geländebewegung (Höhenwechsel je km, auf 0–1 skaliert).", fmt: (x) => de(x, 2), better: 1 },
  { k: "hmkm", label: "Anstieg", help: "Aufwärts-Höhenmeter je Kilometer.", fmt: (x) => de(x, 1) + " Hm/km", better: 1 },
  { k: "aus", label: "Aussicht", chip: "Weite Aussicht", help: "Anteil der Strecke mit freier Sicht in die Landschaft (Sichtfeldberechnung auf dem Höhenmodell).", fmt: pct, better: 1 },
  { k: "fern", label: "Fernsicht", help: "Typische Sichtweite, wo freie Sicht besteht.", fmt: (x) => de(x / 1000, 1) + " km", better: 1 },
  { k: "ort", label: "Wenig Ortsdurchfahrt", chip: "Wenig Ortsdurchfahrten", help: "Anteil der Strecke innerorts. Weniger ist besser.", fmt: pct, better: -1 },
  { k: "ortn", label: "Ortseinfahrten", help: "Anzahl der Ortseinfahrten auf der Runde. Weniger ist besser.", fmt: (x) => de(x, 0), better: -1 },
  { k: "b", label: "Wenig Bundesstraße", chip: "Kaum Bundesstraße", help: "Anteil auf Bundesstraßen. Weniger ist besser.", fmt: pct, better: -1 },
  { k: "hl", label: "Highlight-Anteil", chip: "Viele Highlights", help: "Anteil der Strecke auf Highlight-Abschnitten.", fmt: pct, better: 1 },
  { k: "unt", label: "Wenig Unterbrechung", chip: "Wenig Stopps", help: "Ampeln, Stoppschilder und Ortseinfahrten je 10 km. Weniger ist besser.", fmt: (x) => de(x, 1) + " /10 km", better: -1 },
  { k: "dram", label: "Dramaturgie", chip: "Gute Dramaturgie", help: "Wie gut die Highlights über die Runde verteilt sind, statt sich am Anfang oder Ende zu ballen (0–1).", fmt: (x) => de(x, 2), better: 1 },
]
export const BARS: MetricKey[] = ["kurv", "kehr", "rhy", "rel", "aus", "ort", "b", "unt", "dram"]
export const metric = (k: MetricKey) => METRICS.find((m) => m.k === k)!
export const CONTRIBS: { k: ContribKey; label: string; help: string }[] = [
  { k: "route", label: "Strecke", help: "40 % × Strecken-Score (Kurven, Relief, Landschaft, abzüglich Ort/B-Straße)." },
  { k: "topk", label: "Top-Abschnitte", help: "25 % × mittlerer Score der drei besten Abschnitte." },
  { k: "flow", label: "Wenig Verbindung", help: "15 % × Anteil ohne reine Verbindungsetappen." },
  { k: "dram", label: "Dramaturgie", help: "10 % × Verteilung der Highlights über die Runde." },
  { k: "view", label: "Aussicht", help: "10 % × Aussichtsanteil (ab 25 % voll)." },
  { k: "interrupts", label: "Unterbrechungen", help: "Abzug 0,01 je Unterbrechung pro 10 km (max. 0,10)." },
]
export const strengthLabels = (st?: string[]) => (st ?? []).map((k) => metric(k as MetricKey)?.chip).filter(Boolean) as string[]
export const SEG_KIND = { h: "Highlight", n: "Normal", t: "Verbindung" } as const

/** Beste Abschnitte (ohne Verbindungen, ≥ 1 km), nach Score */
export const bestSegs = (sg: Seg[], n = 5) => sg.map((s, i) => ({ s, i })).filter(({ s }) => s[3] !== "t" && s[1] >= 1000).sort((a, b) => b.s[2] - a.s[2]).slice(0, n)

const hav = (a: [number, number], b: [number, number]) => {
  const R = 6371000, r = Math.PI / 180, dl = (b[1] - a[1]) * r, dn = (b[0] - a[0]) * r
  const h = Math.sin(dl / 2) ** 2 + Math.cos(a[1] * r) * Math.cos(b[1] * r) * Math.sin(dn / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}
/** Ausschnitt der Geometrie von km0 bis km0+len (m); Distanz auf Gesamtlänge totalM skaliert (Geometrie ist vereinfacht) */
export function sliceGeom(g: [number, number][], km0: number, lenM: number, totalM?: number): [number, number][] {
  if (g.length < 2) return []
  const cum = [0]; for (let i = 1; i < g.length; i++) cum.push(cum[i - 1] + hav(g[i - 1], g[i]))
  const f = totalM ? totalM / cum[cum.length - 1] : 1, a = km0 * 1000 / f, b = (km0 * 1000 + lenM) / f
  const at = (d: number): [number, number] => {
    const i = cum.findIndex((c) => c >= d); if (i <= 0) return g[Math.max(0, i)]; const t = (d - cum[i - 1]) / (cum[i] - cum[i - 1] || 1)
    return [g[i - 1][0] + t * (g[i][0] - g[i - 1][0]), g[i - 1][1] + t * (g[i][1] - g[i - 1][1])]
  }
  const out: [number, number][] = [at(a)]; for (let i = 0; i < g.length; i++) if (cum[i] > a && cum[i] < b) out.push(g[i]); out.push(at(Math.min(b, cum[cum.length - 1])))
  return out
}
