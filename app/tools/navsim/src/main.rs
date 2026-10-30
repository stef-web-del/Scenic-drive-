//! Scenic Routes – Navigations-Simulation gegen den echten Ferrostar-Kern (gleiche Version wie die App).
//! Spielt simulierte GPS-Folgen entlang vorberechneter Runden (nav/v1-JSON) ab und misst,
//! ob die Abbiegeanweisung (TripState.visual_instruction / aktueller Schritt) mit der Fahrt mitläuft.
//!
//! Aufruf: navsim [--config before|after|both] [--strict] <nav.json>...
//! --strict: Exit-Code 1, wenn die "after"-Konfiguration ein Szenario nicht besteht (für CI).
use ferrostar::deviation_detection::{RouteDeviation, RouteDeviationTracking};
use ferrostar::models::{GeographicCoordinate, Route, UserLocation};
use ferrostar::navigation_controller::models::{CourseFiltering, NavigationControllerConfig, TripState, WaypointAdvanceMode};
use ferrostar::navigation_controller::step_advance::conditions::{
    DeviationCalculationPolicy, DistanceFromStepCondition, DistanceToEndOfStepCondition, OrAdvanceConditions,
};
use ferrostar::navigation_controller::create_navigator;
use ferrostar::routing_adapters::{osrm::OsrmResponseParser, RouteResponseParser};
use std::sync::Arc;
use std::time::{Duration, SystemTime};

const SPEED: f64 = 15.0; // m/s ≈ 54 km/h

fn entry_exit(acc: u16) -> Arc<dyn ferrostar::navigation_controller::step_advance::StepAdvanceCondition> {
    ferrostar::navigation_controller::step_advance::step_advance_distance_entry_and_exit(30, 5, acc)
}

/// Konfiguration wie in NavViewModel.loopNavConfig() (0.4.1).
fn config_before() -> NavigationControllerConfig {
    NavigationControllerConfig {
        waypoint_advance: WaypointAdvanceMode::WaypointAlongAdvancingStep(100.0),
        step_advance_condition: entry_exit(32),
        arrival_step_advance_condition: Arc::new(DistanceToEndOfStepCondition { distance: 10, minimum_horizontal_accuracy: 32 }),
        route_deviation_tracking: RouteDeviationTracking::StaticThreshold { minimum_horizontal_accuracy: 15, max_acceptable_deviation: 50.0 },
        snapped_location_course_filtering: CourseFiltering::SnapToRoute,
    }
}

/// Fix (Ferrostar-Teil): Eintritt/Austritt mit realistischer Genauigkeitsgrenze (40 m statt 32 m),
/// Ankunft 30 m, Abweichungserkennung auch bei 15–40 m Genauigkeit. Das Weiterschalten nach verpasstem
/// Schrittende übernimmt die App (StepSync, siehe [App]); ein reines DistanceFromStep-Speedrun ist bei
/// Runden (Start = Ziel, Kreuzungen) gefährlich: ein einzelner 50-m-Ausreißer am Start springt ans Ende.
fn config_after() -> NavigationControllerConfig {
    NavigationControllerConfig {
        waypoint_advance: WaypointAdvanceMode::WaypointAlongAdvancingStep(100.0),
        step_advance_condition: entry_exit(40),
        arrival_step_advance_condition: Arc::new(DistanceToEndOfStepCondition { distance: 30, minimum_horizontal_accuracy: 40 }),
        route_deviation_tracking: RouteDeviationTracking::StaticThreshold { minimum_horizontal_accuracy: 40, max_acceptable_deviation: 50.0 },
        snapped_location_course_filtering: CourseFiltering::SnapToRoute,
    }
}
/// Nur zum Nachweis: Or(EntryExit, DistanceFromStep 50 m) ohne App-Sync.
fn config_dfs() -> NavigationControllerConfig {
    let mut c = config_after();
    c.step_advance_condition = Arc::new(OrAdvanceConditions { conditions: vec![entry_exit(40),
        Arc::new(DistanceFromStepCondition { distance: 50, minimum_horizontal_accuracy: 40, calculation_policy: DeviationCalculationPolicy::WhileOnRoute })] });
    c
}

