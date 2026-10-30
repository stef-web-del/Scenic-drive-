import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"
import { LoopList } from "./LoopList"
import { LoopDetail } from "./LoopDetail"
import { Filters } from "./Filters"
import { DEFAULT } from "@/lib/state"
import { detail, loop, sb } from "@/test/fixtures"
import { ScoreBreakdown } from "./ScoreBreakdown"

vi.mock("sonner", () => ({ toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }) }))

describe("LoopList", () => {
  it("zeigt Runden sortiert nach Tour-Score mit Kennzahlen", async () => {
    const onPick = vi.fn()
    render(<LoopList loops={[loop({ id: 1, t: 0.3, ti: "Amorbach – Kirchzell" }), loop({ id: 2, t: 0.41, ti: "Breitenau – Wettersdorf", km: 54.4, min: 56, hm: 801, tags: ["a", "b", "c", "d", "e", "f"] })]} sort="tour" onPick={onPick} onReset={() => {}} />)
    const items = within(screen.getByRole("list", { name: "Runden" })).getAllByRole("button")
    expect(items[0]).toHaveTextContent("Breitenau – Wettersdorf"); expect(items[0]).toHaveTextContent("54,4 km"); expect(items[0]).toHaveTextContent("801 Hm"); expect(items[0]).toHaveTextContent("+2")
    await userEvent.click(items[1]); expect(onPick).toHaveBeenCalledWith(1)
  })
  it("paginiert ab 40 Einträgen", async () => {
    render(<LoopList loops={Array.from({ length: 105 }, () => loop())} sort="score" onPick={() => {}} onReset={() => {}} />)
    expect(screen.getAllByRole("listitem")).toHaveLength(41)
    await userEvent.click(screen.getByRole("button", { name: "Weitere anzeigen (65)" }))
    await userEvent.click(screen.getByRole("button", { name: "Weitere anzeigen (5)" }))
    expect(screen.getAllByRole("listitem")).toHaveLength(105)
  })
  it("Leerzustand mit Zurücksetzen", async () => {
    const onReset = vi.fn(); render(<LoopList loops={[]} sort="tour" onPick={() => {}} onReset={onReset} />)
    expect(screen.getByText("Keine Runde passt")).toBeInTheDocument()
    await userEvent.click(screen.getByRole("button", { name: "Filter zurücksetzen" })); expect(onReset).toHaveBeenCalled()
  })
})

