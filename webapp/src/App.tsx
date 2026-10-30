import { useEffect, useMemo, useState, useSyncExternalStore } from "react"
import { Drawer as Vaul } from "vaul"
import { LayersIcon, MoonIcon, RouteIcon, SlidersHorizontalIcon, SunIcon } from "lucide-react"
import { Toaster } from "@/components/ui/sonner"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { DropdownMenu, DropdownMenuCheckboxItem, DropdownMenuContent, DropdownMenuGroup, DropdownMenuLabel, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { useTheme } from "@/components/theme-provider"
import { lazy, Suspense } from "react"
const MapView = lazy(() => import("@/components/MapView").then((m) => ({ default: m.MapView })))
import { Filters } from "@/components/Filters"
import { LoopList, SortSelect } from "@/components/LoopList"
const LoopDetail = lazy(() => import("@/components/LoopDetail").then((m) => ({ default: m.LoopDetail })))
import { fmt, loadAll, loadDetail, loadLayers, loadLines, loadMeta, type Detail, type Layers, type Lines, type Loop, type Meta } from "@/lib/data"
import { toast } from "sonner"
import { DEFAULT, useUrlState } from "@/lib/state"
import { familyInfo, sortRegions, visibleLoops } from "@/lib/filter"

const mq = window.matchMedia("(min-width: 768px)")
const useDesktop = () => useSyncExternalStore((cb) => { mq.addEventListener("change", cb); return () => mq.removeEventListener("change", cb) }, () => mq.matches)
const SNAPS = ["148px", 0.5, 0.92] as const

function Legend({ className }: { className?: string }) {
  return (
    <div className={className} aria-label="Legende">
      <div className="text-xs font-medium">Runden-Score</div>
      <div className="mt-1 h-2 w-40 rounded-full bg-linear-to-r from-score-0 via-score-2 to-score-3" />
      <div className="mt-0.5 flex w-40 justify-between text-[11px] tabular-nums text-muted-foreground"><span>0,15</span><span>0,18</span><span>0,21</span><span>0,24</span></div>
      <div className="mt-1 flex items-center gap-1.5 text-[11px] text-muted-foreground"><span className="inline-block h-1 w-5 rounded bg-primary" aria-hidden /><span className="whitespace-nowrap">gewählte Runde</span><span className="ml-2 inline-block size-2.5 rounded-full bg-primary ring-2 ring-background" aria-hidden /> Start</div>
    </div>
  )
}

export default function App() {
  const [st, set] = useUrlState()
  const desktop = useDesktop()
  const { theme, setTheme } = useTheme()
  const dark = theme === "dark" || (theme === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches)
  const [meta, setMeta] = useState<Meta | null>(null); const [lines, setLines] = useState<Lines | null>(null); const [layers, setLayers] = useState<Layers | null>(null)
  const [err, setErr] = useState<string | null>(null)
  // Detail und Fehler tragen die Runden-ID; Werte einer anderen Auswahl gelten als leer (kein Zurücksetzen im Effekt nötig).
  const [detailS, setDetail] = useState<Detail | null>(null); const [detErrS, setDetErr] = useState<{ id: number; msg: string } | null>(null)
  const detail = st.sel != null && detailS?.id === st.sel ? detailS : null
  const detErr = st.sel != null && detErrS?.id === st.sel ? detErrS.msg : null
  const [filterOpen, setFilterOpen] = useState(false); const [snap, setSnap] = useState<number | string | null>(SNAPS[0])
  const [tab, setTab] = useState("runden")
  // Gewählter Abschnitt gilt nur für die Runde, in der er gewählt wurde.
  const [segS, setSegS] = useState<{ sel: number | null; seg: number | null }>({ sel: null, seg: null }); const [segCol, setSegCol] = useState(false)
  const seg = segS.sel === st.sel ? segS.seg : null
  const setSeg = (v: number | null) => setSegS({ sel: st.sel, seg: v })

  // Karte erst nach dem ersten Zeichnen der Liste initialisieren (MapLibre ist ~300 KB gzip und WebGL-lastig)
  const [mapOn, setMapOn] = useState(false)
  // Direktlink auf eine Runde: Karte sofort (Zustandsanpassung beim Rendern statt im Effekt)
  if (meta && !mapOn && st.sel != null) setMapOn(true)
  useEffect(() => {
    if (!meta || mapOn || st.sel != null) return
    // Mobil: Karte erst bei erster Interaktion (statisches Vorschaubild bis dahin); Desktop: wenn Browser idle
    const on = () => setMapOn(true)
    window.addEventListener("pointerdown", on, { once: true })
    window.addEventListener("keydown", on, { once: true })
    let id: number | undefined
    if (window.matchMedia("(min-width: 768px)").matches) {
      const ric = window.requestIdleCallback ?? ((f: () => void) => setTimeout(f, 200))
      id = ric(on, { timeout: 1200 }) as number
    }
    return () => {
      window.removeEventListener("pointerdown", on); window.removeEventListener("keydown", on)
      if (id !== undefined) (window.cancelIdleCallback ?? clearTimeout)(id)
    }
  }, [meta, mapOn, st.sel])
  useEffect(() => { loadMeta().then(setMeta).catch((e) => setErr(String(e))); loadLines().then(setLines).catch((e) => setErr(String(e))) }, [])
  // Alle 342 Runden nur bei Bedarf: Expertenmodus, Varianten einer Familie oder Direktlink auf eine Nicht-Vertreter-Runde
  const [all, setAll] = useState<{ loops: Loop[]; lines: Lines } | null>(null); const [allFailed, setAllFailed] = useState(false)
  const needAll = !!meta && (!st.rep || st.v != null || (st.sel != null && !meta.loops.some((l) => l.id === st.sel)))
  const allBusy = needAll && !all && !allFailed
  useEffect(() => { if (allBusy) loadAll().then(setAll).catch((e) => { setAllFailed(true); toast.error("Weitere Runden nicht geladen: " + e) }) }, [allBusy])
  const loops = useMemo(() => meta ? (all ? [...meta.loops, ...all.loops] : meta.loops) : [], [meta, all])
  const allLines = useMemo(() => lines && all ? { ...lines, ...all.lines } : lines, [lines, all])
  const wantLayers = st.heat || st.refs || st.pauses
  useEffect(() => {
    if (layers) return
    if (wantLayers) { loadLayers().then(setLayers).catch(() => {}); return }
    const t = setTimeout(() => loadLayers().then(setLayers).catch(() => {}), 4000); return () => clearTimeout(t)
  }, [wantLayers, layers])

  const regions = useMemo(() => sortRegions(loops), [loops])
  const varOf = useMemo(() => st.v != null ? loops.find((l) => l.id === st.v) ?? null : null, [loops, st.v])
  const visible = useMemo(() => visibleLoops(loops, { kl: st.kl, rg: st.rg, t: st.t, s: st.s, rep: st.rep, lvl: st.lvl }, varOf), [loops, varOf, st.kl, st.rg, st.t, st.s, st.rep, st.lvl])
  const sel = useMemo(() => loops.find((l) => l.id === st.sel) ?? null, [loops, st.sel])
  const fam = useMemo(() => familyInfo(sel, loops, st.lvl), [sel, loops, st.lvl])

  useEffect(() => {
    if (st.sel == null) return
    const id = st.sel; let ok = true
    loadDetail(id).then((d) => ok && setDetail(d)).catch((e) => ok && setDetErr({ id, msg: String(e) }))
    return () => { ok = false }
  }, [st.sel])

  const pick = (id: number | null) => { set({ sel: id }); if (id && !desktop) setSnap(SNAPS[1]); if (id) setTab("runden") }
  const reset = () => set({ ...DEFAULT, heat: st.heat, refs: st.refs, pauses: st.pauses, sort: st.sort })
  const sheetH = typeof snap === "number" ? snap * window.innerHeight : 148
  const padding = desktop ? { top: 0, bottom: 0, left: 0, right: 0 } : { top: 72, bottom: Math.min(sheetH, window.innerHeight * 0.5), left: 0, right: 0 }

  const layerMenu = (
    <DropdownMenu>
      <DropdownMenuTrigger asChild><Button variant="secondary" className="h-11 shadow-md max-md:w-11 max-md:px-0" title="Kartenebenen"><LayersIcon data-icon="inline-start" /><span className="max-md:sr-only">Ebenen</span></Button></DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Zusätzliche Ebenen</DropdownMenuLabel>
          <DropdownMenuCheckboxItem className="min-h-11" checked={st.heat} onCheckedChange={(c) => set({ heat: c })} onSelect={(e) => e.preventDefault()}>Straßenscore-Heatmap</DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem className="min-h-11" checked={st.refs} onCheckedChange={(c) => set({ refs: c })} onSelect={(e) => e.preventDefault()}>Referenzstrecken</DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem className="min-h-11" checked={st.pauses} onCheckedChange={(c) => set({ pauses: c })} onSelect={(e) => e.preventDefault()}>Pausenorte</DropdownMenuCheckboxItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
  const themeBtn = <Button variant="secondary" size="icon" className="size-11 shadow-md" onClick={() => setTheme(dark ? "light" : "dark")} aria-label={dark ? "Helles Design" : "Dunkles Design"}>{dark ? <SunIcon /> : <MoonIcon />}</Button>
  const ov = meta ? Math.round(meta.levels[st.lvl] * 100) : 25
  const count = !meta ? <Skeleton className="h-4 w-32" /> : varOf ? <span className="tabular-nums"><strong>{fmt(visible.length)}</strong> Varianten · {ov} % Überschneidung</span>
    : st.rep ? <span className="tabular-nums"><strong>{fmt(visible.length)}</strong> {visible.length === 1 ? "Runde" : "Runden"} · max. {ov} % Überschneidung</span>
    : <span className="tabular-nums">Experten: <strong>{fmt(visible.length)}</strong> von {fmt(meta.total)} Runden</span>

  const listOrDetail = err ? (
    <Alert variant="destructive"><AlertTitle>Daten konnten nicht geladen werden</AlertTitle><AlertDescription>{err}. Bitte Seite neu laden.</AlertDescription></Alert>
  ) : !meta ? (
    <div className="flex flex-col gap-2">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-24 w-full" />)}</div>
  ) : sel ? (
    <Suspense fallback={<Skeleton className="h-96 w-full" />}><LoopDetail dark={dark} onVariants={(id) => { set({ v: id, sel: null }); toast("Varianten werden angezeigt") }} loop={sel} detail={detail} error={detErr} start={meta.start} levels={meta.levels} lvl={st.lvl} famSize={fam.n} repId={fam.rep} sbm={meta.sbm} seg={seg} onSeg={setSeg} segCol={segCol} onSegCol={setSegCol} onBack={() => set({ sel: null })} onPick={pick} /></Suspense>
  ) : (
    <div className="flex flex-col gap-3">
      {/* W07: Einstieg „Runde ab hier“ in der Liste; der einzige Standort-Knopf ist der auf der Karte. */}
      <Button asChild variant="outline" className="h-11 w-full justify-start"><a href="#ab-hier"><RouteIcon data-icon="inline-start" />Runde ab hier: Start in deiner Nähe</a></Button>
      <div className="flex items-center justify-between gap-2 text-sm">{count}<SortSelect sort={st.sort} onSort={(s) => set({ sort: s })} /></div>
      {varOf && <div className="flex items-center gap-2 rounded-lg bg-muted px-3 py-2 text-sm"><span className="min-w-0 flex-1 truncate">Varianten von <strong>{varOf.ti}</strong></span><Button variant="outline" size="sm" className="h-9" onClick={() => set({ v: null })}>Zurück</Button></div>}
      {!st.rep && !varOf && <div className="flex items-center gap-2 rounded-lg bg-muted px-3 py-2 text-sm"><span className="min-w-0 flex-1">Alle Varianten werden angezeigt.</span><Button variant="outline" size="sm" className="h-9" onClick={() => set({ rep: true })}>Nur beste</Button></div>}
      {allBusy && <div className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner />Weitere Runden werden geladen …</div>}
      <LoopList loops={visible} sort={st.sort} onPick={pick} onReset={reset} />
    </div>
  )
  const filters = meta ? <Filters st={st} set={set} loops={loops} total={meta.total} regions={regions} levels={meta.levels} /> : <Skeleton className="h-64 w-full" />

  return (
    <div className="fixed inset-0 flex bg-background text-foreground">
      {desktop && (
        <aside className="flex w-[400px] shrink-0 flex-col border-r" aria-label="Runden und Filter">
          <header className="flex items-start gap-2 px-4 pt-4 pb-2">
            <div className="flex-1"><h1 className="text-lg font-semibold">Scenic Routes Miltenberg</h1><p className="text-sm text-muted-foreground">Kurvige Runden ab OBI Miltenberg · 45–60 min</p></div>
          </header>
          <Tabs value={tab} onValueChange={setTab} className="min-h-0 flex-1 gap-0">
            <TabsList className="mx-4 grid grid-cols-2"><TabsTrigger value="runden">{sel ? "Runde" : "Runden"}</TabsTrigger><TabsTrigger value="filter">Filter</TabsTrigger></TabsList>
            <TabsContent value="runden" className="min-h-0 flex-1 overflow-y-auto p-4">{listOrDetail}</TabsContent>
            <TabsContent value="filter" className="min-h-0 flex-1 overflow-y-auto p-4">{filters}</TabsContent>
          </Tabs>
        </aside>
      )}
      <main className="relative min-w-0 flex-1" onPointerDown={() => meta && setMapOn(true)}>
        <h1 className="sr-only">{desktop ? "Karte" : "Scenic Routes Miltenberg"}</h1>
        <img src={dark ? "/preview-dark.webp" : "/preview-light.webp"} alt="" aria-hidden fetchPriority="high" decoding="async" className="pointer-events-none absolute inset-0 h-full w-full object-none" onPointerDown={() => setMapOn(true)} />
        {!mapOn && <div className="absolute bottom-[92px] right-1 z-[1] md:bottom-1 max-w-[90%] rounded bg-background/80 px-1.5 py-0.5 text-[10px] leading-tight text-muted-foreground">© <a className="underline" href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap-Mitwirkende</a> · <a className="underline" href="https://openfreemap.org" target="_blank" rel="noreferrer">OpenFreeMap</a> · <a className="underline" href="https://www.openmaptiles.org/" target="_blank" rel="noreferrer">OpenMapTiles</a> · Höhen: Copernicus DEM</div>}
        {meta && mapOn && <Suspense fallback={null}><MapView dark={dark} start={meta.start} loops={meta.loops} lines={allLines} layers={layers} visible={visible} sel={sel} detail={detail} show={{ heat: st.heat, refs: st.refs, pauses: st.pauses }} padding={padding} onPick={pick} seg={seg} segCol={segCol} /></Suspense>}
        {(!meta || !lines) && !err && <div className="absolute inset-x-0 top-1/3 flex justify-center"><Badge variant="secondary" className="gap-2 px-3 py-1.5 text-sm shadow"><Spinner />Runden werden geladen …</Badge></div>}
        <div className={"pointer-events-none absolute inset-x-0 top-0 flex items-start gap-2 p-3 pt-[max(0.75rem,env(safe-area-inset-top))] " + (desktop ? "pr-15" : "pr-3")}>
          {!desktop && <div className="pointer-events-auto flex min-w-0 flex-1 flex-col rounded-lg bg-background/95 px-3 py-1.5 shadow-md"><span className="truncate text-sm font-semibold">Scenic Routes Miltenberg</span><span className="text-xs text-muted-foreground">{count}</span></div>}
          {desktop && <div className="flex-1" />}
          <div className="pointer-events-auto flex gap-2">
            {!desktop && <Button variant="secondary" className="h-11 shadow-md" onClick={() => setFilterOpen(true)} aria-label="Filter öffnen"><SlidersHorizontalIcon data-icon="inline-start" />Filter</Button>}
            {layerMenu}{themeBtn}
          </div>
        </div>
        <Legend className={desktop ? "absolute bottom-8 left-3 rounded-lg bg-background/95 p-3 shadow-md" : "absolute left-3 top-[calc(env(safe-area-inset-top)+4.5rem)] w-44 rounded-lg bg-background/90 px-2.5 py-1.5 text-xs shadow-md"} />
      </main>
      {!desktop && (
        <>
          <Vaul.Root open modal={false} dismissible={false} snapPoints={[...SNAPS]} activeSnapPoint={snap} setActiveSnapPoint={setSnap}>
            <Vaul.Portal>
              {/* Höhe = 100dvh: vaul verschiebt um (1 − Snap) × Fensterhöhe; 8dvh Polster, weil bei Snap 0,92 der untere Rand außerhalb liegt */}
              <Vaul.Content className="fixed inset-x-0 bottom-0 z-40 flex h-dvh flex-col rounded-t-2xl border-t bg-background shadow-[0_-4px_16px_rgba(0,0,0,0.12)] outline-none" aria-describedby={undefined}>
                <div className="mx-auto mt-2.5 h-1.5 w-12 shrink-0 rounded-full bg-muted-foreground/40" aria-hidden />
                <Vaul.Title className="sr-only">{sel ? `Runde ${sel.id}` : "Rundenliste"}</Vaul.Title>
                <div className={(snap === SNAPS[2] ? "overflow-y-auto" : "overflow-hidden") + " flex-1 px-4 pt-3 pb-[calc(8dvh+max(1rem,env(safe-area-inset-bottom)))]"}>{listOrDetail}</div>
              </Vaul.Content>
            </Vaul.Portal>
          </Vaul.Root>
          <Sheet open={filterOpen} onOpenChange={setFilterOpen}>
            <SheetContent side="bottom" className="max-h-[88dvh] overflow-y-auto rounded-t-2xl pb-[env(safe-area-inset-bottom)]">
              <SheetHeader><SheetTitle>Filter</SheetTitle><SheetDescription>{count}</SheetDescription></SheetHeader>
              <div className="px-4 pb-4">{filters}</div>
              <div className="sticky bottom-0 bg-background p-4 pt-0"><Button className="h-11 w-full" onClick={() => setFilterOpen(false)}>{meta ? `${fmt(visible.length)} Runden anzeigen` : "Anzeigen"}</Button></div>
            </SheetContent>
          </Sheet>
        </>
      )}
      <Toaster position="top-center" mobileOffset={{ top: "calc(env(safe-area-inset-top) + 6rem)" }} />
    </div>
  )
}
