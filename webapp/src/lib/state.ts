import { useCallback, useEffect, useState } from "react"
import type { Klasse } from "./data"
export type Sort = "score" | "tour" | "kurz" | "lang" | "hm"
export interface UiState {
  kl: Klasse[]; rg: string[] | null; t: [number, number]; s: number; rep: boolean; lvl: number
  sel: number | null; v: number | null; sort: Sort; heat: boolean; refs: boolean; pauses: boolean
}
export const DEFAULT: UiState = { kl: ["A", "B", "C"], rg: null, t: [45, 60], s: 0.15, rep: true, lvl: 1, sel: null, v: null, sort: "tour", heat: false, refs: false, pauses: false }
function parse(): UiState {
  const q = new URLSearchParams(location.hash.slice(1)); const st = { ...DEFAULT }
  if (q.has("kl")) st.kl = q.get("kl")!.split("").filter((c) => "ABC".includes(c)) as Klasse[]
  if (q.has("rg")) st.rg = q.get("rg")!.split("|").filter(Boolean)
  if (q.has("t")) { const [a, b] = q.get("t")!.split("-").map(Number); if (a && b) st.t = [a, b] }
  if (q.has("s")) st.s = Number(q.get("s")) || DEFAULT.s
  if (q.has("alle")) st.rep = q.get("alle") !== "1"
  if (q.has("v")) st.v = Number(q.get("v")) || null
  if (q.has("lvl")) st.lvl = Math.min(4, Math.max(0, Number(q.get("lvl"))))
  if (q.has("r")) st.sel = Number(q.get("r")) || null
  if (q.has("sort")) st.sort = q.get("sort") as Sort
  const ly = q.get("ebenen") ?? ""; st.heat = ly.includes("h"); st.refs = ly.includes("r"); st.pauses = ly.includes("p")
  return st
}
function serialize(s: UiState) {
  const q = new URLSearchParams()
  if (s.kl.join("") !== "ABC") q.set("kl", s.kl.join(""))
  if (s.rg) q.set("rg", s.rg.join("|"))
  if (s.t[0] !== 45 || s.t[1] !== 60) q.set("t", s.t.join("-"))
  if (s.s !== DEFAULT.s) q.set("s", String(s.s))
  if (!s.rep) q.set("alle", "1")
  if (s.v) q.set("v", String(s.v))
  if (s.lvl !== 1) q.set("lvl", String(s.lvl))
  if (s.sort !== DEFAULT.sort) q.set("sort", s.sort)
  const ly = (s.heat ? "h" : "") + (s.refs ? "r" : "") + (s.pauses ? "p" : ""); if (ly) q.set("ebenen", ly)
  if (s.sel) q.set("r", String(s.sel))
  return q.toString()
}
export function useUrlState() {
  const [st, setSt] = useState<UiState>(parse)
  useEffect(() => { const h = serialize(st); history.replaceState(null, "", h ? "#" + h : location.pathname) }, [st])
  useEffect(() => { const f = () => setSt(parse()); addEventListener("hashchange", f); return () => removeEventListener("hashchange", f) }, [])
  const set = useCallback((p: Partial<UiState>) => setSt((s) => ({ ...s, ...p })), [])
  return [st, set] as const
}
