/**
 * Navi-Übergabe: URLs für Google Maps und Apple Karten aus vorberechneten Lenkpunkten (phase3/navpoints.py).
 * Belegte Grenzen (Stand Okt. 2026):
 * - Google Maps URLs (developers.google.com/maps/documentation/urls/get-started): api=1 Pflicht, Wegpunkte mit „|“ getrennt,
 *   „bis zu drei Wegpunkte in mobilen Browsern, sonst maximal neun“, URL max. 2.048 Zeichen, dir_action=navigate, travelmode=driving.
 * - Apple Unified Maps URLs (developer.apple.com/documentation/mapkit/unified-map-urls): ab iOS 18.4 / macOS 15.4
 *   maps.apple.com/directions mit wiederholtem waypoint=…, mode=driving. Kein Limit dokumentiert; Apple Support: „bis zu 14 Stopps“
 *   → 13 Zwischenziele + Ziel.
 */
export type LL = [number, number] // [lon, lat]
export interface NavLeg { wp: LL[]; end: LL; f: number }
export interface NavPlan { f: number; wp: LL[]; st?: { f: number; legs: NavLeg[] } }
export interface Nav { g: NavPlan; a: NavPlan }
export type App = "google" | "apple"

export const LIMITS: Record<App, number> = { google: 9, apple: 13 }
export const GOOGLE_MOBILE_BROWSER_LIMIT = 3
export const MAX_URL = 2048

const c = (p: LL) => `${+p[1].toFixed(6)},${+p[0].toFixed(6)}` // lat,lon
const q = (o: Record<string, string | undefined>) => Object.entries(o).filter(([, v]) => v != null).map(([k, v]) => `${k}=${encodeURIComponent(v!)}`).join("&")

/** Eine Etappe: origin fehlt → aktueller Standort (Navigation startet sofort), sonst Routenvorschau ab origin. */
export function googleUrl(wp: LL[], dest: LL, origin?: LL): string {
  if (wp.length > LIMITS.google) throw new Error(`Google Maps: max. ${LIMITS.google} Wegpunkte`)
  const u = "https://www.google.com/maps/dir/?" + q({ api: "1", origin: origin && c(origin), destination: c(dest), waypoints: wp.length ? wp.map(c).join("|") : undefined, travelmode: "driving", dir_action: "navigate" })
  if (u.length > MAX_URL) throw new Error("Google Maps: URL zu lang")
  return u
}
export function appleUrl(wp: LL[], dest: LL, origin?: LL): string {
  if (wp.length > LIMITS.apple) throw new Error(`Apple Karten: max. ${LIMITS.apple} Wegpunkte`)
  const parts = [origin && `source=${encodeURIComponent(c(origin))}`, `destination=${encodeURIComponent(c(dest))}`, ...wp.map((p) => `waypoint=${encodeURIComponent(c(p))}`), "mode=driving"].filter(Boolean)
  return "https://maps.apple.com/directions?" + parts.join("&")
}

export interface NavLink { label: string; href: string; f: number }
/** Links je App: Gesamtrunde plus ggf. Etappen (Etappe 1 ab Standort, folgende ab Ende der vorigen). Start = Ziel der Runde. */
export function navLinks(app: App, plan: NavPlan, start: LL): { whole: NavLink; legs: NavLink[]; stagesF: number | null } {
  const mk = app === "google" ? googleUrl : appleUrl
  const whole = { label: "Ganze Runde", href: mk(plan.wp, start), f: plan.f }
  const legs = (plan.st?.legs ?? []).map((l, i, a) => ({ label: `Etappe ${i + 1} von ${a.length}`, href: mk(l.wp, l.end, i ? a[i - 1].end : undefined), f: l.f }))
  return { whole, legs, stagesF: plan.st?.f ?? null }
}

export interface Platform { ios: boolean; iosVersion: number | null; mobile: boolean }
export function detectPlatform(ua: string, maxTouchPoints = 0): Platform {
  const m = ua.match(/(?:iPhone|iPad|iPod).*? OS (\d+)_(\d+)/)
  const ipadDesktop = /Macintosh/.test(ua) && maxTouchPoints > 1 // iPadOS meldet sich als Mac
  const ios = !!m || ipadDesktop
  return { ios, iosVersion: m ? +m[1] + +m[2] / 100 : null, mobile: ios || /Android|Mobi/.test(ua) }
}
/** Apple Unified Maps URLs (mehrere Stopps) erst ab iOS 18.4; ältere Systeme ignorieren die Wegpunkte. */
export const appleMultiStopOk = (p: Platform) => !p.ios || p.iosVersion == null || p.iosVersion >= 18.04
/** Reihenfolge im Menü: auf iOS Apple zuerst. */
export const appOrder = (p: Platform): App[] => (p.ios ? ["apple", "google"] : ["google", "apple"])
export const pct = (f: number) => `${Math.floor(f * 100)} %`
