/// <reference types="geojson" />
import { useEffect, useRef, type RefObject } from "react"
import { Dialog } from "radix-ui"
import * as maplibregl from "maplibre-gl"
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url"
import "maplibre-gl/dist/maplibre-gl.css"
import { XIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { MAP_THEME, ROUTE } from "@/lib/mapTheme"
maplibregl.setWorkerUrl(workerUrl)

const STYLE = { light: "/map/scenic-light.json", dark: "/map/scenic-dark.json" }
const reduced = () => typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches

/**
 * Karte einer Route im Vollbild (Overlay, randlos mit Safe-Area-Insets).
 * Radix-Dialog: Esc schließt, Fokus bleibt im Overlay und kehrt danach zum auslösenden Knopf zurück.
 * Route eingepasst (fitBounds mit Polsterung), Zoom/Pan/Drehen aktiv, Kompass + Standort wie auf der Hauptkarte.
 */
export default function FullscreenMap({ open, onOpenChange, dark, geom, start, title, returnFocus }: {
  open: boolean; onOpenChange: (o: boolean) => void; dark: boolean
  geom: [number, number][]; start?: [number, number]; title: string; returnFocus?: RefObject<HTMLElement | null>
}) {
  const closeRef = useRef<HTMLButtonElement>(null)
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Content data-testid="map-fullscreen" aria-describedby={undefined}
          onOpenAutoFocus={(e) => { e.preventDefault(); closeRef.current?.focus() }}
          onCloseAutoFocus={(e) => { if (returnFocus?.current) { e.preventDefault(); returnFocus.current.focus() } }}
          className="fixed inset-0 z-50 bg-background outline-none">
          <Dialog.Title className="sr-only">{`Karte im Vollbild: ${title}`}</Dialog.Title>
          {open && <MapCanvas dark={dark} geom={geom} start={start} />}
          <Dialog.Close asChild>
            <Button ref={closeRef} variant="secondary" size="icon" aria-label="Vollbild schließen"
              className="absolute top-[max(0.75rem,env(safe-area-inset-top))] left-[max(0.75rem,env(safe-area-inset-left))] size-12 rounded-full shadow-md">
              <XIcon />
            </Button>
          </Dialog.Close>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

function MapCanvas({ dark, geom, start }: { dark: boolean; geom: [number, number][]; start?: [number, number] }) {
  const el = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!el.current || geom.length < 2) return
    const b = geom.reduce((bb, c) => bb.extend(c), new maplibregl.LngLatBounds(geom[0], geom[0]))
    const pad = 56
    const m = new maplibregl.Map({ container: el.current, style: STYLE[dark ? "dark" : "light"], bounds: b, fitBoundsOptions: { padding: pad }, attributionControl: false })
    m.addControl(new maplibregl.AttributionControl({ compact: true, customAttribution: 'Daten © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap-Mitwirkende</a>' }), "bottom-right")
    m.addControl(new maplibregl.NavigationControl({ showCompass: true, visualizePitch: false }), "top-right")
    m.addControl(new maplibregl.GeolocateControl({ trackUserLocation: false }), "top-right")
    const T = MAP_THEME[dark ? "dark" : "light"]
    m.on("style.load", () => {
      m.addSource("fs-route", { type: "geojson", data: { type: "Feature", properties: {}, geometry: { type: "LineString", coordinates: geom } } })
      m.addLayer({ id: "fs-casing", type: "line", source: "fs-route", layout: { "line-cap": "round", "line-join": "round" }, paint: { "line-color": T.routeCasing, "line-width": ROUTE.routeWidth + 2 * ROUTE.routeCasing } })
      m.addLayer({ id: "fs-route", type: "line", source: "fs-route", layout: { "line-cap": "round", "line-join": "round" }, paint: { "line-color": T.routeActive, "line-width": ROUTE.routeWidth } })
      const s = start ?? geom[0]
      m.addSource("fs-start", { type: "geojson", data: { type: "Feature", properties: {}, geometry: { type: "Point", coordinates: s } } })
      m.addLayer({ id: "fs-start", type: "circle", source: "fs-start", paint: { "circle-radius": 8, "circle-color": T.routeActive, "circle-stroke-color": T.routeCasing, "circle-stroke-width": 3 } })
      m.fitBounds(b, { padding: pad, duration: reduced() ? 0 : 300 })
    })
    return () => m.remove()
  }, [dark, geom, start])
  // MapLibre setzt am Container position: relative – daher äußere Hülle absolut, innerer Container füllt sie.
  return <div className="absolute inset-0 pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)]" role="region" aria-label="Karte"><div ref={el} className="map-fs size-full" /></div>
}