// ---------- Geometrie ----------
#[derive(Clone, Copy)]
struct P { lat: f64, lng: f64 }
fn dist(a: P, b: P) -> f64 {
    let dy = (a.lat - b.lat) * 111_320.0;
    let dx = (a.lng - b.lng) * 111_320.0 * a.lat.to_radians().cos();
    (dx * dx + dy * dy).sqrt()
}
struct Line { pts: Vec<P>, cum: Vec<f64> }
impl Line {
    fn new(pts: Vec<P>) -> Self {
        let mut cum = vec![0.0];
        for i in 1..pts.len() { cum.push(cum[i - 1] + dist(pts[i - 1], pts[i])); }
        Line { pts, cum }
    }
    fn len(&self) -> f64 { *self.cum.last().unwrap() }
    fn at(&self, s: f64) -> P {
        let s = s.clamp(0.0, self.len());
        let i = match self.cum.binary_search_by(|c| c.partial_cmp(&s).unwrap()) { Ok(i) => i.min(self.pts.len() - 2), Err(i) => i.saturating_sub(1).min(self.pts.len() - 2) };
        let seg = self.cum[i + 1] - self.cum[i];
        let t = if seg > 0.0 { (s - self.cum[i]) / seg } else { 0.0 };
        P { lat: self.pts[i].lat + t * (self.pts[i + 1].lat - self.pts[i].lat), lng: self.pts[i].lng + t * (self.pts[i + 1].lng - self.pts[i].lng) }
    }
    /// Normale (Einheitsvektor in Metern, links der Fahrtrichtung) bei s.
    fn normal(&self, s: f64) -> (f64, f64) {
        let a = self.at(s); let b = self.at(s + 20.0);
        let dy = (b.lat - a.lat) * 111_320.0; let dx = (b.lng - a.lng) * 111_320.0 * a.lat.to_radians().cos();
        let n = (dx * dx + dy * dy).sqrt().max(1e-6);
        (-dy / n, dx / n)
    }
}
fn offset(p: P, east: f64, north: f64) -> P { P { lat: p.lat + north / 111_320.0, lng: p.lng + east / (111_320.0 * p.lat.to_radians().cos()) } }

