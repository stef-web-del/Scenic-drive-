import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldContent, FieldDescription, FieldGroup, FieldLabel, FieldLegend, FieldSet } from "@/components/ui/field"
import { Slider } from "@/components/ui/slider"
import { Switch } from "@/components/ui/switch"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { Button } from "@/components/ui/button"
import { KLASSEN, fmt, type Klasse, type Loop } from "@/lib/data"
import { DEFAULT, type UiState } from "@/lib/state"

export function Filters({ st, set, loops, total, regions, levels }: { st: UiState; set: (p: Partial<UiState>) => void; loops: Loop[]; total: number; regions: string[]; levels: number[] }) {
  const rg = st.rg ?? regions
  const nFam = loops.filter((l) => l.r[st.lvl]).length
  return (
    <FieldGroup className="gap-6">
      <FieldSet>
        <FieldLegend variant="label">Qualitätsklasse</FieldLegend>
        <ToggleGroup type="multiple" variant="outline" className="w-full" value={st.kl} onValueChange={(v) => set({ kl: v as Klasse[] })} aria-label="Qualitätsklasse">
          {KLASSEN.map((k) => (
            <ToggleGroupItem key={k.v} value={k.v} className="h-11 flex-1 flex-col gap-0 leading-tight" title={`${k.label}, Score ${k.range}`}>
              <span className="font-medium"><span className="sr-only">{"Klasse\u00a0"}</span>{k.v}</span><span className="text-[11px] text-muted-foreground">{k.range}</span>
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
      </FieldSet>
      <FieldSet>
        <FieldLegend variant="label">Region</FieldLegend>
        <FieldGroup className="gap-1" data-slot="checkbox-group">
          {regions.map((r) => {
            const id = "rg-" + r.replace(/\W+/g, "")
            return (
              <Field key={r} orientation="horizontal" className="min-h-11 items-center">
                <Checkbox id={id} checked={rg.includes(r)} onCheckedChange={(c) => { const n = c ? [...rg, r] : rg.filter((x) => x !== r); set({ rg: n.length === regions.length ? null : n }) }} />
                <FieldLabel htmlFor={id} className="font-normal">{r} <span className="text-muted-foreground">({loops.filter((l) => l.rg === r).length})</span></FieldLabel>
              </Field>
            )
          })}
        </FieldGroup>
      </FieldSet>
      <Field>
        <div className="flex items-center justify-between"><FieldLabel id="lbl-t">Fahrzeit</FieldLabel><span className="text-sm tabular-nums text-muted-foreground">{st.t[0]}–{st.t[1]} min</span></div>
        <Slider aria-labelledby="lbl-t" thumbLabels={["Fahrzeit von (min)", "Fahrzeit bis (min)"]} min={45} max={60} step={1} minStepsBetweenThumbs={1} value={st.t} onValueChange={(v) => set({ t: [v[0], v[1]] })} className="py-3" />
      </Field>
      <Field>
        <div className="flex items-center justify-between"><FieldLabel id="lbl-s">Mindest-Score (Straßenqualität)</FieldLabel><span className="text-sm tabular-nums text-muted-foreground">ab {fmt(st.s, 3)}</span></div>
        <Slider aria-labelledby="lbl-s" thumbLabels={["Mindest-Score"]} min={0.15} max={0.24} step={0.005} value={[st.s]} onValueChange={(v) => set({ s: v[0] })} className="py-3" />
      </Field>
      <FieldSet>
        <FieldLegend variant="label">Ähnliche Runden zusammenfassen</FieldLegend>
        <Field>
          <FieldLabel id="lbl-lvl">Max. Überschneidung</FieldLabel>
          <ToggleGroup type="single" variant="outline" className="w-full" value={String(st.lvl)} onValueChange={(v) => v && set({ lvl: Number(v) })} aria-labelledby="lbl-lvl">
            {levels.map((t, i) => <ToggleGroupItem key={t} value={String(i)} className="h-11 flex-1">{Math.round(t * 100)} %</ToggleGroupItem>)}
          </ToggleGroup>
          <FieldDescription>Längengewichteter Jaccard. Runden, die sich stärker überschneiden, zählen zur Familie der besseren Runde. Standard 25 %. Angezeigt wird je Familie die Runde mit dem besten Tour-Score: aktuell {nFam} Runden.</FieldDescription>
        </Field>
        <Field orientation="horizontal" className="min-h-11 items-center">
          <FieldContent>
            <FieldLabel htmlFor="alle">Experten: alle Runden</FieldLabel>
            <FieldDescription>Lädt alle {fmt(total)} Varianten nach (ca. 0,2 MB).</FieldDescription>
          </FieldContent>
          <Switch id="alle" checked={!st.rep} onCheckedChange={(c) => set({ rep: !c, v: null })} />
        </Field>
      </FieldSet>
      <Button variant="outline" className="h-11" onClick={() => set({ ...DEFAULT, sel: st.sel, v: null, heat: st.heat, refs: st.refs, pauses: st.pauses, sort: st.sort })}>Filter zurücksetzen</Button>
    </FieldGroup>
  )
}
