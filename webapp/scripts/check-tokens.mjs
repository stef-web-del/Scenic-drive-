#!/usr/bin/env node
// Designsystem-Wächter: keine Farbwerte und keine Tailwind-Standardpaletten außerhalb der Token-Dateien.
// Quelle aller Farben: design/tokens.json → app/tools/gen_tokens.py → src/tokens.css, src/lib/mapTheme.ts.
import { readdirSync, readFileSync, statSync } from "node:fs"
import { join, relative } from "node:path"

const ROOT = new URL("../src/", import.meta.url).pathname
// components/ui/chart.tsx: shadcn-Selektoren auf Recharts-Standardfarben (stroke="#ccc"), keine eigenen Farben.
const ALLOW = new Set(["tokens.css", "lib/mapTheme.ts", "lib/terms.ts", "components/ui/chart.tsx"])
const PALETTE = "slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose|white|black"
const RULES = [
  [/#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3,5})?\b(?![-\w])/g, "Hex-Farbe"],
  [/\b(?:rgba?|hsla?|oklch)\(/g, "Farbfunktion"],
  [new RegExp(String.raw`\b(?:bg|text|border|fill|stroke|ring|from|via|to|outline|decoration|shadow|accent|caret)-(?:${PALETTE})(?:-\d{2,3})?\b`, "g"), "Tailwind-Standardfarbe"],
]
const files = []
const walk = (d) => { for (const f of readdirSync(d)) { const p = join(d, f); if (statSync(p).isDirectory()) walk(p); else if (/\.(tsx?|css)$/.test(f)) files.push(p) } }
walk(ROOT)
let bad = 0
for (const f of files) {
  const rel = relative(ROOT, f)
  if (ALLOW.has(rel) || /\.test\.tsx?$/.test(rel)) continue
  readFileSync(f, "utf8").split("\n").forEach((line, i) => {
    if (line.includes("tokens-ok")) return
    for (const [re, what] of RULES) for (const m of line.matchAll(re)) { bad++; console.log(`${rel}:${i + 1}: ${what} „${m[0]}“`) }
  })
}
if (bad) { console.log(`\n${bad} Fund(e). Farben bitte über Tokens (Tailwind-Klassen, var(--sr-*), lib/mapTheme.ts).`); process.exit(1) }
console.log(`Tokens ok (${files.length} Dateien geprüft).`)