describe("LoopDetail", () => {
  const base = { error: null, start: [9.22, 49.7] as [number, number], levels: [0.15, 0.25, 0.35, 0.5, 0.7], lvl: 1, onBack: vi.fn(), onPick: vi.fn(), onVariants: vi.fn() }
  it("Ab meinem Standort einsteigen: rotierte Links für Google Maps und Apple Karten", async () => {
    const d = detail({ id: 9 })
    const here = d.g[Math.floor(d.g.length / 2)]
    Object.defineProperty(navigator, "geolocation", { configurable: true, value: { getCurrentPosition: (ok: PositionCallback) => ok({ coords: { longitude: here[0] + 0.01, latitude: here[1], speed: null, heading: null } } as unknown as GeolocationPosition) } })
    render(<LoopDetail {...base} loop={loop({ id: 9 })} detail={d} famSize={1} repId={9} />)
    await userEvent.click(screen.getByRole("button", { name: "Losfahren" }))
    await userEvent.click(screen.getByRole("menuitem", { name: /Ab meinem Standort einsteigen/ }))
    const g = screen.getByRole("menuitem", { name: /Google Maps ab Einstieg/ }), a = screen.getByRole("menuitem", { name: /Apple Karten ab Einstieg/ })
    expect(g).toHaveAttribute("href", expect.stringContaining("https://www.google.com/maps/dir/?api=1&destination="))
    expect(a).toHaveAttribute("href", expect.stringContaining("https://maps.apple.com/directions?"))
    expect(screen.getByTestId("entry-info")).toHaveTextContent(/Einstieg|offizielle Start/)
  })
  it("Vertreter: Familie, Varianten-Button, Kennzahlen, Pausen", async () => {
    const l = loop({ id: 9, ti: "Breitenau – Wettersdorf", kl: "A" })
    render(<LoopDetail {...base} loop={l} detail={detail({ id: 9 })} famSize={592} repId={9} />)
    expect(screen.getByRole("heading", { level: 2 })).toHaveTextContent("Breitenau – Wettersdorf")
    expect(screen.getByText("Familie bei 25 % Überschneidung: 592 Runden.")).toBeInTheDocument()
    expect(screen.getByText("Beste Runde dieser Familie (Tour-Score).")).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Zur besten Runde der Familie" })).toBeNull()
    expect(screen.getByText("Gasthof Linde")).toBeInTheDocument()
    await userEvent.click(screen.getByRole("button", { name: "Losfahren" }))
    const items = screen.getAllByRole("menuitem")
    expect(items.map((i) => i.textContent)).toEqual(["Ganze Runde" + "ca. 97 % originalgetreu", "Ganze Runde" + "ca. 83 % originalgetreu", "Etappe 1 von 2ca. 95 % originalgetreu", "Etappe 2 von 2ca. 97 % originalgetreu", "Ab meinem Standort einsteigenRunde dort beginnen und dort beenden"])
    expect(items[0]).toHaveAttribute("href", expect.stringMatching(/^https:\/\/www\.google\.com\/maps\/dir\/\?api=1&destination=49\.7%2C9\.22&waypoints=49\.7%2C9\.25%7C49\.71%2C9\.3&travelmode=driving&dir_action=navigate$/))
    expect(items[1]).toHaveAttribute("href", expect.stringContaining("https://maps.apple.com/directions?"))
    expect(items[0]).toHaveAttribute("target", "_blank"); expect(screen.getByText(/Genauer in 2 Etappen \(ca\. 96 %/)).toBeInTheDocument()
    await userEvent.keyboard("{Escape}")
    await userEvent.click(screen.getByRole("button", { name: "Varianten dieser Runde anzeigen (592)" })); expect(base.onVariants).toHaveBeenCalledWith(9)
    await userEvent.click(screen.getByRole("button", { name: "Zurück zur Liste" })); expect(base.onBack).toHaveBeenCalled()
  })
  it("Variante: Sprung zur besten Runde", async () => {
    render(<LoopDetail {...base} loop={loop({ id: 77 })} detail={detail({ id: 77 })} famSize={5} repId={9} />)
    await userEvent.click(screen.getByRole("button", { name: "Zur besten Runde der Familie" })); expect(base.onPick).toHaveBeenCalledWith(9)
  })
  it("Einzelrunde ohne Varianten-Button", () => {
    render(<LoopDetail {...base} loop={loop({ id: 3 })} detail={detail({ id: 3 })} famSize={1} repId={3} />)
    expect(screen.getByText(/1 Runde\./)).toBeInTheDocument(); expect(screen.queryByRole("button", { name: /Varianten/ })).toBeNull()
  })
  it("GPX-Download erzeugt Datei runde-ID.gpx", async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
    render(<LoopDetail {...base} loop={loop({ id: 9 })} detail={detail({ id: 9 })} famSize={1} repId={9} />)
    await userEvent.click(screen.getByRole("button", { name: "GPX" }))
    expect(click).toHaveBeenCalled(); expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe("runde-9.gpx")
  })
  it("ohne Detail: GPX deaktiviert; Fehler sichtbar", () => {
    render(<LoopDetail {...base} loop={loop({ id: 4 })} detail={null} error="HTTP 500" famSize={1} repId={4} />)
    expect(screen.getByRole("button", { name: "GPX" })).toBeDisabled(); expect(screen.getByText(/Details konnten nicht geladen werden: HTTP 500/)).toBeInTheDocument()
  })
})

