#!/usr/bin/env python3
"""A15: Routentexte in vorhandenen Katalogdaten auf deutsches Zahlenformat umstellen (nur Text).
Entspricht der Ausgabe von scenic/character.py:describe() mit de_num(). Ändert ausschließlich die
Zahlen in den Generator-Sätzen; danach wird jede Datei geparst und geprüft, dass sich nur Strings ändern."""
import json, re, sys, pathlib
def th(n): return f"{int(n):,}".replace(",", ".")
RULES = [
    (re.compile(r"\((\d+)\.(\d) km, "), lambda m: f"({th(m[1])},{m[2]} km, "),
    (re.compile(r"(?<![\d.,])(\d{4,}) km in etwa (\d+) Minuten"), lambda m: f"{th(m[1])} km in etwa {th(m[2])} Minuten"),
    (re.compile(r"in etwa (\d{4,}) Minuten"), lambda m: f"in etwa {th(m[1])} Minuten"),
    (re.compile(r"(?<![\d.,])(\d{4,}) Höhenmeter zwischen"), lambda m: f"{th(m[1])} Höhenmeter zwischen"),
    (re.compile(r"Höhenmeter zwischen (\d+) und (\d+) m\."), lambda m: f"Höhenmeter zwischen {th(m[1])} und {th(m[2])} m."),
    (re.compile(r"(ab|bei) km (\d{4,})"), lambda m: f"{m[1]} km {th(m[2])}"),
]
def fix(s):
    for rx, f in RULES: s = rx.sub(f, s)
    return s
def same_but_strings(a, b, path="$", diffs=None):
    if isinstance(a, dict):
        assert isinstance(b, dict) and list(a) == list(b), path
        for k in a: same_but_strings(a[k], b[k], f"{path}.{k}", diffs)
    elif isinstance(a, list):
        assert isinstance(b, list) and len(a) == len(b), path
        for i, (x, y) in enumerate(zip(a, b)): same_but_strings(x, y, f"{path}[{i}]", diffs)
    elif isinstance(a, str):
        assert isinstance(b, str), path
        if a != b: diffs.append(path)
    else:
        assert a == b and type(a) is type(b), f"Nicht-Text geändert: {path}"
dry = "--dry" in sys.argv
roots = [a for a in sys.argv[1:] if not a.startswith("--")]
files = changed = strings = 0
for root in roots:
    for p in pathlib.Path(root).rglob("*.json"):
        raw = p.read_text(encoding="utf-8")
        if "Höhenmeter zwischen" not in raw and "Stärkster Abschnitt" not in raw: continue
        files += 1; new = fix(raw)
        if new == raw: continue
        d = []; same_but_strings(json.loads(raw), json.loads(new), diffs=d)
        changed += 1; strings += len(d)
        if not dry: p.write_text(new, encoding="utf-8")
print(f"Dateien mit Routentexten: {files}, geändert: {changed}, geänderte Strings: {strings}, nur Text: ja{' (Probelauf)' if dry else ''}")
