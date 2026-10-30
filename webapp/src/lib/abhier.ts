/** „Runde ab hier“: nächster Rasterpunkt, Profile, Filter, Umkehren, Sonnenuntergang und lokales Profil (sync-fähig). */
export type LL = [number, number] // [lon, lat]
export type ProfileId = "sportlich" | "geniesser" | "entdecker"
export const PROFILES: { id: ProfileId; label: string; hint: string }[] = [
  { id: "sportlich", label: "Sportlich", hint: "Kurven, Rhythmus, Relief" },
  { id: "geniesser", label: "Genießer", hint: "Aussicht, Landschaft, wenig Orte" },
  { id: "entdecker", label: "Entdecker", hint: "viele Orte, Höhenmeter, Einkehr" },
]
export const PAGE = 6
export const GRID_BASE = "/grid/v1"

export interface GridPoint { pid: number; name: string; lat: number; lon: number; n: number; valid?: number; minutes?: number }
export interface GridIndex { v: number; generated: string; center: [number, number]; radius_km: number; spacing_km: number; minutes: number; window?: [number, number]; points: GridPoint[] }
export interface RankEntry { id: number; rank: number; score: number; maxov: number; why?: string[] }
export interface GLoop {
  id: string; ti: string; km: number; min: number; t: number; s: number; kl: string; hm: number; rg: string; tags: string[]
  g: LL[]; prof: number[]; pstep: number; hl: { km: number; len: number; s: number }[]; orte: string[]
  pausen: { n: string; k: string; km: number; ll: LL }[]; m: Record<string, number>; nav: boolean; navMatch?: number; gurl?: string; sha?: string
}
export interface PointData { v: number; pid: number; name: string; start: { lat: number; lon: number; name: string }; minutes: number; valid: number; profiles: Record<ProfileId, RankEntry[]>; loops: Record<string, GLoop> }

const R = 6371
export function haversineKm(a: { lat: number; lon: number }, b: { lat: number; lon: number }): number {
  const r = Math.PI / 180, dLa = (b.lat - a.lat) * r, dLo = (b.lon - a.lon) * r
  const h = Math.sin(dLa / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLo / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}
/** Nächster Rasterpunkt mit Runden. Anfahrt grob geschätzt: Luftlinie × 1,35 Umwegfaktor bei 50 km/h, auf ganze Minuten. */
export function nearest(idx: GridIndex, pos: { lat: number; lon: number }) {
  let best: GridPoint | null = null, d = Infinity
  for (const p of idx.points) { if (!p.n) continue; const k = haversineKm(pos, p); if (k < d) { d = k; best = p } }
  if (!best) return null
  return { point: best, km: d, approachMin: Math.max(1, Math.round((d * 1.35) / 50 * 60)) }
}
/** Ob ein Ort im vorberechneten Gebiet liegt (Radius + halber Rasterabstand). */
export function inArea(idx: GridIndex, pos: { lat: number; lon: number }) {
  return haversineKm(pos, { lat: idx.center[0], lon: idx.center[1] }) <= idx.radius_km + idx.spacing_km
}

/** Runde in Gegenrichtung: Geometrie, Höhenprofil und Highlight-/Pausenpositionen spiegeln. */
export function reverseLoop(l: GLoop): GLoop {
  const flip = (km: number, len = 0) => +(l.km - km - len / 1000).toFixed(1)
  return {
    ...l, g: [...l.g].reverse(), prof: [...l.prof].reverse(),
    hl: l.hl.map((h) => ({ ...h, km: flip(h.km, h.len) })).sort((a, b) => a.km - b.km),
    pausen: l.pausen.map((p) => ({ ...p, km: flip(p.km) })).sort((a, b) => a.km - b.km),
    orte: [...l.orte].reverse(), gurl: l.gurl ? reverseGoogleUrl(l.gurl) : l.gurl,
  }
}
export function reverseGoogleUrl(u: string): string {
  const url = new URL(u); const w = url.searchParams.get("waypoints")
  if (w) url.searchParams.set("waypoints", w.split("|").reverse().join("|"))
  return url.toString()
}

/** Sonnenuntergang (NOAA-Näherung, ±2 min) als Date für den Kalendertag von `day` am Ort. */
export function sunset(day: Date, lat: number, lon: number): Date | null {
  const rad = Math.PI / 180
  const start = Date.UTC(day.getFullYear(), 0, 0); const n = Math.floor((Date.UTC(day.getFullYear(), day.getMonth(), day.getDate()) - start) / 864e5)
  const g = (2 * Math.PI / 365) * (n - 1 + 0.5)
  const eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g))
  const decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g)
  const c = Math.cos(90.833 * rad) / (Math.cos(lat * rad) * Math.cos(decl)) - Math.tan(lat * rad) * Math.tan(decl)
  if (c < -1 || c > 1) return null
  const ha = Math.acos(c) / rad
  const utcMin = 720 - 4 * (lon - ha) - eq
  return new Date(Date.UTC(day.getFullYear(), day.getMonth(), day.getDate()) + utcMin * 60000)
}
/** Ende der Fahrt: jetzt + Anfahrt + Runde + Rückweg vom Rasterpunkt (gleiche Schätzung wie die Anfahrt). */
export function tripEnd(now: Date, approachMin: number, loopMin: number) { return new Date(now.getTime() + (2 * approachMin + loopMin) * 60000) }

