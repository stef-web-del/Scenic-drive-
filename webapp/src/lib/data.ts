/// <reference types="geojson" />
import type { Nav } from "./nav"
import type { ScoreBreak, ScoreMeta } from "./score"
export type Klasse = "A" | "B" | "C"
export interface Loop {
  id: number; km: number; min: number; s: number; t: number; kl: Klasse; rg: string; hm: number
  tags: string[]; tx?: string; ti: string; fn: number[]; f: number[]; r: number[]; bb: [number, number, number, number]
  /** Stärken (Kennzahl-Schlüssel, Perzentil ≥ 75) */
  st?: string[]
}
export interface Meta { levels: number[]; start: [number, number]; total: number; loops: Loop[]; sbm?: ScoreMeta }
export interface Pause { n: string; k: string; km: number; ll: [number, number] }
export interface Detail {
  id: number; g: [number, number][]; prof: number[]; step: number; pauses: Pause[]
  hl: { km: number; len: number; s: number }[]; orte: string[]; nav?: Nav; tx?: string; sb?: ScoreBreak
}
export type Lines = Record<string, [number, number][]>
export interface Layers { heat: GeoJSON.FeatureCollection; refs: GeoJSON.FeatureCollection; pauses: GeoJSON.FeatureCollection }

const base = import.meta.env.BASE_URL + "data/"
const get = <T,>(p: string): Promise<T> => fetch(base + p).then((r) => { if (!r.ok) throw new Error(p + ": HTTP " + r.status); return r.json() })
export const loadMeta = () => get<Meta>("meta.json")
export function loadLines(file = "lines.bin"): Promise<Lines> {
  return new Promise((res, rej) => {
    const w = new Worker(new URL("./lines.worker.ts", import.meta.url), { type: "module" }); let acc: Lines = {}
    w.onmessage = (e: MessageEvent<{ lines?: Lines; done?: boolean; error?: string }>) => {
      if (e.data.error) { w.terminate(); rej(new Error(e.data.error)); return }
      acc = { ...acc, ...e.data.lines }; if (e.data.done) { w.terminate(); res(acc) }
    }
    w.postMessage(new URL(base + file, location.href).href)
  })
}
/** Alle übrigen Runden (Expertenmodus / Varianten) – nur bei Bedarf */
let allP: Promise<{ loops: Loop[]; lines: Lines }> | null = null
export const loadAll = () => (allP ??= Promise.all([get<Loop[]>("all.json"), loadLines("lines-all.bin")]).then(([loops, lines]) => ({ loops, lines })).catch((e) => { allP = null; throw e }))
export const loadLayers = () => get<Layers>("layers.json")
const shards = new Map<number, Promise<Record<string, Detail>>>()
export const loadDetail = (id: number): Promise<Detail> => {
  const k = id % 32
  if (!shards.has(k)) shards.set(k, get<Record<string, Detail>>(`r/s${k}.json`).catch((e) => { shards.delete(k); throw e }))
  return shards.get(k)!.then((s) => { const d = s[String(id)]; if (!d) throw new Error("Runde " + id + " nicht gefunden"); return d })
}

export const KLASSEN: { v: Klasse; label: string; range: string }[] = [
  { v: "A", label: "Klasse A", range: "0,21–0,24" },
  { v: "B", label: "Klasse B", range: "0,18–0,21" },
  { v: "C", label: "Klasse C", range: "0,15–0,18" },
]
// Viridis, Score 0,15 → 0,24
import { SCORE_STOPS } from "./mapTheme"
export { SCORE_STOPS }
const scoreIdx = (s: number) => { let i = 0; SCORE_STOPS.forEach(([v], k) => { if (s >= v - 0.015) i = k }); return i }
export const scoreColor = (s: number) => SCORE_STOPS[scoreIdx(s)][1]
/** Tailwind-Klasse für den Score-Punkt (Farben aus den Tokens). */
const SCORE_BG = ["bg-score-0", "bg-score-1", "bg-score-2", "bg-score-3"] as const
export const scoreBg = (s: number) => SCORE_BG[scoreIdx(s)]
export const fmt = (n: number, d = 0) => n.toLocaleString("de-DE", { minimumFractionDigits: d, maximumFractionDigits: d })
export const PAUSE_KIND: Record<string, string> = { restaurant: "Restaurant", cafe: "Café", biergarten: "Biergarten", fuel: "Tankstelle", viewpoint: "Aussichtspunkt", parking: "Parkplatz", fast_food: "Imbiss", pub: "Kneipe" }
export const kindLabel = (k: string) => PAUSE_KIND[k] ?? k

export function gpx(l: Loop, d: Detail) {
  const pts = d.g.map(([lo, la]) => `<trkpt lat="${la}" lon="${lo}"/>`).join("")
  const wpts = d.pauses.map((p) => `<wpt lat="${p.ll[1]}" lon="${p.ll[0]}"><name>${esc(p.n)}</name><type>${esc(kindLabel(p.k))}</type></wpt>`).join("")
  // Route (rtept) mit den Lenkpunkten – für Apps, die Routenpunkte neu berechnen (OsmAnd, calimoto, Kurviger); Track bleibt die exakte Strecke
  const st = d.g[0], rp = d.nav ? [st, ...d.nav.a.wp, d.g[d.g.length - 1]] : []
  const rte = rp.length ? `<rte><name>Runde ${l.id} (Lenkpunkte)</name>${rp.map(([lo, la], i) => `<rtept lat="${la}" lon="${lo}"><name>${i === 0 ? "Start" : i === rp.length - 1 ? "Ziel" : "Lenkpunkt " + i}</name></rtept>`).join("")}</rte>` : ""
  return `<?xml version="1.0" encoding="UTF-8"?><gpx version="1.1" creator="Scenic Routes Miltenberg" xmlns="http://www.topografix.com/GPX/1/1"><metadata><name>${esc(l.ti)}</name><desc>${esc(d.tx ?? l.tx ?? "")}</desc><copyright author="OpenStreetMap-Mitwirkende"><license>https://opendatacommons.org/licenses/odbl/</license></copyright></metadata>${wpts}${rte}<trk><name>Runde ${l.id} · ${fmt(l.km, 1)} km</name><trkseg>${pts}</trkseg></trk></gpx>`
}
const esc = (s: string) => s.replace(/[<>&"]/g, (c) => ({ "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;" }[c]!))
