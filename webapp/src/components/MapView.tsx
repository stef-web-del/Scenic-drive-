/// <reference types="geojson" />
import { useEffect, useLayoutEffect, useRef, useState } from "react"
import * as maplibregl from "maplibre-gl"
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url"
maplibregl.setWorkerUrl(workerUrl)
type GeoJSONSource = maplibregl.GeoJSONSource; type MLMap = maplibregl.Map
import "maplibre-gl/dist/maplibre-gl.css"
import type { Detail, Layers, Lines, Loop } from "@/lib/data"
import { kindLabel } from "@/lib/data"
import { MAP_THEME, ROUTE, SCORE_STOPS } from "@/lib/mapTheme"
import { sliceGeom } from "@/lib/score"

/** prefers-reduced-motion respektieren: Kamerafahrten dann ohne Animation. */
const motionMs = (ms: number) => (typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : ms)

const STYLE = { light: "/map/scenic-light.json", dark: "/map/scenic-dark.json" }
const scoreExpr = ["interpolate", ["linear"], ["get", "s"], ...SCORE_STOPS.flat()] as unknown as maplibregl.ExpressionSpecification
const empty: GeoJSON.FeatureCollection = { type: "FeatureCollection", features: [] }

interface Props {
  dark: boolean; start: [number, number]; loops: Loop[]; lines: Lines | null; layers: Layers | null
  visible: Loop[]; sel: Loop | null; detail: Detail | null
  show: { heat: boolean; refs: boolean; pauses: boolean }
  padding: { top: number; bottom: number; left: number; right: number }
  onPick: (id: number | null) => void
  seg?: number | null; segCol?: boolean
}
export function MapView(p: Props) {
  const el = useRef<HTMLDivElement>(null); const map = useRef<MLMap | null>(null)
  const [ready, setReady] = useState(0) // erhöht sich nach jedem Style-Load
  const latest = useRef(p); useLayoutEffect(() => { latest.current = p })

  useEffect(() => {
    const m = new maplibregl.Map({
      container: el.current!, style: STYLE[p.dark ? "dark" : "light"], center: p.start, zoom: 9.6,
      attributionControl: false, dragRotate: false, pitchWithRotate: false, touchPitch: false,
    })
    m.touchZoomRotate.disableRotation()
    m.addControl(new maplibregl.AttributionControl({ compact: true, customAttribution: 'Daten © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap-Mitwirkende</a> · Höhen: Copernicus DEM' }), "bottom-right")
    m.addControl(new maplibregl.NavigationControl({ showCompass: false }), "top-right")
    m.addControl(new maplibregl.GeolocateControl({ trackUserLocation: false }), "top-right")
    m.on("style.load", () => setReady((r) => r + 1))
    // Antippen: Toleranzbox (Touch-freundlich), höchster Score gewinnt
    m.on("click", (e) => {
      const r = 10, b: [maplibregl.PointLike, maplibregl.PointLike] = [[e.point.x - r, e.point.y - r], [e.point.x + r, e.point.y + r]]
      if (m.queryRenderedFeatures(b, { layers: ["pausen"] }).length) return
      const f = m.queryRenderedFeatures(b, { layers: ["runden-hit"] })
      if (!f.length) { latest.current.onPick(null); return }
      f.sort((a, c) => (c.properties!.s as number) - (a.properties!.s as number)); latest.current.onPick(f[0].properties!.id as number)
    })
    m.on("click", "pausen", (e) => {
      const f = e.features![0].properties!
      new maplibregl.Popup({ closeButton: true, maxWidth: "260px" }).setLngLat(e.lngLat).setHTML(`<strong>${f.n}</strong><br>${kindLabel(f.k)} · Pausenvorschlag in ${f.c} Runden`).addTo(m)
    })
    for (const l of ["runden-hit", "pausen", "refs"]) { m.on("mouseenter", l, () => (m.getCanvas().style.cursor = "pointer")); m.on("mouseleave", l, () => (m.getCanvas().style.cursor = "")) }
    map.current = m
    return () => m.remove()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => { map.current?.setStyle(STYLE[p.dark ? "dark" : "light"]) }, [p.dark])

  // Quellen/Ebenen nach Style-Load anlegen
  useEffect(() => {
    const m = map.current; if (!m || !ready) return
    const add = (id: string, data: GeoJSON.FeatureCollection) => { if (!m.getSource(id)) m.addSource(id, { type: "geojson", data }) }
    add("heat", empty); add("refs", empty); add("pausen", empty); add("runden", empty); add("sel", empty); add("segs", empty); add("seg", empty); add("start", { type: "FeatureCollection", features: [{ type: "Feature", properties: {}, geometry: { type: "Point", coordinates: p.start } }] })
    const T = MAP_THEME[p.dark ? "dark" : "light"]
    const L = (spec: maplibregl.LayerSpecification) => { if (!m.getLayer(spec.id)) m.addLayer(spec) }
    L({ id: "heat", type: "line", source: "heat", layout: { "line-cap": "round", visibility: "none" }, paint: { "line-color": ["interpolate", ["linear"], ["get", "s"], 0.2, SCORE_STOPS[0][1], 0.3, SCORE_STOPS[1][1], 0.4, SCORE_STOPS[2][1], 0.5, SCORE_STOPS[3][1]], "line-width": ["interpolate", ["linear"], ["zoom"], 9, 2, 14, 6], "line-opacity": 0.85 } })
    L({ id: "runden", type: "line", source: "runden", layout: { "line-cap": "round", "line-join": "round" }, paint: { "line-color": scoreExpr, "line-width": ["interpolate", ["linear"], ["zoom"], 9, 1.6, 14, 4], "line-opacity": 0.55 } })
    L({ id: "runden-hit", type: "line", source: "runden", paint: { "line-color": T.routeCasing, "line-opacity": 0, "line-width": 14 } })
    L({ id: "refs", type: "line", source: "refs", layout: { visibility: "none", "line-cap": "round" }, paint: { "line-color": T.approach, "line-width": 4, "line-dasharray": [2, 1.5] } })
    L({ id: "sel-casing", type: "line", source: "sel", layout: { "line-cap": "round", "line-join": "round" }, paint: { "line-color": T.routeCasing, "line-width": ROUTE.routeWidth + 2 * ROUTE.routeCasing + 1 } })
    L({ id: "sel", type: "line", source: "sel", layout: { "line-cap": "round", "line-join": "round" }, paint: { "line-color": T.routeActive, "line-width": ROUTE.routeWidth } })
    L({ id: "segs", type: "line", source: "segs", layout: { "line-cap": "round", "line-join": "round" }, paint: { "line-color": scoreExpr, "line-width": 5 } })
    L({ id: "seg-casing", type: "line", source: "seg", layout: { "line-cap": "round", "line-join": "round" }, paint: { "line-color": T.routeCasing, "line-width": 12 } })
    L({ id: "seg", type: "line", source: "seg", layout: { "line-cap": "round", "line-join": "round" }, paint: { "line-color": T.label, "line-width": 7 } })
    L({ id: "pausen", type: "circle", source: "pausen", layout: { visibility: "none" }, paint: { "circle-radius": ["interpolate", ["linear"], ["get", "c"], 1, 5, 200, 11], "circle-color": T.routeActive, "circle-stroke-color": T.routeCasing, "circle-stroke-width": 1.5 } })
    L({ id: "start", type: "circle", source: "start", paint: { "circle-radius": 9, "circle-color": T.routeActive, "circle-stroke-color": T.routeCasing, "circle-stroke-width": 3 } })
    L({ id: "start-label", type: "symbol", source: "start", layout: { "text-field": "Start/Ziel OBI Miltenberg", "text-font": ["Noto Sans Bold"], "text-size": 12, "text-offset": [0, 1.4], "text-anchor": "top" }, paint: { "text-color": T.label, "text-halo-color": T.land, "text-halo-width": 2 } })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready])

  useEffect(() => {
    const m = map.current; if (!m || !ready || !p.layers) return
    ;(m.getSource("heat") as GeoJSONSource).setData(p.layers.heat); (m.getSource("refs") as GeoJSONSource).setData(p.layers.refs); (m.getSource("pausen") as GeoJSONSource).setData(p.layers.pauses)
  }, [ready, p.layers])
  useEffect(() => {
    const m = map.current; if (!m || !ready) return
    m.setLayoutProperty("heat", "visibility", p.show.heat ? "visible" : "none"); m.setLayoutProperty("refs", "visibility", p.show.refs ? "visible" : "none"); m.setLayoutProperty("pausen", "visibility", p.show.pauses ? "visible" : "none")
  }, [ready, p.show.heat, p.show.refs, p.show.pauses])
  useEffect(() => {
    const m = map.current; if (!m || !ready || !p.lines) return
    const feats = [...p.visible].sort((a, b) => a.s - b.s).map((l) => ({ type: "Feature" as const, properties: { id: l.id, s: l.s }, geometry: { type: "LineString" as const, coordinates: p.lines![l.id] } }))
    ;(m.getSource("runden") as GeoJSONSource).setData({ type: "FeatureCollection", features: feats })
    m.setPaintProperty("runden", "line-opacity", p.sel ? 0.18 : p.visible.length < 80 ? 0.85 : 0.55)
  }, [ready, p.lines, p.visible, p.sel])
  useEffect(() => {
    const m = map.current; if (!m || !ready) return
    const g = p.detail && p.sel && p.detail.id === p.sel.id ? p.detail.g : p.sel && p.lines ? p.lines[p.sel.id] : null
    ;(m.getSource("sel") as GeoJSONSource).setData(g ? { type: "Feature", properties: {}, geometry: { type: "LineString", coordinates: g } } : empty)
  }, [ready, p.sel, p.detail, p.lines])
  // Abschnitte: Färbung nach Abschnitts-Score und hervorgehobener Abschnitt
  useEffect(() => {
    const m = map.current; if (!m || !ready) return
    const d = p.detail && p.sel && p.detail.id === p.sel.id ? p.detail : null, sg = d?.sb?.sg ?? [], tot = d ? sg.reduce((a, s) => a + s[1], 0) : 0
    const line = (c: [number, number][], s: number) => ({ type: "Feature" as const, properties: { s }, geometry: { type: "LineString" as const, coordinates: c } })
    // Abschnitts-Score (0–1) auf die Score-Farbskala der Runden (0,15–0,24) abbilden
    ;(m.getSource("segs") as GeoJSONSource).setData(d && p.segCol ? { type: "FeatureCollection", features: sg.map((s) => line(sliceGeom(d.g, s[0], s[1], tot), 0.15 + Math.min(1, s[2] / 0.4) * 0.09)) } : empty)
    const s = d && p.seg != null ? sg[p.seg] : null
    ;(m.getSource("seg") as GeoJSONSource).setData(s ? { type: "FeatureCollection", features: [line(sliceGeom(d!.g, s[0], s[1], tot), s[2])] } : empty)
    if (s) { const c = sliceGeom(d!.g, s[0], s[1], tot), xs = c.map((q) => q[0]), ys = c.map((q) => q[1]); m.fitBounds([[Math.min(...xs), Math.min(...ys)], [Math.max(...xs), Math.max(...ys)]], { padding: { top: p.padding.top + 48, bottom: p.padding.bottom + 48, left: p.padding.left + 48, right: p.padding.right + 48 }, maxZoom: 14, duration: motionMs(500) }) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, p.detail, p.sel, p.seg, p.segCol])
  useEffect(() => {
    const m = map.current; if (!m || !p.sel) return
    const [a, b, c, d] = p.sel.bb; m.fitBounds([[a, b], [c, d]], { padding: { top: p.padding.top + 24, bottom: p.padding.bottom + 24, left: p.padding.left + 24, right: p.padding.right + 24 }, maxZoom: 13, duration: motionMs(600) })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [p.sel?.id])

  return <div className="absolute inset-0"><div ref={el} className="h-full w-full" role="application" aria-label="Karte der Runden" /></div>
}