/* ---------- Lokales Profil: UUID, Zeitstempel, Löschflag – später synchronisierbar ---------- */
export interface Rec { id: string; createdAt: string; updatedAt: string; deleted: boolean }
export interface Driven extends Rec { loopId: string; reversed: boolean }
export interface Report extends Rec { loopId: string; kind: string; text: string }
export interface LocalProfile extends Rec { schema: 1; profile: ProfileId; driven: Driven[]; reports: Report[] }
export const STORE_KEY = "scenic.local.v1"
export const REPORT_KINDS = ["Baustelle / gesperrt", "Straßenzustand schlecht", "Zu viel Verkehr", "Gefährliche Stelle", "Sonstiges"]
const uuid = () => (typeof globalThis.crypto?.randomUUID === "function" ? crypto.randomUUID() : "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => { const r = (Math.random() * 16) | 0; return (c === "x" ? r : (r & 3) | 8).toString(16) }))
const now = () => new Date().toISOString()
const rec = (): Rec => { const t = now(); return { id: uuid(), createdAt: t, updatedAt: t, deleted: false } }
export function loadProfile(s: Storage | undefined = globalThis.localStorage): LocalProfile {
  try { const p = JSON.parse(s?.getItem(STORE_KEY) ?? "null"); if (p?.schema === 1) return p } catch { /* neu anlegen */ }
  return { ...rec(), schema: 1, profile: "sportlich", driven: [], reports: [] }
}
export function saveProfile(p: LocalProfile, s: Storage | undefined = globalThis.localStorage) { s?.setItem(STORE_KEY, JSON.stringify(p)) }
export function setProfileKind(p: LocalProfile, k: ProfileId): LocalProfile { return { ...p, profile: k, updatedAt: now() } }
export function isDriven(p: LocalProfile, loopId: string) { return p.driven.some((d) => d.loopId === loopId && !d.deleted) }
/** Umschalten: markieren legt einen Datensatz an, entfernen setzt nur das Löschflag (für Sync). */
export function toggleDriven(p: LocalProfile, loopId: string, reversed = false): LocalProfile {
  const t = now(); const cur = p.driven.find((d) => d.loopId === loopId && !d.deleted)
  const driven = cur ? p.driven.map((d) => (d === cur ? { ...d, deleted: true, updatedAt: t } : d)) : [...p.driven, { ...rec(), loopId, reversed }]
  return { ...p, driven, updatedAt: t }
}
export function addReport(p: LocalProfile, loopId: string, kind: string, text: string): LocalProfile {
  return { ...p, reports: [...p.reports, { ...rec(), loopId, kind, text: text.slice(0, 500) }], updatedAt: now() }
}

/* ---------- Überschneidung über ein ~100-m-Zellraster der Linien ---------- */
export function cells(g: LL[], size = 0.0012): Set<string> {
  const s = new Set<string>()
  for (let i = 1; i < g.length; i++) {
    const [a, b] = [g[i - 1], g[i]]; const n = Math.max(1, Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / (size / 2)))
    for (let k = 0; k <= n; k++) { const x = a[0] + ((b[0] - a[0]) * k) / n, y = a[1] + ((b[1] - a[1]) * k) / n; s.add(`${Math.round(x / size / 1.5)}:${Math.round(y / size)}`) }
  }
  return s
}
export function overlap(a: Set<string>, b: Set<string>) { let i = 0; for (const x of a) if (b.has(x)) i++; return i / Math.max(1, Math.min(a.size, b.size)) }

export interface Filters { hideDriven: boolean; relaxQuality: boolean; lessOverlap: boolean }
export const STRICT_CLASSES = ["A+", "A", "B"]
export interface Shown { e: RankEntry; l: GLoop; driven: boolean; ovKnown: number }
/** Rangliste des Profils → Filter → Seitenweise (6, „Mehr Runden zeigen“ +6). known = Geometrien bereits gefahrener Runden. */
export function shownLoops(d: PointData, prof: ProfileId, f: Filters, p: LocalProfile, known: LL[][], pages: number) {
  const kc = f.lessOverlap ? known.map((g) => cells(g)) : []
  const all: Shown[] = []
  for (const e of d.profiles[prof] ?? []) {
    const l = d.loops[String(e.id)]; if (!l) continue
    const driven = isDriven(p, l.id)
    if (f.hideDriven && driven) continue
    if (!f.relaxQuality && !STRICT_CLASSES.includes(l.kl)) continue
    let ovKnown = 0
    if (kc.length && !driven) { const c = cells(l.g); ovKnown = Math.max(0, ...kc.map((k) => overlap(c, k))); if (ovKnown > 0.5) continue }
    all.push({ e, l, driven, ovKnown })
  }
  return { list: all.slice(0, pages * PAGE), total: all.length, more: all.length > pages * PAGE }
}
export const hhmm = (d: Date) => d.toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" })