// ---------- App-Logik (1:1 wie shared/nav/OffRouteMonitor + StepSync in Kotlin) ----------
struct Proj { d: f64, along: f64 }
fn project(line: &Line, p: P, hint: f64, window: f64) -> Proj { project_pen(line, p, hint, window, 30.0) }
fn project_pen(line: &Line, p: P, hint: f64, window: f64, penalty: f64) -> Proj {
    let kx = 111_320.0 * p.lat.to_radians().cos(); let ky = 110_540.0;
    let pen = |al: f64| if (al - hint).abs() > window { penalty } else { 0.0 };
    let mut best: Option<Proj> = None;
    for i in 0..line.pts.len() - 1 {
        let (a, b) = (line.pts[i], line.pts[i + 1]);
        let (ax, ay) = ((a.lng - p.lng) * kx, (a.lat - p.lat) * ky); let (bx, by) = ((b.lng - p.lng) * kx, (b.lat - p.lat) * ky);
        let (dx, dy) = (bx - ax, by - ay); let l2 = dx * dx + dy * dy;
        let t = if l2 == 0.0 { 0.0 } else { (-(ax * dx + ay * dy) / l2).clamp(0.0, 1.0) };
        let d = ((ax + t * dx).powi(2) + (ay + t * dy).powi(2)).sqrt();
        let along = line.cum[i] + t * (line.cum[i + 1] - line.cum[i]);
        if best.as_ref().map_or(true, |bp| d + pen(along) < bp.d + pen(bp.along)) { best = Some(Proj { d, along }); }
    }
    best.unwrap()
}
struct App { progress: f64, off: bool, off_count: u32, last_t: Option<f64>, pending: Option<(f64, f64, u32)>, ww: WrongWay }
/// WrongWayDetector (0.6.6): > 120° gegen die Runde, ≤ 40 m daneben, ≥ 3 Fixes und ≥ 150 m.
#[derive(Default)]
struct WrongWay { n: u32, m: f64, last: Option<P>, fired_at: Option<f64> }
impl WrongWay {
    fn update(&mut self, p: P, d: f64, diff: Option<f64>, t: f64) {
        match diff { Some(x) if x > 120.0 && d <= 40.0 => { if let Some(q) = self.last { self.m += dist(q, p); } self.n += 1; self.last = Some(p);
            if self.fired_at.is_none() && self.n >= 3 && self.m >= 150.0 { self.fired_at = Some(t); } }
            _ => { self.n = 0; self.m = 0.0; self.last = None; } }
    }
}
impl App {
    /// OffRouteMonitor.update (Schwelle 50 m, zurück < 25 m, 3 Fixes; Fortschritt monoton).
    /// Neu: Fortschritt springt höchstens 300 m + 70 m/s seit dem letzten Treffer vor (Runde kreuzt sich /
    /// Start = Ziel: ein verrauschter Fix am Start passt sonst aufs Rundenende). Erster Treffer: starke Präferenz
    /// für den Anfang, damit Einstieg mitten in der Runde trotzdem gefunden wird.
    fn update(&mut self, line: &Line, p: P, t: f64, hdg: Option<f64>) {
        let pr = match self.last_t { None => project_pen(line, p, 0.0, 3000.0, 60.0), Some(_) => project(line, p, self.progress, 3000.0) };
        // 0.6.6: Fenster höchstens 2 km; Fortschritt nur, wenn die Fahrtrichtung zur Runde passt.
        let max_jump = self.last_t.map_or(f64::INFINITY, |lt| (300.0 + 70.0 * (t - lt)).min(2000.0));
        let diff = hdg.map(|h| hdiff(h, loop_bearing(line, pr.along)));
        self.ww.update(p, pr.d, diff, t);
        let against = diff.map_or(false, |x| x > 120.0);
        if against { self.pending = None; }
        else if pr.d < 25.0 && pr.along >= self.progress - 200.0 && pr.along <= self.progress + max_jump {
            if pr.along > self.progress || self.last_t.is_none() { self.progress = self.progress.max(pr.along); }
            self.last_t = Some(t); self.pending = None;
        } else if pr.d < 25.0 && pr.along > self.progress + max_jump {
            // Abkürzung: weiter vorn wieder auf der Runde. Erst nach 3 aufeinanderfolgenden, plausibel
            // vorwärts laufenden Treffern übernehmen (ein Einzel-Fix an einer Kreuzung reicht nicht).
            let ok = matches!(self.pending, Some((pa, pt, _)) if pr.along > pa && pr.along <= pa + 300.0 + 70.0 * (t - pt));
            let c = if ok { self.pending.unwrap().2 + 1 } else { 1 };
            if c >= 3 { self.progress = pr.along; self.last_t = Some(t); self.pending = None; } else { self.pending = Some((pr.along, t, c)); }
        } else { self.pending = None; }
        if !self.off { self.off_count = if pr.d > 50.0 { self.off_count + 1 } else { 0 }; if self.off_count >= 3 { self.off = true; } }
        else if pr.d < 25.0 { self.off = false; self.off_count = 0; }
    }
}
/// StepSync.stepsToAdvance: wie viele Schritte liegen komplett hinter dem (monotonen) Fortschritt?
fn steps_to_advance(step_end: &[f64], idx: usize, progress: f64) -> usize {
    let mut k = 0;
    while idx + k + 2 < step_end.len() && step_end[idx + k] < progress - 40.0 { k += 1; }
    k
}

// ---------- deterministischer Zufall ----------
struct Rng(u64);
impl Rng {
    fn next(&mut self) -> f64 { self.0 ^= self.0 << 13; self.0 ^= self.0 >> 7; self.0 ^= self.0 << 17; (self.0 >> 11) as f64 / (1u64 << 53) as f64 }
    fn range(&mut self, a: f64, b: f64) -> f64 { a + (b - a) * self.next() }
    fn gauss(&mut self) -> f64 { let u = self.next().max(1e-12); let v = self.next(); (-2.0 * u.ln()).sqrt() * (2.0 * std::f64::consts::PI * v).cos() }
}

