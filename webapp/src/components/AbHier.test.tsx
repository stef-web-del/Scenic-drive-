import { render, screen, fireEvent, waitFor, within } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { readFileSync } from "node:fs"

const handlers: Record<string, (e?: unknown) => void> = {}
const fitBounds = vi.fn()
vi.mock("maplibre-gl/dist/maplibre-gl-worker.mjs?url", () => ({ default: "w.js" }))
vi.mock("maplibre-gl", () => {
  class Map {
    sources: Record<string, { setData: (d: unknown) => void }> = {}; layers = new Set<string>()
    constructor() { setTimeout(() => handlers["style.load"]?.(), 0) }
    addControl() {} on(ev: string, f: (e?: unknown) => void) { handlers[ev] = f } remove() {}
    getSource(id: string) { return this.sources[id] } addSource(id: string) { this.sources[id] = { setData: () => {} } }
    getLayer(id: string) { return this.layers.has(id) } addLayer(l: { id: string }) { this.layers.add(l.id) }
    fitBounds(...a: unknown[]) { fitBounds(...a) }
  }
  class LngLatBounds { extend() { return this } }
  return { Map, LngLatBounds, AttributionControl: class {}, NavigationControl: class {}, setWorkerUrl: () => {} }
})
import AbHierPage from "./AbHier"
import { ThemeProvider } from "./theme-provider"
const AbHier = () => <ThemeProvider><AbHierPage /></ThemeProvider>

const file = (u: string) => readFileSync(`public${u}`, "utf8")
beforeEach(() => {
  localStorage.clear(); fitBounds.mockClear()
  vi.stubGlobal("fetch", vi.fn(async (u: string) => new Response(file(u), { status: 200 })))
  vi.stubGlobal("matchMedia", (q: string) => ({ matches: false, media: q, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {} }))
})

describe("Runde ab hier", () => {
  it("zeigt nach Kartenklick 6 Runden, mehr, Filter und Profile", async () => {
    render(<AbHier />)
    await waitFor(() => expect(handlers.click).toBeDefined())
    await waitFor(() => expect(fetch).toHaveBeenCalledWith("/grid/v1/index.json"))
    handlers.click({ lngLat: { lat: 49.703, lng: 9.2575 } })
    const list = await screen.findByRole("list", { name: "Runden-Karussell" })
    expect(within(list).getAllByRole("listitem")).toHaveLength(6)
    expect(screen.getAllByText(/Luftlinie/).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole("button", { name: "Mehr Runden zeigen" }))
    expect(within(list).getAllByRole("listitem").length).toBeGreaterThan(6)
    fireEvent.click(screen.getByLabelText("Mindestqualität lockern (auch Klasse C)"))
    fireEvent.click(screen.getByText("Genießer"))
    expect(JSON.parse(localStorage.getItem("scenic.local.v1")!).profile).toBe("geniesser")
    expect(fitBounds).toHaveBeenCalled()
    expect(screen.getByText(/OpenStreetMap-Mitwirkende/)).toBeTruthy()
  })

  it("umgekehrt, gefahren und melden speichern lokal", async () => {
    render(<AbHier />)
    await waitFor(() => expect(handlers.click).toBeDefined())
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1))
    handlers.click({ lngLat: { lat: 49.703, lng: 9.2575 } })
    const sec = await screen.findByRole("region", { name: "Gewählte Runde" })
    const before = within(sec).getByRole("link").getAttribute("href")
    fireEvent.click(within(sec).getByRole("button", { name: /Umgekehrt fahren/ }))
    expect(within(sec).getByText(/umgekehrt/)).toBeTruthy()
    expect(within(sec).getByRole("link").getAttribute("href")).not.toBe(before)
    fireEvent.click(within(sec).getByRole("button", { name: /Schon gefahren/ }))
    expect(JSON.parse(localStorage.getItem("scenic.local.v1")!).driven).toHaveLength(1)
    fireEvent.click(screen.getByLabelText("Schon gefahrene ausblenden"))
    fireEvent.click(screen.getByLabelText("Weniger Überschneidung mit gefahrenen Runden"))
    fireEvent.click(within(screen.getByRole("list", { name: "Runden-Karussell" })).getAllByRole("listitem")[0])
    fireEvent.click(screen.getByRole("button", { name: /Melden/ }))
    fireEvent.change(await screen.findByLabelText("Beschreibung"), { target: { value: "Baustelle" } })
    fireEvent.click(screen.getByRole("button", { name: "Speichern" }))
    await waitFor(() => expect(JSON.parse(localStorage.getItem("scenic.local.v1")!).reports).toHaveLength(1))
  })

  it("Standort: nicht unterstützt und Fehler beim Laden", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 404 })))
    Object.defineProperty(navigator, "geolocation", { value: undefined, configurable: true })
    render(<AbHier />)
    expect(await screen.findByText("HTTP 404")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: /Meinen Standort verwenden/ }))
    Object.defineProperty(navigator, "geolocation", { value: { getCurrentPosition: (ok: (p: unknown) => void) => ok({ coords: { latitude: 50.1, longitude: 8.6 } }) }, configurable: true })
    fireEvent.click(screen.getByRole("button", { name: /Meinen Standort verwenden/ }))
  })
})
