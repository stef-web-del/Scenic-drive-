import { render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { readFileSync } from "node:fs"
import { beforeEach, describe, expect, it, vi } from "vitest"
import App from "./App"
import { ThemeProvider } from "@/components/theme-provider"

// MapLibre (WebGL) und Web Worker gibt es in jsdom nicht → Karte durch Platzhalter, Worker durch Stub ersetzen
vi.mock("@/components/MapView", () => ({ MapView: (p: { visible: unknown[] }) => <div data-testid="map">Karte mit {p.visible.length} Runden</div> }))
class FakeWorker { onmessage: ((e: MessageEvent) => void) | null = null; postMessage() { setTimeout(() => this.onmessage?.({ data: { lines: {}, done: true } } as MessageEvent)) } terminate() {} }

beforeEach(() => {
  history.replaceState(null, "", "/")
  vi.stubGlobal("Worker", FakeWorker)
  vi.stubGlobal("fetch", vi.fn(async (u: string) => {
    const p = "public/data/" + String(u).split("/data/")[1]
    try { return new Response(readFileSync(p)) } catch { return new Response("", { status: 404 }) }
  }))
})
const app = () => render(<ThemeProvider><App /></ThemeProvider>)
const list = async () => within(await screen.findByRole("list", { name: "Runden" }))

describe("App (Desktop, echte Daten)", () => {
  it("Start: 9 Familienvertreter bei 25 %", async () => {
    app(); expect((await list()).getAllByRole("button")).toHaveLength(9)
    expect(screen.getByText(/max\. 25 % Überschneidung/)).toBeInTheDocument()
    expect(screen.getByRole("heading", { name: "Scenic Routes Miltenberg" })).toBeInTheDocument()
  })
  it("Runde öffnen → Detail, Varianten → ganze Familie, zurück", async () => {
    app(); const first = (await list()).getAllByRole("button")[0]; const title = first.querySelector(".truncate")!.textContent!
    await userEvent.click(first)
    expect(await screen.findByRole("heading", { level: 2 })).toHaveTextContent(title)
    expect(location.hash).toMatch(/r=\d+/)
    const v = await screen.findByRole("button", { name: /Varianten dieser Runde anzeigen \((\d+)\)/ })
    const n = Number(v.textContent!.match(/\((\d+)\)/)![1])
    await userEvent.click(v)
    await waitFor(() => expect(screen.getByText(/Varianten · 25 % Überschneidung/)).toHaveTextContent(new Intl.NumberFormat("de-DE").format(n)))
    await userEvent.click(screen.getByRole("button", { name: "Zurück" }))
    expect(await screen.findByText(/max\. 25 %/)).toBeInTheDocument()
  })
  it("Filter-Tab: 50 % → 29 Runden; Experten → 342", async () => {
    app(); await list()
    await userEvent.click(screen.getByRole("tab", { name: "Filter" }))
    await userEvent.click(screen.getByRole("radio", { name: "50 %" }))
    expect(screen.getByText(/aktuell 29 Runden/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole("switch", { name: "Experten: alle Runden" }))
    await userEvent.click(screen.getByRole("tab", { name: "Runden" }))
    await waitFor(() => expect(screen.getByText(/Experten:/)).toHaveTextContent("Experten: 342 von 342 Runden"))
  })
  it("Direktlink auf Nicht-Vertreter lädt alle Runden nach und bietet beste Runde", async () => {
    const all = JSON.parse(readFileSync("public/data/all.json", "utf8")); const v = all.find((l: { r: number[] }) => !l.r[1])
    history.replaceState(null, "", "/#r=" + v.id); app()
    await userEvent.click(await screen.findByRole("button", { name: "Zur besten Runde der Familie" }))
    expect(await screen.findByText("Beste Runde dieser Familie (Tour-Score).")).toBeInTheDocument()
  })
  it("Datenfehler zeigt Alert", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 500 }))); app()
    expect(await screen.findByText("Daten konnten nicht geladen werden")).toBeInTheDocument()
  })
})