/// Ein Fix: Position, gemeldete Genauigkeit, wahre Lage auf der Runde (None = abseits), Zeit in s.
struct Fix { p: P, acc: f64, truth: Option<f64>, t: f64, hdg: Option<f64> }
/// Kurs a→b in Grad (0 = Nord, im Uhrzeigersinn).
fn bearing(a: P, b: P) -> f64 {
    let e = (b.lng - a.lng) * 111_320.0 * a.lat.to_radians().cos(); let n = (b.lat - a.lat) * 110_540.0;
    (e.atan2(n).to_degrees() + 360.0) % 360.0
}
fn hdiff(a: f64, b: f64) -> f64 { let d = (a - b).abs() % 360.0; if d > 180.0 { 360.0 - d } else { d } }
/// Kurs der Runde an Position s (±20 m), wie OffRouteMonitor.loopBearingAt.
fn loop_bearing(line: &Line, s: f64) -> f64 { let a = (s - 20.0).max(0.0); let b = (s + 20.0).min(line.len()); bearing(line.at(a), line.at(b)) }

/// Fahrt entlang [from, to) der Runde mit 1 Hz.
fn drive(line: &Line, from: f64, to: f64, t: &mut f64, out: &mut Vec<Fix>) {
    let mut s = from;
    while s < to { out.push(Fix { p: line.at(s), acc: 5.0, truth: Some(s), t: *t, hdg: Some(loop_bearing(line, s)) }); s += SPEED; *t += 1.0; }
}
/// Runde rückwärts: von `from` abwärts bis `to` (Kurs entgegen der Runde).
fn drive_rev(line: &Line, from: f64, to: f64, t: &mut f64, out: &mut Vec<Fix>) {
    let mut s = from;
    while s > to { out.push(Fix { p: line.at(s), acc: 5.0, truth: Some(s), t: *t, hdg: Some((loop_bearing(line, s) + 180.0) % 360.0) }); s -= SPEED; *t += 1.0; }
}
/// Fahrt abseits (gerade Linie a→b).
fn drive_off(a: P, b: P, t: &mut f64, out: &mut Vec<Fix>) {
    let d = dist(a, b); let n = (d / SPEED).ceil().max(1.0) as usize;
    for i in 0..n { let f = i as f64 / n as f64; out.push(Fix { p: P { lat: a.lat + f * (b.lat - a.lat), lng: a.lng + f * (b.lng - a.lng) }, acc: 5.0, truth: None, t: *t, hdg: Some(bearing(a, b)) }); *t += 1.0; }
}

