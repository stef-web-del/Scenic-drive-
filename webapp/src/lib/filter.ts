import type { Loop } from "./data"
import type { Sort, UiState } from "./state"

export const SORT_CMP: Record<Sort, (a: Loop, b: Loop) => number> = {
  score: (a, b) => b.s - a.s, tour: (a, b) => b.t - a.t, kurz: (a, b) => a.min - b.min, lang: (a, b) => b.min - a.min, hm: (a, b) => b.hm - a.hm,
}
export const sortLoops = (loops: Loop[], sort: Sort) => [...loops].sort(SORT_CMP[sort] ?? SORT_CMP.tour)

type FilterState = Pick<UiState, "kl" | "rg" | "t" | "s" | "rep" | "lvl">
/** Sichtbare Runden: Varianten einer Familie (varOf) oder gefilterte Liste (nur Familienvertreter, wenn rep). */
export function visibleLoops(loops: Loop[], st: FilterState, varOf: Loop | null): Loop[] {
  if (varOf) return loops.filter((l) => l.f[st.lvl] === varOf.f[st.lvl])
  return loops.filter((l) => st.kl.includes(l.kl) && (!st.rg || st.rg.includes(l.rg)) && l.min >= st.t[0] && l.min <= st.t[1] && l.s >= st.s - 1e-9 && (!st.rep || !!l.r[st.lvl]))
}
/** Familiengröße und bester Vertreter (Tour-Score) einer Runde auf Überschneidungsstufe lvl. */
export function familyInfo(sel: Loop | null, loops: Loop[], lvl: number): { n: number; rep: number | null } {
  if (!sel) return { n: 0, rep: null }
  const rep = loops.find((l) => l.f[lvl] === sel.f[lvl] && l.r[lvl])
  return { n: sel.fn[lvl], rep: rep?.id ?? null }
}
/** Regionen nach Anzahl Runden absteigend. */
export function sortRegions(loops: Loop[]): string[] {
  const n = new Map<string, number>(); for (const l of loops) n.set(l.rg, (n.get(l.rg) ?? 0) + 1)
  return [...n.keys()].sort((a, b) => n.get(b)! - n.get(a)!)
}
export const representatives = (loops: Loop[], lvl: number) => loops.filter((l) => l.r[lvl]).length
