import { act, renderHook } from "@testing-library/react"
import { beforeEach, describe, expect, it } from "vitest"
import { DEFAULT, useUrlState } from "./state"

describe("URL-Zustand (Hash)", () => {
  beforeEach(() => history.replaceState(null, "", "/"))
  it("Standard ohne Hash", () => { const { result } = renderHook(() => useUrlState()); expect(result.current[0]).toEqual(DEFAULT); expect(location.hash).toBe("") })
  it("liest Hash", () => {
    history.replaceState(null, "", "/#kl=AB&t=40-55&s=0.2&alle=1&lvl=3&r=9&sort=hm&ebenen=hp&rg=X|Y&v=5")
    const st = renderHook(() => useUrlState()).result.current[0]
    expect(st).toMatchObject({ kl: ["A", "B"], t: [40, 55], s: 0.2, rep: false, lvl: 3, sel: 9, sort: "hm", heat: true, refs: false, pauses: true, rg: ["X", "Y"], v: 5 })
  })
  it("begrenzt lvl und verwirft Unsinn", () => {
    history.replaceState(null, "", "/#lvl=9&kl=AZ&t=x")
    const st = renderHook(() => useUrlState()).result.current[0]; expect(st.lvl).toBe(4); expect(st.kl).toEqual(["A"]); expect(st.t).toEqual([45, 60])
  })
  it("schreibt nur Abweichungen in den Hash (Roundtrip)", () => {
    const { result } = renderHook(() => useUrlState())
    act(() => result.current[1]({ lvl: 3, sel: 42, rep: false, refs: true }))
    expect(location.hash).toBe("#alle=1&lvl=3&ebenen=r&r=42")
    act(() => result.current[1]({ ...DEFAULT })); expect(location.hash).toBe("")
  })
  it("reagiert auf hashchange", () => {
    const { result } = renderHook(() => useUrlState())
    act(() => { history.replaceState(null, "", "/#lvl=0"); window.dispatchEvent(new HashChangeEvent("hashchange")) })
    expect(result.current[0].lvl).toBe(0)
  })
})