fn scenario(name: &str, line: &Line, rng: &mut Rng) -> Vec<Fix> {
    let l = line.len(); let mut t = 0.0; let mut v = Vec::new();
    match name {
        "exakt" => drive(line, 0.0, l, &mut t, &mut v),
        "rauschen" => { drive(line, 0.0, l, &mut t, &mut v); for f in &mut v { let sd = rng.range(5.0, 15.0); f.p = offset(f.p, rng.gauss() * sd / 1.4, rng.gauss() * sd / 1.4); f.acc = rng.range(6.0, 25.0); } }
        "wald" => { drive(line, 0.0, l, &mut t, &mut v); for f in &mut v { f.p = offset(f.p, rng.gauss() * 10.0, rng.gauss() * 10.0); f.acc = rng.range(20.0, 38.0); } }
        "luecken" => {
            drive(line, 0.0, l, &mut t, &mut v);
            // alle ~4 km eine Lücke von 30–120 s (Tunnel / Bildschirm aus)
            let mut keep = vec![true; v.len()]; let mut i = 60;
            while i < v.len() { let g = rng.range(30.0, 120.0) as usize; for k in i..(i + g).min(v.len() - 5) { keep[k] = false; } i += g + 260; }
            let mut k = 0; v.retain(|_| { let r = keep[k]; k += 1; r });
        }
        "abweichung_2km" => {
            let s0 = 0.35 * l; drive(line, 0.0, s0, &mut t, &mut v);
            let a = line.at(s0); let (ex, ny) = line.normal(s0); let far = offset(a, ex * 1000.0, ny * 1000.0);
            drive_off(a, far, &mut t, &mut v); drive_off(far, a, &mut t, &mut v); drive(line, s0, l, &mut t, &mut v);
        }
        "rueckkehr_voraus" => {
            let s0 = 0.35 * l; let s1 = s0 + 3000.0; drive(line, 0.0, s0, &mut t, &mut v);
            let a = line.at(s0); let (ex, ny) = line.normal(s0); let far = offset(a, ex * 800.0, ny * 800.0);
            drive_off(a, far, &mut t, &mut v); drive_off(far, line.at(s1), &mut t, &mut v); drive(line, s1, l, &mut t, &mut v);
        }
        "abkuerzung" => {
            let s0 = 0.30 * l; let s1 = 0.45 * l; drive(line, 0.0, s0, &mut t, &mut v);
            drive_off(line.at(s0), line.at(s1), &mut t, &mut v); drive(line, s1, l, &mut t, &mut v);
        }
        "ausreisser" => { // Mehrwege-Ausreißer: alle ~2 km ein einzelner Fix 60–150 m daneben
            drive(line, 0.0, l, &mut t, &mut v);
            let mut i = 0; while i < v.len() { let (ex, ny) = line.normal(v[i].truth.unwrap()); let d = rng.range(60.0, 150.0); v[i].p = offset(v[i].p, ex * d, ny * d); v[i].acc = rng.range(10.0, 30.0); i += 133; }
        }
        "einstieg_mitte" => drive(line, 0.5 * l, l, &mut t, &mut v),
        // 0.6.6: Runde verkehrt herum gefahren (vom Start aus entgegen der Richtung, ~60 %)
        "runde_rueckwaerts" => drive_rev(line, l, 0.4 * l, &mut t, &mut v),
        _ => unreachable!(),
    }
    // Am Ziel realistisch stehen bleiben: 30 s Stand mit GPS-Rauschen (Wald stärker, Rest wie ein ruhendes Handy).
    let (sd, a0, a1) = match name { "wald" => (10.0, 20.0, 38.0), "rauschen" => (8.0, 6.0, 25.0), _ => (4.0, 5.0, 12.0) };
    let end = line.at(l);
    for _ in 0..DWELL_S { v.push(Fix { p: offset(end, rng.gauss() * sd, rng.gauss() * sd), acc: rng.range(a0, a1), truth: Some(l), t, hdg: None }); t += 1.0; }
    v
}
const DWELL_S: usize = 30;

/// Ankunftsregel der App (shared/nav/ArrivalDetector): ≤ 50 m vom Ziel, ≥ 95 % Fortschritt, 10 s Stand
/// (Mittel der ersten und letzten drei Fixes im Fenster < 20 m auseinander).
#[derive(Default)]
struct Arrival { win: std::collections::VecDeque<(f64, f64, f64)> }
impl Arrival {
    fn update(&mut self, dist_end: f64, frac: f64, e: f64, n: f64, t: f64) -> bool {
        if dist_end > 50.0 || frac < 0.95 { self.win.clear(); return false; }
        self.win.push_back((e, n, t));
        while self.win.len() > 3 && t - self.win[1].2 >= 10.0 { self.win.pop_front(); }
        if self.win.len() < 6 || t - self.win[0].2 < 10.0 { return false; }
        let k = self.win.len();
        let m = |r: std::ops::Range<usize>| { let (mut x, mut y) = (0.0, 0.0); for i in r { x += self.win[i].0; y += self.win[i].1; } (x / 3.0, y / 3.0) };
        let (a, b) = (m(0..3), m(k - 3..k));
        ((a.0 - b.0).powi(2) + (a.1 - b.1).powi(2)).sqrt() < 20.0
    }
}

const SCENARIOS: [&str; 10] = ["exakt", "ausreisser", "rauschen", "wald", "luecken", "abweichung_2km", "rueckkehr_voraus", "abkuerzung", "einstieg_mitte", "runde_rueckwaerts"];

struct Outcome { max_stale_s: f64, stale_pct: f64, complete_at: Option<f64>, final_step: usize, steps: usize, off_seen: bool, banner_while_off: usize, wrong_way_s: Option<f64>, max_progress: f64, len: f64 }

