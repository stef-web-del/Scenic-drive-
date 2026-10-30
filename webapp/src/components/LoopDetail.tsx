import { TERMS } from "@/lib/terms"
import { lazy, Suspense, useMemo, useRef, useState } from "react"
import { Area, AreaChart, CartesianGrid, XAxis, YAxis } from "recharts"
import { ArrowLeftIcon, MaximizeIcon, DownloadIcon, Share2Icon, CoffeeIcon } from "lucide-react"
import { NavMenu } from "@/components/NavMenu"
import { pct } from "@/lib/nav"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { ScoreBreakdown } from "@/components/ScoreBreakdown"
import { strengthLabels } from "@/lib/score"
import type { ScoreMeta } from "@/lib/score"
import { fmt, gpx, kindLabel, scoreBg, type Detail, type Loop } from "@/lib/data"

// Vollbild-Karte lädt MapLibre erst beim Öffnen nach
const FullscreenMap = lazy(() => import("@/components/FullscreenMap"))

const cfg = { z: { label: "Höhe (m)", color: "var(--chart-2)" } } satisfies ChartConfig

function Stat({ label, value }: { label: string; value: string }) {
  return <div className="flex min-w-0 flex-col rounded-lg bg-muted px-3 py-2"><dt className="text-xs text-muted-foreground">{label}</dt><dd className="truncate text-base font-medium tabular-nums" title={value}>{value}</dd></div>
}

