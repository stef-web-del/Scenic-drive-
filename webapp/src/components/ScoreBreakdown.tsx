import { TERMS } from "@/lib/terms"
import { InfoIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Switch } from "@/components/ui/switch"
import { Label } from "@/components/ui/label"
import { fmt, scoreBg, type Loop } from "@/lib/data"
import { BARS, CONTRIBS, SEG_KIND, bestSegs, metric, type ScoreBreak, type ScoreMeta } from "@/lib/score"

function Info({ title, text }: { title: string; text: string }) {
  return (
    <Popover>
      <PopoverTrigger asChild><Button variant="ghost" size="icon" className="size-8 shrink-0" aria-label={`Erklärung: ${title}`}><InfoIcon /></Button></PopoverTrigger>
      <PopoverContent className="w-72 text-sm"><p className="font-medium">{title}</p><p className="mt-1 text-muted-foreground">{text}</p></PopoverContent>
    </Popover>
  )
}

export function ScoreBreakdown({ loop, sb, sbm, seg, onSeg, segCol, onSegCol }: {
  loop: Loop; sb: ScoreBreak; sbm?: ScoreMeta; seg: number | null; onSeg: (i: number | null) => void; segCol: boolean; onSegCol: (v: boolean) => void
}) {
  const hl = sb.v.hl, tr = sb.v.tr, nm = Math.max(0, 1 - hl - tr)
  const max = Math.max(...CONTRIBS.map((c) => Math.abs(sb.c[c.k])))
  const best = bestSegs(sb.sg)
  return (
    <section aria-labelledby="h-sb" className="flex flex-col gap-3">
      <div className="flex items-center gap-2">
        <h3 id="h-sb" className="text-sm font-medium">{TERMS.why}</h3>
        <span className="ml-auto text-sm tabular-nums">Tour-Score <strong>{fmt(loop.t, 3)}</strong> · Klasse {loop.kl}</span>
      </div>

      <div className="flex flex-col gap-1.5" role="list" aria-label="Anteile am Tour-Score">
        {CONTRIBS.map((c) => {
          const v = sb.c[c.k], avg = sbm?.c[c.k]
          return (
            <div key={c.k} role="listitem" className="grid grid-cols-[8.75rem_1fr_3.25rem_2rem] items-center gap-2 text-sm">
              <span className="truncate">{c.label}</span>
              <div className="relative h-2.5 rounded-full bg-muted" aria-hidden>
                <div className={"absolute inset-y-0 left-0 rounded-full " + (v < 0 ? "bg-destructive" : "bg-primary")} style={{ width: `${(Math.abs(v) / max) * 100}%` }} />
                {avg != null && <div className="absolute inset-y-[-3px] w-0.5 bg-foreground/60" style={{ left: `${(Math.abs(avg) / max) * 100}%` }} title="Durchschnitt aller Runden" />}
              </div>
              <span className="text-right tabular-nums">{v >= 0 ? "+" : "−"}{fmt(Math.abs(v), 3)}</span>
              <Info title={c.label} text={c.help + (avg != null ? ` Durchschnitt aller ${sbm!.n} Runden: ${fmt(avg, 3)}.` : "")} />
            </div>
          )
        })}
        <p className="text-xs text-muted-foreground">Die Beiträge ergeben zusammen den Tour-Score. Strich = Durchschnitt aller Runden.</p>
      </div>

      <div className="flex flex-col gap-1">
        <div className="flex justify-between text-xs text-muted-foreground"><span className="flex items-center gap-1"><span className="size-2 rounded-full bg-primary" aria-hidden />Highlight {Math.round(hl * 100)} %</span><span>Normal {Math.round(nm * 100)} %</span><span>Verbindung {Math.round(tr * 100)} %</span></div>
        <div className="flex h-3 overflow-hidden rounded-full" role="img" aria-label={`Streckenanteile: Highlight ${Math.round(hl * 100)} %, normal ${Math.round(nm * 100)} %, Verbindung ${Math.round(tr * 100)} %`}>
          <div className="bg-primary" style={{ width: `${hl * 100}%` }} /><div className="bg-chart-2" style={{ width: `${nm * 100}%` }} /><div className="bg-muted-foreground/30" style={{ width: `${tr * 100}%` }} />
        </div>
      </div>

      <div className="flex flex-col gap-1.5" role="list" aria-label="Teilwerte im Vergleich">
        <p className="text-xs text-muted-foreground">Balken = besser als … % aller {sbm?.n ?? 342} Runden</p>
        {BARS.map((k) => {
          const m = metric(k), p = sb.p[k] ?? 0
          return (
            <div key={k} role="listitem" className="grid grid-cols-[8.75rem_1fr_4.5rem_2rem] items-center gap-2 text-sm">
              <span className="truncate">{m.label}</span>
              <div className="h-2.5 rounded-full bg-muted" aria-hidden><div className="h-full rounded-full bg-chart-2" style={{ width: `${Math.max(2, p)}%` }} /></div>
              <span className="text-right tabular-nums" aria-label={`${m.label}: ${m.fmt(sb.v[k])}, besser als ${p} Prozent`}>{p} %</span>
              <Info title={m.label} text={`${m.help} Diese Runde: ${m.fmt(sb.v[k])}${sbm ? `, Median aller Runden: ${m.fmt(sbm.med[k])}` : ""}.`} />
            </div>
          )
        })}
      </div>

      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2"><h4 className="text-sm font-medium">{TERMS.best}</h4>
          <div className="ml-auto flex items-center gap-2"><Switch id="segcol" checked={segCol} onCheckedChange={onSegCol} /><Label htmlFor="segcol" className="text-xs">{TERMS.segColor}</Label></div></div>
        <ul className="flex flex-col gap-1">
          {best.map(({ s, i }) => (
            <li key={i}>
              <button type="button" aria-pressed={seg === i} onClick={() => onSeg(seg === i ? null : i)}
                className={"flex min-h-11 w-full items-center gap-2 rounded-md border px-3 py-2 text-left text-sm transition-colors hover:bg-accent focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none " + (seg === i ? "border-primary bg-accent" : "")}>
                <span className={`size-2.5 shrink-0 rounded-full ${scoreBg(s[2])}`} aria-hidden />
                <span className="min-w-0 flex-1 truncate">{s[4] || "Ohne Namen"}</span>
                <span className="text-xs text-muted-foreground">{SEG_KIND[s[3]]}</span>
                <span className="tabular-nums text-muted-foreground">km {fmt(s[0], 1)} · {fmt(s[1] / 1000, 1)} km</span>
                <span className="w-10 text-right tabular-nums">{fmt(s[2], 2)}</span>
              </button>
            </li>
          ))}
        </ul>
        {segCol && <p className="text-xs text-muted-foreground">Karte: Abschnitte von gelb (schwach) bis violett (stark, Abschnitts-Score ≥ 0,40).</p>}
        {best.length === 0 && <p className="text-sm text-muted-foreground">Keine Abschnitte ab 1 km.</p>}
      </div>
    </section>
  )
}
