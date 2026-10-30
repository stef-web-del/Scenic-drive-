/// <reference types="geojson" />
import { fmt } from "@/lib/data"
import { MAP_THEME } from "@/lib/mapTheme"
import { useEffect, useMemo, useRef, useState } from "react"
import * as maplibregl from "maplibre-gl"
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url"
import "maplibre-gl/dist/maplibre-gl.css"
import { ArrowLeftIcon, CheckIcon, FlagIcon, LocateFixedIcon, NavigationIcon, RepeatIcon, SunsetIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Switch } from "@/components/ui/switch"
import { Label } from "@/components/ui/label"
import { Spinner } from "@/components/ui/spinner"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Toaster } from "@/components/ui/sonner"
import { toast } from "sonner"
import { useTheme } from "@/components/theme-provider"
import * as A from "@/lib/abhier"
maplibregl.setWorkerUrl(workerUrl)

const STYLE = { light: "/map/scenic-light.json", dark: "/map/scenic-dark.json" }
const ATTR = 'Daten © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap-Mitwirkende</a> (ODbL) · Höhen: Copernicus DEM'
const fc = (f: GeoJSON.Feature[]): GeoJSON.FeatureCollection => ({ type: "FeatureCollection", features: f })

export default function AbHier() {
  const { theme } = useTheme()
  const dark = theme === "dark" || (theme === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches)
  const [idx, setIdx] = useState<A.GridIndex | null>(null); const [err, setErr] = useState<string | null>(null)
  const [pos, setPos] = useState<{ lat: number; lon: number } | null>(null); const [locBusy, setLocBusy] = useState(false)
  const [data, setData] = useState<A.PointData | null>(null); const [busy, setBusy] = useState(false)
  const [prof, setProf] = useState<A.LocalProfile>(() => A.loadProfile())
  const [f, setF] = useState<A.Filters>({ hideDriven: false, relaxQuality: false, lessOverlap: false })
  const [pages, setPages] = useState(1); const [selId, setSelId] = useState<string | null>(null); const [rev, setRev] = useState(false)
  const [report, setReport] = useState<string | null>(null); const [kind, setKind] = useState(A.REPORT_KINDS[0]); const [text, setText] = useState("")
  const [clock, setClock] = useState(() => new Date())
  useEffect(() => { const t = setInterval(() => setClock(new Date()), 60000); return () => clearInterval(t) }, [])
  useEffect(() => { A.saveProfile(prof) }, [prof])
  useEffect(() => { fetch(`${A.GRID_BASE}/index.json`).then((r) => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() }).then(setIdx).catch((e) => setErr(String(e.message ?? e))) }, [])

  const near = useMemo(() => (idx && pos ? A.nearest(idx, pos) : null), [idx, pos])
  const outside = idx && pos ? !A.inArea(idx, pos) : false
  useEffect(() => {
    if (!near) return; let live = true
    setBusy(true); setSelId(null); setPages(1) // eslint-disable-line react-hooks/set-state-in-effect
    fetch(`${A.GRID_BASE}/p${String(near.point.pid).padStart(3, "0")}.json`).then((r) => r.json()).then((d) => { if (live) setData(d) }).catch((e) => setErr(String(e))).finally(() => live && setBusy(false))
    return () => { live = false }
  }, [near?.point.pid]) // eslint-disable-line react-hooks/exhaustive-deps

  const known = useMemo(() => (data ? prof.driven.filter((d) => !d.deleted).map((d) => data.loops[d.loopId]?.g).filter(Boolean) as A.LL[][] : []), [data, prof.driven])
  const shown = useMemo(() => (data ? A.shownLoops(data, prof.profile, f, prof, known, pages) : null), [data, prof, f, known, pages])
  const sel0 = shown?.list.find((s) => s.l.id === selId) ?? shown?.list[0] ?? null
  const sel = sel0 ? { ...sel0, l: rev ? A.reverseLoop(sel0.l) : sel0.l } : null
  const sunset = pos ? A.sunset(clock, pos.lat, pos.lon) : null

  const locate = () => {
    if (!navigator.geolocation) { toast("Standort wird von diesem Browser nicht unterstützt – bitte auf die Karte tippen."); return }
    setLocBusy(true)
    navigator.geolocation.getCurrentPosition((p) => { setPos({ lat: p.coords.latitude, lon: p.coords.longitude }); setLocBusy(false) },
      () => { setLocBusy(false); toast("Standort nicht verfügbar – bitte auf die Karte tippen.") }, { enableHighAccuracy: false, timeout: 10000, maximumAge: 120000 })
  }

  /* ---------- Karte ---------- */
  const el = useRef<HTMLDivElement>(null); const map = useRef<maplibregl.Map | null>(null); const [ready, setReady] = useState(0)
  useEffect(() => {
    const m = new maplibregl.Map({ container: el.current!, style: STYLE[dark ? "dark" : "light"], center: [9.2575, 49.703], zoom: 9.8, attributionControl: false, dragRotate: false })
    m.addControl(new maplibregl.AttributionControl({ compact: true, customAttribution: ATTR }), "bottom-right")
    m.addControl(new maplibregl.NavigationControl({ showCompass: false }), "top-right")
    m.on("style.load", () => setReady((r) => r + 1)); m.on("click", (e) => setPos({ lat: e.lngLat.lat, lon: e.lngLat.lng }))
    map.current = m; return () => m.remove()
  }, [dark])
  useEffect(() => {
    const m = map.current; if (!m || !ready) return
    const src = (id: string, d: GeoJSON.FeatureCollection) => { const s = m.getSource(id) as maplibregl.GeoJSONSource | undefined; if (s) s.setData(d); else m.addSource(id, { type: "geojson", data: d }) }
    src("grid", fc((idx?.points ?? []).filter((p) => p.n).map((p) => ({ type: "Feature", properties: { n: p.name }, geometry: { type: "Point", coordinates: [p.lon, p.lat] } }))))
    src("others", fc((shown?.list ?? []).filter((s) => s.l.id !== sel?.l.id).map((s) => ({ type: "Feature", properties: {}, geometry: { type: "LineString", coordinates: s.l.g } }))))
    src("sel", fc(sel ? [{ type: "Feature", properties: {}, geometry: { type: "LineString", coordinates: sel.l.g } }] : []))
    src("me", fc([...(pos ? [{ type: "Feature" as const, properties: { k: "me" }, geometry: { type: "Point" as const, coordinates: [pos.lon, pos.lat] } }] : []),
      ...(near ? [{ type: "Feature" as const, properties: { k: "start" }, geometry: { type: "Point" as const, coordinates: [near.point.lon, near.point.lat] } }] : []),
      ...(pos && near ? [{ type: "Feature" as const, properties: { k: "anf" }, geometry: { type: "LineString" as const, coordinates: [[pos.lon, pos.lat], [near.point.lon, near.point.lat]] } }] : [])]))
    const T = MAP_THEME[dark ? "dark" : "light"]
    if (!m.getLayer("grid")) {
      m.addLayer({ id: "grid", type: "circle", source: "grid", paint: { "circle-radius": 4, "circle-color": T.routeAlt, "circle-opacity": 0.5 } })
      m.addLayer({ id: "others", type: "line", source: "others", paint: { "line-color": T.routeAlt, "line-width": 2.5, "line-opacity": 0.6 }, layout: { "line-cap": "round", "line-join": "round" } })
      m.addLayer({ id: "sel-case", type: "line", source: "sel", paint: { "line-color": T.routeCasing, "line-width": 7 }, layout: { "line-cap": "round", "line-join": "round" } })
      m.addLayer({ id: "sel", type: "line", source: "sel", paint: { "line-color": T.routeActive, "line-width": 4.5 }, layout: { "line-cap": "round", "line-join": "round" } })
      m.addLayer({ id: "sel-dir", type: "symbol", source: "sel", layout: { "symbol-placement": "line", "symbol-spacing": 90, "text-field": "›", "text-size": 22, "text-keep-upright": false }, paint: { "text-color": T.land } })
      m.addLayer({ id: "anf", type: "line", source: "me", filter: ["==", ["get", "k"], "anf"], paint: { "line-color": T.approach, "line-width": 2, "line-dasharray": [2, 2] } })
      m.addLayer({ id: "me", type: "circle", source: "me", filter: ["==", ["geometry-type"], "Point"], paint: { "circle-radius": 7, "circle-color": ["match", ["get", "k"], "me", T.approach, T.routeActive], "circle-stroke-color": T.land, "circle-stroke-width": 2 } })
    }
  }, [ready, idx, shown, sel?.l.id, rev, pos, near]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    const m = map.current; if (!m || !sel) return
    const b = new maplibregl.LngLatBounds(); sel.l.g.forEach((p) => b.extend(p)); if (pos) b.extend([pos.lon, pos.lat])
    m.fitBounds(b, { padding: 40, duration: 500, maxZoom: 13 })
  }, [sel?.l.id]) // eslint-disable-line react-hooks/exhaustive-deps

  const end = sel && near ? A.tripEnd(clock, near.approachMin, sel.l.min) : null
  const late = !!(end && sunset && end > sunset)
  const setP = (k: A.ProfileId) => { setProf((p) => A.setProfileKind(p, k)); setPages(1); setSelId(null) }

  return (
    <div className="fixed inset-0 flex flex-col bg-background text-foreground md:flex-row-reverse">
      <div className="relative h-[40dvh] shrink-0 md:h-full md:flex-1"><div ref={el} className="h-full w-full" aria-label="Karte: zum Wählen des Standorts tippen" /></div>
      <aside className="flex min-h-0 flex-1 flex-col md:w-[420px] md:flex-none md:border-r" aria-label="Runde ab hier">
        <header className="flex items-center gap-2 border-b px-3 py-2">
          <Button asChild variant="ghost" size="icon" className="size-11"><a href="#" aria-label="Zurück zu allen Runden"><ArrowLeftIcon /></a></Button>
          <div className="min-w-0 flex-1"><h1 className="text-base font-semibold">Runde ab hier</h1><p className="truncate text-xs text-muted-foreground">Anfahrt zum nächsten Startpunkt + Runde ({idx?.window ? `${idx.window[0]}–${idx.window[1]}` : `~${idx?.minutes ?? 45}`} min)</p></div>
        </header>
        <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] [&>*]:shrink-0">
          {err && <Alert variant="destructive"><AlertTitle>Daten konnten nicht geladen werden</AlertTitle><AlertDescription>{err}</AlertDescription></Alert>}
          <div className="flex flex-wrap items-center gap-2">
            <Button className="h-11" onClick={locate} disabled={locBusy}>{locBusy ? <Spinner /> : <LocateFixedIcon data-icon="inline-start" />}Meinen Standort verwenden</Button>
            <span className="text-xs text-muted-foreground">oder auf die Karte tippen</span>
          </div>
          {outside && <Alert><AlertTitle>Außerhalb des Testgebiets</AlertTitle><AlertDescription>Vorberechnet ist bisher nur die Gegend um Miltenberg (Radius {idx?.radius_km} km). Gezeigt wird der nächste Startpunkt, die Anfahrt ist entsprechend lang.</AlertDescription></Alert>}
          {near && <div className="rounded-lg bg-muted px-3 py-2 text-sm">Start <strong>{near.point.name}</strong> · {near.km.toFixed(1)} km Luftlinie · Anfahrt ca. {near.approachMin} min <span className="text-muted-foreground">(geschätzt)</span></div>}
          <ToggleGroup type="single" variant="outline" value={prof.profile} onValueChange={(v) => v && setP(v as A.ProfileId)} aria-label="Fahrprofil" className="w-full">
            {A.PROFILES.map((p) => <ToggleGroupItem key={p.id} value={p.id} className="h-11 flex-1" title={p.hint}>{p.label}</ToggleGroupItem>)}
          </ToggleGroup>
          <p className="-mt-1 text-xs text-muted-foreground">{A.PROFILES.find((p) => p.id === prof.profile)?.hint}</p>
          <div className="grid gap-2 text-sm">
            {([["hideDriven", "Schon gefahrene ausblenden"], ["relaxQuality", "Mindestqualität lockern (auch Klasse C)"], ["lessOverlap", "Weniger Überschneidung mit gefahrenen Runden"]] as const).map(([k, l]) => (
              <div key={k} className="flex items-center justify-between gap-3"><Label htmlFor={k} className="font-normal">{l}</Label><Switch id={k} checked={f[k]} onCheckedChange={(c) => { setF((o) => ({ ...o, [k]: c })); setPages(1) }} /></div>))}
          </div>
          {busy && <div className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner />Runden werden geladen …</div>}
          {!pos && !busy && <p className="text-sm text-muted-foreground">Wähle deinen Standort, dann zeigen wir bis zu sechs Runden vom nächstgelegenen vorberechneten Startpunkt.</p>}
          {data && shown && (
            <>
              <div className="text-sm"><strong>{shown.list.length}</strong> von {shown.total} Runden · {data.valid} geprüfte Kandidaten</div>
              {shown.total === 0 && <p className="text-sm">Mit diesen Filtern bleibt keine Runde übrig.</p>}
              <div className="-mx-3 flex snap-x snap-mandatory gap-2 overflow-x-auto px-3 pb-1" role="list" aria-label="Runden-Karussell">
                {shown.list.map((s, i) => (
                  <div key={s.l.id} role="listitem" className="shrink-0 snap-start"><button type="button" onClick={() => { setSelId(s.l.id); setRev(false) }} aria-pressed={sel?.l.id === s.l.id}
                    className={`w-60 rounded-lg border p-3 text-left text-sm transition-colors hover:bg-accent focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none ${sel?.l.id === s.l.id ? "border-primary ring-2 ring-primary/30" : ""}`}>
                    <div className="flex items-center gap-1"><Badge variant="secondary">{i + 1}</Badge><Badge variant="outline">{s.l.kl}</Badge>{s.driven && <Badge><CheckIcon />gefahren</Badge>}</div>
                    <div className="mt-1 line-clamp-2 font-medium">{s.l.ti}</div>
                    <div className="text-xs text-muted-foreground tabular-nums">{s.l.min} min · {fmt(s.l.km, 1)} km · {fmt(s.l.hm)} Hm</div>
                    {s.e.why?.length ? <div className="mt-1 text-xs text-warning">Schwächer: {s.e.why.join(", ")}</div> : null}
                    {s.ovKnown > 0.2 && <div className="mt-1 text-xs text-muted-foreground">{Math.round(s.ovKnown * 100)} % schon bekannt</div>}
                  </button></div>))}
              </div>
              {shown.more && <Button variant="outline" className="h-11" onClick={() => setPages((p) => p + 1)}>Mehr Runden zeigen</Button>}
            </>
          )}
          {sel && near && (
            <section className="flex flex-col gap-2 rounded-lg border p-3 text-sm" aria-label="Gewählte Runde">
              <h2 className="font-semibold">{sel.l.ti}{rev && <span className="font-normal text-muted-foreground"> · umgekehrt</span>}</h2>
              <div className="tabular-nums">Anfahrt ca. {near.approachMin} min + Runde {sel.l.min} min + Rückweg ca. {near.approachMin} min</div>
              <div className={`flex items-center gap-1.5 ${late ? "text-warning" : ""}`}><SunsetIcon className="size-4" />Zurück ca. {A.hhmm(end!)} · Sonnenuntergang {sunset ? A.hhmm(sunset) : "–"}{late && " – Ende nach Sonnenuntergang"}</div>
              <div className="text-xs text-muted-foreground">Durch: {sel.l.orte.slice(0, 8).join(" · ")}</div>
              <div className="flex flex-wrap gap-2">
                {sel.l.gurl && <Button asChild className="h-11"><a href={sel.l.gurl} target="_blank" rel="noreferrer"><NavigationIcon data-icon="inline-start" />In Google Maps losfahren</a></Button>}
                <Button variant="outline" className="h-11" onClick={() => setRev((r) => !r)} aria-pressed={rev}><RepeatIcon data-icon="inline-start" />Umgekehrt fahren</Button>
                <Button variant="outline" className="h-11" onClick={() => { setProf((p) => A.toggleDriven(p, sel.l.id, rev)); toast(sel0!.driven ? "Markierung entfernt" : "Als gefahren gespeichert (nur auf diesem Gerät)") }}><CheckIcon data-icon="inline-start" />{sel0!.driven ? "Nicht gefahren" : "Schon gefahren"}</Button>
                <Button variant="outline" className="h-11" onClick={() => setReport(sel.l.id)}><FlagIcon data-icon="inline-start" />Melden</Button>
              </div>
              <p className="text-xs text-muted-foreground">Google Maps startet an deinem Standort und führt über die Wegpunkte der Runde zurück zum Startpunkt; die Strecke kann dort leicht abweichen.</p>
            </section>
          )}
          <footer className="mt-auto pt-2 text-[11px] leading-tight text-muted-foreground">Straßendaten © <a className="underline" href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap-Mitwirkende</a>, verfügbar unter der Open Database License (ODbL) · Karte: <a className="underline" href="https://openfreemap.org" target="_blank" rel="noreferrer">OpenFreeMap</a> / <a className="underline" href="https://www.openmaptiles.org/" target="_blank" rel="noreferrer">OpenMapTiles</a> · Höhen: Copernicus DEM</footer>
        </div>
      </aside>
      <Sheet open={!!report} onOpenChange={(o) => !o && setReport(null)}>
        <SheetContent side="bottom" className="rounded-t-2xl pb-[env(safe-area-inset-bottom)]">
          <SheetHeader><SheetTitle>Problem melden</SheetTitle><SheetDescription>Wird vorerst nur auf diesem Gerät gespeichert, es wird nichts gesendet.</SheetDescription></SheetHeader>
          <div className="flex flex-col gap-3 px-4 pb-4">
            <Select value={kind} onValueChange={setKind}><SelectTrigger className="h-11 w-full" aria-label="Art der Meldung"><SelectValue /></SelectTrigger><SelectContent>{A.REPORT_KINDS.map((k) => <SelectItem key={k} value={k}>{k}</SelectItem>)}</SelectContent></Select>
            <textarea className="min-h-24 rounded-md border bg-background p-2 text-sm" maxLength={500} placeholder="Was ist dir aufgefallen? (optional)" value={text} onChange={(e) => setText(e.target.value)} aria-label="Beschreibung" />
            <Button className="h-11" onClick={() => { setProf((p) => A.addReport(p, report!, kind, text)); setReport(null); setText(""); toast("Meldung lokal gespeichert") }}>Speichern</Button>
          </div>
        </SheetContent>
      </Sheet>
      <Toaster position="top-center" />
    </div>
  )
}
