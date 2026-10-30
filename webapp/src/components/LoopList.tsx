import { Badge } from "@/components/ui/badge"
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { RouteOffIcon } from "lucide-react"
import { strengthLabels } from "@/lib/score"
import { fmt, scoreBg, type Loop } from "@/lib/data"
import type { Sort } from "@/lib/state"
import { useState } from "react"
import { sortLoops } from "@/lib/filter"

const SORTS: { v: Sort; label: string }[] = [{ v: "score", label: "Bester Score" }, { v: "tour", label: "Bester Tour-Score" }, { v: "kurz", label: "Kürzeste Fahrzeit" }, { v: "lang", label: "Längste Fahrzeit" }, { v: "hm", label: "Meiste Höhenmeter" }]

export function SortSelect({ sort, onSort }: { sort: Sort; onSort: (s: Sort) => void }) {
  return (
    <Select value={sort} onValueChange={(v) => onSort(v as Sort)}>
      <SelectTrigger className="h-10 w-[190px]" aria-label="Sortierung"><SelectValue /></SelectTrigger>
      <SelectContent><SelectGroup>{SORTS.map((s) => <SelectItem key={s.v} value={s.v}>{s.label}</SelectItem>)}</SelectGroup></SelectContent>
    </Select>
  )
}

export function LoopList({ loops, sort, onPick, onReset }: { loops: Loop[]; sort: Sort; onPick: (id: number) => void; onReset: () => void }) {
  const [n, setN] = useState(40)
  if (!loops.length) return (
    <Empty className="border border-dashed">
      <EmptyHeader><EmptyMedia variant="icon"><RouteOffIcon /></EmptyMedia><EmptyTitle>Keine Runde passt</EmptyTitle>
        <EmptyDescription>Mit diesen Filtern bleibt keine Runde übrig. Lockere Fahrzeit, Score oder Klasse.</EmptyDescription></EmptyHeader>
      <EmptyContent><Button variant="outline" onClick={onReset}>Filter zurücksetzen</Button></EmptyContent>
    </Empty>
  )
  const sorted = sortLoops(loops, sort)
  return (
    <ul className="flex flex-col gap-2" aria-label="Runden">
      {sorted.slice(0, n).map((l) => (
        <li key={l.id}>
          <button type="button" onClick={() => onPick(l.id)} className="flex w-full flex-col gap-2 rounded-lg border bg-card p-3 text-left transition-colors hover:bg-accent focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
            <div className="flex items-center gap-2">
              <span className={`size-3 shrink-0 rounded-full ${scoreBg(l.s)}`} aria-hidden />
              <span className="min-w-0 truncate font-medium">{l.ti}</span>
              <Badge variant="secondary">Klasse {l.kl}</Badge>
              <span className="ml-auto shrink-0 text-sm tabular-nums">Tour {fmt(l.t, 2)}</span>
            </div>
            <div className="flex gap-4 text-sm tabular-nums text-muted-foreground">
              <span className="whitespace-nowrap">{fmt(l.km, 1)} km</span><span className="whitespace-nowrap">{l.min} min</span><span className="whitespace-nowrap">{fmt(l.hm)} Hm</span><span className="truncate">{l.rg.split(" (")[0]}</span>
            </div>
            {strengthLabels(l.st).length > 0 && <div className="flex flex-wrap gap-1">{strengthLabels(l.st).map((t) => <Badge key={t}>{t}</Badge>)}</div>}
            {l.tags.length > 0 && <div className="flex flex-wrap gap-1">{l.tags.slice(0, 4).map((t) => <Badge key={t} variant="outline">{t}</Badge>)}{l.tags.length > 4 && <Badge variant="outline">+{l.tags.length - 4}</Badge>}</div>}
          </button>
        </li>
      ))}
      {n < sorted.length && <li><Button variant="outline" className="h-11 w-full" onClick={() => setN(n + 60)}>Weitere anzeigen ({sorted.length - n})</Button></li>}
    </ul>
  )
}