describe("Filters", () => {
  const loops = [loop({ r: [1, 1, 0, 1, 1] }), loop({ r: [0, 1, 0, 1, 1] }), loop({ rg: "Spessart", r: [0, 0, 0, 1, 1] })]
  const props = { loops, total: 1306, regions: ["Odenwald (N)", "Spessart"], levels: [0.15, 0.25, 0.35, 0.5, 0.7] }
  it("Überschneidungs-Auswahl mit 25 % aktiv und Familienzahl", async () => {
    const set = vi.fn(); render(<Filters {...props} st={DEFAULT} set={set} />)
    expect(screen.getByRole("radio", { name: "25 %" })).toHaveAttribute("aria-checked", "true")
    expect(screen.getByText(/aktuell 2 Runden/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole("radio", { name: "50 %" })); expect(set).toHaveBeenCalledWith({ lvl: 3 })
  })
  it("Expertenschalter: alle 1.306 Runden", async () => {
    const set = vi.fn(); render(<Filters {...props} st={DEFAULT} set={set} />)
    expect(screen.getByText(/Lädt alle 1.306 Varianten/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole("switch", { name: "Experten: alle Runden" })); expect(set).toHaveBeenCalledWith({ rep: false, v: null })
  })
  it("Klasse abwählen und zurücksetzen", async () => {
    const set = vi.fn(); render(<Filters {...props} st={{ ...DEFAULT, lvl: 3 }} set={set} />)
    await userEvent.click(screen.getByRole("button", { name: /Klasse\s?C/ }))
    expect(set).toHaveBeenCalledWith({ kl: ["A", "B"] })
    await userEvent.click(screen.getByRole("button", { name: "Filter zurücksetzen" })); expect(set).toHaveBeenLastCalledWith(expect.objectContaining({ lvl: 1, kl: ["A", "B", "C"] }))
  })
  it("Region abwählen", async () => {
    const set = vi.fn(); render(<Filters {...props} st={DEFAULT} set={set} />)
    await userEvent.click(screen.getByRole("checkbox", { name: /Spessart/ })); expect(set).toHaveBeenCalledWith({ rg: ["Odenwald (N)"] })
  })
})

describe("ScoreBreakdown", () => {
  it("zeigt Beiträge, Perzentile, Anteile und beste Abschnitte; Klick wählt Abschnitt", async () => {
    let picked: number | null = -1
    render(<ScoreBreakdown loop={loop({ t: 0.35, kl: "A" })} sb={sb()} seg={null} onSeg={(i) => (picked = i)} segCol={false} onSegCol={() => {}} />)
    expect(screen.getByText("Warum diese Runde?")).toBeInTheDocument()
    expect(screen.getByText("Kurvigkeit")).toBeInTheDocument(); expect(screen.getByText("90 %")).toBeInTheDocument()
    expect(screen.getByRole("img", { name: /Highlight 30 %/ })).toBeInTheDocument()
    const b = screen.getByRole("button", { name: /Kehrenstrecke/ }); expect(screen.queryByRole("button", { name: /Breitendieler/ })).toBeNull()
    await userEvent.click(b); expect(picked).toBe(2)
    await userEvent.click(screen.getByRole("button", { name: "Erklärung: Rhythmus" })); expect(await screen.findByText(/gleichmäßig/)).toBeInTheDocument()
  })
  it("LoopDetail zeigt Stärken-Chips und Aufschlüsselung", () => {
    render(<LoopDetail error={null} start={[9.22, 49.7]} levels={[0.15, 0.25]} lvl={1} onBack={vi.fn()} onPick={vi.fn()} onVariants={vi.fn()} loop={loop({ id: 9, st: ["kurv", "dram"] })} detail={detail({ id: 9, sb: sb() })} famSize={1} repId={9} />)
    expect(screen.getByLabelText("Stärken")).toHaveTextContent("Sehr kurvig"); expect(screen.getByRole("heading", { name: "Warum diese Runde?" })).toBeInTheDocument()
  })
})