export function LoopDetail({ dark = false, loop, detail, error, start, levels, lvl, famSize, repId, onBack, onPick, onVariants, sbm, seg = null, onSeg = () => {}, segCol = false, onSegCol = () => {} }: {

  dark?: boolean; loop: Loop; detail: Detail | null; error: string | null; start: [number, number]; levels: number[]; lvl: number; famSize: number; repId: number | null
  onBack: () => void; onPick: (id: number) => void; onVariants: (id: number) => void
  sbm?: ScoreMeta; seg?: number | null; onSeg?: (i: number | null) => void; segCol?: boolean; onSegCol?: (v: boolean) => void
}) {
  const [full, setFull] = useState(false); const [fsMounted, setFsMounted] = useState(false); const fsBtn = useRef<HTMLButtonElement>(null)
  const data = useMemo(() => detail?.prof.map((z, i) => ({ km: (i * detail.step) / 1000, z })) ?? [], [detail])
  const share = async () => {
    const url = location.href
    try { if (navigator.share) await navigator.share({ title: loop.ti, url }); else { await navigator.clipboard.writeText(url); toast.success("Link kopiert") } } catch { /* abgebrochen */ }
  }
  const dl = () => {
    if (!detail) return
    // Anker muss im DOM hängen und die Blob-URL darf erst nach dem Klick freigegeben werden, sonst bricht der Download ab
    const a = document.createElement("a"); a.href = URL.createObjectURL(new Blob([gpx(loop, detail)], { type: "application/gpx+xml" })); a.download = `runde-${loop.id}.gpx`
    document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 1000)
  }
  return (
    <article className="flex flex-col gap-4" aria-labelledby="det-title">
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="icon" className="size-11" onClick={onBack} aria-label="Zurück zur Liste"><ArrowLeftIcon /></Button>
        <span className={`size-3 rounded-full ${scoreBg(loop.s)}`} aria-hidden />
        <h2 id="det-title" className="min-w-0 flex-1 text-lg leading-tight font-semibold"><span className="block truncate">{loop.ti}</span><span className="block text-xs font-normal text-muted-foreground">Runde {loop.id}</span></h2>
        <Badge variant="secondary">Klasse {loop.kl}</Badge>
        <Button ref={fsBtn} variant="secondary" size="icon" className="size-12 rounded-full" disabled={!detail?.g?.length} onClick={() => { setFsMounted(true); setFull(true) }} aria-label="Karte im Vollbild" title="Karte im Vollbild"><MaximizeIcon /></Button>
      </div>
      {fsMounted && detail?.g && <Suspense fallback={null}><FullscreenMap open={full} onOpenChange={setFull} dark={dark} geom={detail.g} title={loop.ti} returnFocus={fsBtn} /></Suspense>}
      <dl className="grid grid-cols-3 gap-2">
        <Stat label={TERMS.length} value={`${fmt(loop.km, 1)} km`} /><Stat label={TERMS.time} value={`${loop.min} min`} /><Stat label={TERMS.climb} value={`${fmt(loop.hm)} Hm`} />
        <Stat label={TERMS.score} value={fmt(loop.s, 3)} /><Stat label={TERMS.tour} value={fmt(loop.t, 3)} /><Stat label={TERMS.region} value={loop.rg.split(" (")[0]} />
      </dl>
      {strengthLabels(loop.st).length > 0 && <div className="flex flex-wrap gap-1" aria-label="Stärken">{strengthLabels(loop.st).map((t) => <Badge key={t}>{t}</Badge>)}</div>}
      {loop.tags.length > 0 && <div className="flex flex-wrap gap-1">{loop.tags.map((t) => <Badge key={t} variant="outline">{t}</Badge>)}</div>}
      {detail?.tx ? <p className="text-sm leading-relaxed">{detail.tx}</p> : !detail && <div className="flex flex-col gap-1.5"><Skeleton className="h-4 w-full" /><Skeleton className="h-4 w-4/5" /></div>}
      <div className="grid grid-cols-2 gap-2">
        <NavMenu nav={detail?.nav} start={start} disabled={!detail} geom={detail?.g} />
        <Button className="h-11" variant="outline" onClick={dl} disabled={!detail}><DownloadIcon data-icon="inline-start" />GPX</Button>
        <Button className="col-span-2 h-11" variant="outline" onClick={share}><Share2Icon data-icon="inline-start" />Link teilen</Button>
      </div>
      {detail?.nav && <p className="text-xs text-muted-foreground">Navi-Übergabe ca. {pct(Math.max(detail.nav.g.f, detail.nav.g.st?.f ?? 0))} originalgetreu (Google) · {pct(Math.max(detail.nav.a.f, detail.nav.a.st?.f ?? 0))} (Apple). Für die exakte Strecke GPX nutzen.</p>}
      <Separator />
      {detail?.sb ? <ScoreBreakdown loop={loop} sb={detail.sb} sbm={sbm} seg={seg} onSeg={onSeg} segCol={segCol} onSegCol={onSegCol} /> : !detail && !error && <Skeleton className="h-64 w-full" />}
      <Separator />
      <section aria-labelledby="h-prof" className="flex flex-col gap-2">
        <h3 id="h-prof" className="text-sm font-medium">{TERMS.profile}</h3>
        {error ? <p className="text-sm text-destructive">Details konnten nicht geladen werden: {error}</p> : !detail ? <Skeleton className="h-40 w-full" /> : (
          <ChartContainer config={cfg} className="aspect-auto h-40 w-full">
            <AreaChart data={data} margin={{ left: 0, right: 8, top: 8, bottom: 0 }}>
              <CartesianGrid vertical={false} />
              <XAxis dataKey="km" type="number" domain={[0, "dataMax"]} tickFormatter={(v) => `${Math.round(v)} km`} tickLine={false} axisLine={false} />
              <YAxis width={44} domain={["dataMin - 20", "dataMax + 20"]} tickFormatter={(v) => `${Math.round(v)}`} tickLine={false} axisLine={false} />
              <ChartTooltip content={<ChartTooltipContent labelFormatter={(_, p) => `km ${fmt(p?.[0]?.payload?.km ?? 0, 1)}`} />} />
              <Area dataKey="z" type="monotone" stroke="var(--color-z)" fill="var(--color-z)" fillOpacity={0.25} strokeWidth={2} isAnimationActive={false} />
            </AreaChart>
          </ChartContainer>
        )}
      </section>
      <section aria-labelledby="h-p" className="flex flex-col gap-2">
        <h3 id="h-p" className="text-sm font-medium">{TERMS.pauses}</h3>
        {!detail ? <Skeleton className="h-12 w-full" /> : detail.pauses.length ? (
          <ul className="flex flex-col gap-1">{detail.pauses.map((p) => (
            <li key={p.n + p.km} className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm"><CoffeeIcon className="text-muted-foreground" aria-hidden /><span className="truncate">{p.n}</span><span className="text-muted-foreground">{kindLabel(p.k)}</span><span className="ml-auto tabular-nums text-muted-foreground">km {fmt(p.km, 1)}</span></li>
          ))}</ul>
        ) : <p className="text-sm text-muted-foreground">Keine Einkehr direkt an der Strecke.</p>}
      </section>
      {detail && detail.orte.length > 0 && <section className="flex flex-col gap-1"><h3 className="text-sm font-medium">{TERMS.places}</h3><p className="text-sm text-muted-foreground">{detail.orte.join(" · ")}</p></section>}
      <section className="flex flex-col gap-1 rounded-lg bg-muted p-3 text-sm">
        <span>Familie bei {Math.round(levels[lvl] * 100)} % Überschneidung: {famSize} {famSize === 1 ? "Runde" : "Runden"}.</span>
        {repId === loop.id ? <span className="text-muted-foreground">Beste Runde dieser Familie (Tour-Score).</span> : repId != null && <Button variant="link" className="h-auto justify-start p-0" onClick={() => onPick(repId)}>Zur besten Runde der Familie</Button>}
        {famSize > 1 && <Button variant="outline" size="sm" className="mt-1 h-10 self-start" onClick={() => onVariants(loop.id)}>Varianten dieser Runde anzeigen ({famSize})</Button>}
      </section>
    </article>
  )
}