fn run(route: &Route, line: &Line, step_end: &[f64], fixes: &[Fix], cfg: NavigationControllerConfig, app_on: bool) -> Outcome {
    let mut app = App { progress: 0.0, off: false, off_count: 0, last_t: None, pending: None, ww: WrongWay::default() };
    let mut arr = Arrival::default(); let end = line.at(line.len());
    let nav = create_navigator(route.clone(), cfg, false);
    let mk = |f: &Fix| UserLocation {
        coordinates: GeographicCoordinate { lat: f.p.lat, lng: f.p.lng }, horizontal_accuracy: f.acc, course_over_ground: None,
        timestamp: SystemTime::UNIX_EPOCH + Duration::from_secs_f64(1.8e9 + f.t), speed: None,
    };
    let mut st = nav.get_initial_state(mk(&fixes[0]));
    let n = step_end.len();
    let (mut stale_run, mut max_stale, mut stale_n, mut on_n) = (0.0f64, 0.0f64, 0usize, 0usize);
    let mut last_t = fixes[0].t; let mut complete_at = None; let mut off_seen = false; let mut banner_while_off = 0; let mut final_step = 0;
    for f in fixes {
        st = nav.update_user_location(mk(f), st);
        if app_on && std::env::var("NAVSIM_NOAPP").is_err() {
            app.update(line, f.p, f.t, f.hdg);
            if let TripState::Navigating { remaining_steps, .. } = st.trip_state() {
                let idx = n - remaining_steps.len();
                if !app.off { for _ in 0..steps_to_advance(step_end, idx, app.progress) { st = nav.advance_to_next_step(st); } }
            }
            // App-Ankunft: Fortschritt am Rundenende (Ferrostar-Ankunft braucht sonst noch einen Fix hinter dem letzten Kreisel)
            let kx = 111_320.0 * end.lat.to_radians().cos();
            if complete_at.is_none() && arr.update(dist(f.p, end), app.progress / line.len(), (f.p.lng - end.lng) * kx, (f.p.lat - end.lat) * 110_540.0, f.t) {
                complete_at = Some(f.truth.unwrap_or(-1.0) / line.len()); final_step = n; break;
            }
            if app.progress >= line.len() - 40.0 && complete_at.is_none() { complete_at = Some(f.truth.unwrap_or(-1.0) / line.len()); final_step = n; break; }
        }
        if std::env::var("NAVSIM_TRACE").is_ok() && f.t < 40.0 {
            let (i, dv) = match st.trip_state() { TripState::Navigating { remaining_steps, deviation, .. } => (n - remaining_steps.len(), format!("{deviation:?}")), o => (999, format!("{o:?}").chars().take(30).collect()) };
            eprintln!("t={} truth={:?} acc={:.0} step={} {}", f.t, f.truth.map(|x| x.round()), f.acc, i, dv);
        }
        match st.trip_state() {
            TripState::Navigating { remaining_steps, deviation, visual_instruction, .. } => {
                let idx = n - remaining_steps.len(); final_step = idx;
                if matches!(deviation, RouteDeviation::Deviation { .. }) && f.truth.is_none() { off_seen = true; }
                // Banner sichtbar abseits: Anweisung da, App blendet nicht aus, > 80 m neben der Runde
                if f.truth.is_none() && visual_instruction.is_some() && !(app_on && app.off) && project(line, f.p, 0.0, 1e9).d > 80.0 { banner_while_off += 1; }
                if let Some(s) = f.truth {
                    on_n += 1;
                    // veraltet: das Manöver des aktuellen Schritts liegt > 150 m hinter mir
                    if step_end[idx] < s - 150.0 { stale_n += 1; stale_run += f.t - last_t; max_stale = max_stale.max(stale_run); } else { stale_run = 0.0; }
                }
            }
            TripState::Complete { .. } => { if complete_at.is_none() { complete_at = Some(f.truth.unwrap_or(-1.0) / line.len()); final_step = n; } }
            _ => {}
        }
        last_t = f.t;
    }
    // Bezug: erster Fix, der eindeutig gegen die Runde fährt (ein gemeinsamer Start-Stich ist in beide Richtungen gleich)
    let samples: Vec<(P, f64)> = (0..(line.len() / 10.0) as usize).map(|i| { let a = i as f64 * 10.0; (line.at(a), loop_bearing(line, a)) }).collect();
    let clear: Vec<bool> = fixes.iter().map(|f| f.hdg.map_or(false, |h| !samples.iter().any(|(q, b)| dist(*q, f.p) < 25.0 && hdiff(h, *b) <= 120.0))).collect();
    // erster Fix einer Folge von 5 eindeutigen Gegen-Fixes (einzelne Kurven-Fixes zählen nicht)
    let t0 = (0..clear.len().saturating_sub(4)).find(|&i| clear[i..i + 5].iter().all(|&c| c)).map_or(fixes[0].t, |i| fixes[i].t);
    Outcome { wrong_way_s: app.ww.fired_at.map(|x| x - t0), max_progress: app.progress, len: line.len(), max_stale_s: max_stale, stale_pct: 100.0 * stale_n as f64 / on_n.max(1) as f64, complete_at, final_step, steps: n, off_seen, banner_while_off }
}

