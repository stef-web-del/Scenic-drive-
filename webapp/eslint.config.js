import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist', 'coverage', 'playwright-report', 'test-results', '.lighthouseci']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
    rules: {
      'react-refresh/only-export-components': 'off',
      // Daten-Lade-Effekte setzen bewusst Lade-/Reset-Zustände; als Hinweis statt Fehler
      'react-hooks/set-state-in-effect': 'warn',
      // Designsystem: style={{}} nur für Layoutwerte (Breite, Position, Safe-Area, CSS-Variablen).
      // Farben, Schrift und Abstände kommen aus den Tokens (Tailwind-Klassen oder lib/mapTheme.ts).
      'no-restricted-syntax': ['error', {
        selector: "JSXAttribute[name.name='style'] Property[key.type='Identifier'][key.name!=/^(width|height|left|right|top|bottom|transform|paddingTop|paddingBottom|paddingLeft|paddingRight|--[a-z-]+)$/]",
        message: 'style={{}} nur für Layoutwerte (width, left, transform, Safe-Area-Padding …). Farben/Schrift über Tokens.',
      }, {
        selector: "JSXAttribute[name.name='style'] Property[key.type='Literal'][key.value!=/^--/]",
        message: 'style={{}} nur für Layoutwerte. Farben/Schrift über Tokens.',
      }],
    },
  },
  // Vendored shadcn-Bausteine setzen Farben über CSS-Variablen der Tokens (z. B. --normal-bg)
  { files: ['src/components/ui/**'], rules: { 'no-restricted-syntax': 'off' } },
  // Playwright-Fixtures heißen `use(...)` – keine React-Hooks
  { files: ['e2e/**/*.ts'], rules: { 'react-hooks/rules-of-hooks': 'off' } },
])
