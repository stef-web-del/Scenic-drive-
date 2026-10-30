import { TERMS } from "@/lib/terms"
import { useMemo, useState } from "react"
import { bestEntry, DIRECT_START_M, rotateWaypoints, type Entry } from "@/lib/entry"
import { ChevronDownIcon, LocateFixedIcon, NavigationIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { appleMultiStopOk, appleUrl, appOrder, googleUrl, detectPlatform, GOOGLE_MOBILE_BROWSER_LIMIT, navLinks, pct, type App, type LL, type Nav, type NavLink } from "@/lib/nav"

const NAME: Record<App, string> = { google: "Google Maps", apple: "Apple Karten" }

function Item({ l, sub }: { l: NavLink; sub?: string }) {
  return (
    <DropdownMenuItem asChild className="min-h-11 flex-col items-start gap-0">
      <a href={l.href} target="_blank" rel="noopener noreferrer">
        <span>{l.label}</span>
        <span className="text-xs text-muted-foreground">{sub ?? `ca. ${pct(l.f)} originalgetreu`}</span>
      </a>
    </DropdownMenuItem>
  )
}

/** „Losfahren“: Übergabe an Google Maps / Apple Karten mit vorberechneten Lenkpunkten (Treue je App aus Valhalla-Nachrouting). */
const km = (m: number) => (m < 1000 ? `${Math.round(m / 10) * 10} m` : `${(m / 1000).toFixed(1).replace(".", ",")} km`)

type EntryState = { s: "idle" } | { s: "busy" } | { s: "err"; msg: string } | { s: "ok"; e: Entry }

export function NavMenu({ nav, start, disabled, geom }: { nav?: Nav; start: LL; disabled?: boolean; geom?: LL[] }) {
  const [es, setEs] = useState<EntryState>({ s: "idle" })
  const locate = () => {
    if (!geom || !navigator.geolocation) { setEs({ s: "err", msg: "Standort nicht verfügbar" }); return }
    setEs({ s: "busy" })
    navigator.geolocation.getCurrentPosition(
      (p) => setEs({ s: "ok", e: bestEntry(geom, [p.coords.longitude, p.coords.latitude], p.coords.speed && p.coords.speed > 2 ? p.coords.heading : null) }),
      (err) => setEs({ s: "err", msg: err.code === 1 ? "Standortzugriff abgelehnt" : "Standort nicht ermittelbar" }),
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 30000 },
    )
  }
  const pf = useMemo(() => detectPlatform(navigator.userAgent, navigator.maxTouchPoints), [])
  const apps = useMemo(() => nav ? appOrder(pf).map((app) => ({ app, ...navLinks(app, app === "google" ? nav.g : nav.a, start) })) : [], [nav, pf, start])
  const anyStages = apps.some((a) => a.legs.length > 0)
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild disabled={disabled || !nav}>
        <Button className="h-11"><NavigationIcon data-icon="inline-start" />{TERMS.go}<ChevronDownIcon data-icon="inline-end" /></Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-[min(22rem,calc(100vw-2rem))]" aria-label="Navigation starten mit">
        {apps.map(({ app, whole, legs, stagesF }, i) => (
          <DropdownMenuGroup key={app}>
            {i > 0 && <DropdownMenuSeparator />}
            <DropdownMenuLabel>{NAME[app]}</DropdownMenuLabel>
            {app === "apple" && !appleMultiStopOk(pf) && <p className="px-2 pb-1 text-xs text-muted-foreground">Zwischenziele erst ab iOS 18.4 – bitte iOS aktualisieren oder Google Maps nutzen.</p>}
            <Item l={whole} />
            {legs.length > 0 && <>
              <p className="px-2 pt-1 text-xs text-muted-foreground">Genauer in {legs.length} Etappen (ca. {pct(stagesF!)} originalgetreu):</p>
              {legs.map((l) => <Item key={l.label} l={l} />)}
            </>}
          </DropdownMenuGroup>
        ))}
        {geom && <>
          <DropdownMenuSeparator />
          <DropdownMenuLabel>{TERMS.entry}</DropdownMenuLabel>
          {es.s !== "ok" && (
            <DropdownMenuItem className="min-h-11 flex-col items-start gap-0" onSelect={(ev) => { ev.preventDefault(); locate() }} disabled={es.s === "busy"}>
              <span className="flex items-center gap-2"><LocateFixedIcon />{TERMS.entryHere}</span>
              <span className="text-xs text-muted-foreground">{es.s === "busy" ? "Standort wird bestimmt …" : es.s === "err" ? es.msg : "Runde dort beginnen und dort beenden"}</span>
            </DropdownMenuItem>
          )}
          {es.s === "ok" && nav && (() => {
            const e = es.e
            const g = rotateWaypoints("google", nav.g.wp, geom, e), a = rotateWaypoints("apple", nav.a.wp, geom, e)
            return <>
              <p className="px-2 pb-1 text-xs text-muted-foreground" data-testid="entry-info">
                {e.official ? "Nächster Einstieg ist der offizielle Start." : `${TERMS.entry} bei km ${(e.alongM / 1000).toFixed(1).replace(".", ",")} von ${(e.totalM / 1000).toFixed(1).replace(".", ",")}`}
                {` · ${e.distM <= DIRECT_START_M ? "du bist schon an der Runde" : `${km(e.distM)} Luftlinie entfernt`}. Die Navi-App fährt dich zuerst zum Einstieg.`}
              </p>
              <Item l={{ label: `${NAME.google} ab Einstieg`, href: googleUrl(g.wp, g.dest), f: nav.g.f }} sub="Anfahrt + ganze Runde, endet am Einstieg" />
              <Item l={{ label: `${NAME.apple} ab Einstieg`, href: appleUrl(a.wp, a.dest), f: nav.a.f }} sub={appleMultiStopOk(pf) ? "Anfahrt + ganze Runde, endet am Einstieg" : "ab iOS 18.4"} />
              <DropdownMenuItem className="min-h-11" onSelect={(ev) => { ev.preventDefault(); setEs({ s: "idle" }) }}>{TERMS.entryOfficial}</DropdownMenuItem>
            </>
          })()}
        </>}
        <DropdownMenuSeparator />
        <p className="px-2 py-1.5 text-xs leading-snug text-muted-foreground">
          Die Navi-App rechnet die Strecke selbst über Lenkpunkte nach und kann abweichen{anyStages ? "; bei Etappen nach Ankunft die nächste Etappe hier öffnen" : ""}.
          {pf.mobile && ` Google Maps im Browser (ohne App) übernimmt nur ${GOOGLE_MOBILE_BROWSER_LIMIT} Zwischenziele.`} Exakt: GPX.
        </p>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