fn load(path: &str) -> (Route, Line, Vec<f64>) {
    let d: serde_json::Value = serde_json::from_slice(&std::fs::read(path).unwrap()).unwrap();
    let resp = serde_json::json!({"code": "Ok", "routes": [d["route"]], "waypoints": d["waypoints"]});
    let route = OsrmResponseParser::new(6).parse_response(serde_json::to_vec(&resp).unwrap()).unwrap().remove(0);
    // Gesamtlinie aus den Schritt-Geometrien; Schrittende = kumulierte Länge
    let mut pts: Vec<P> = Vec::new(); let mut ends = Vec::new();
    for s in &route.steps {
        for c in &s.geometry { let p = P { lat: c.lat, lng: c.lng }; if pts.last().map_or(true, |q| dist(*q, p) > 0.01) { pts.push(p); } }
        ends.push(pts.len() - 1);
    }
    let line = Line::new(pts);
    let step_end = ends.iter().map(|&i| line.cum[i]).collect();
    (route, line, step_end)
}

fn pass(o: &Outcome, sc: &str) -> bool {
    if sc == "runde_rueckwaerts" {
        // Falschfahrt binnen 20 s (≈ 300 m) ab dem ersten eindeutigen Gegen-Fix erkannt, Fortschritt springt nicht, keine Ankunft
        return o.wrong_way_s.map_or(false, |s| (0.0..=20.0).contains(&s)) && o.max_progress < 0.1 * o.len && o.complete_at.is_none();
    }
    let completes = o.complete_at.map_or(false, |c| c > 0.9);
    let early = o.complete_at.map_or(false, |c| c >= 0.0 && c < 0.9);
    !early && completes && o.max_stale_s <= 20.0 && (o.banner_while_off == 0 || !sc.contains("abweich"))
}

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let strict = args.iter().any(|a| a == "--strict");
    let cfg = args.windows(2).find(|w| w[0] == "--config").map(|w| w[1].clone()).unwrap_or("both".into());
    let files: Vec<&String> = args.iter().filter(|a| a.ends_with(".json")).collect();
    let mut failed = 0;
    println!("datei;konfig;szenario;schritte;erreicht;fertig_bei;max_veraltet_s;veraltet_%;abweichung_erkannt;banner_abseits;falschfahrt_s;ok");
    for f in files {
        let (route, line, step_end) = load(f);
        for sc in SCENARIOS {
            let mut rng = Rng(0x5EED ^ (line.len() as u64));
            let fixes = scenario(sc, &line, &mut rng);
            for (name, c, app) in [("before", config_before(), false), ("nur_dfs", config_dfs(), false), ("after", config_after(), true)] {
                if cfg != "both" && cfg != "all" && cfg != name { continue; }
                if cfg == "both" && name == "nur_dfs" { continue; }
                let o = run(&route, &line, &step_end, &fixes, c, app);
                let ok = pass(&o, sc);
                if name == "after" && !ok { failed += 1; }
                println!("{};{};{};{};{};{};{:.0};{:.1};{};{};{};{}", f.rsplit('/').next().unwrap(), name, sc, o.steps, o.final_step,
                    o.complete_at.map_or("-".into(), |c| format!("{:.0}%", c * 100.0)), o.max_stale_s, o.stale_pct, o.off_seen, o.banner_while_off, o.wrong_way_s.map_or("-".into(), |s| format!("{s:.0}")), if ok { "OK" } else { "FEHLER" });
            }
        }
    }
    if strict && failed > 0 { eprintln!("{failed} Szenario(s) mit Fix-Konfiguration nicht bestanden"); std::process::exit(1); }
}
