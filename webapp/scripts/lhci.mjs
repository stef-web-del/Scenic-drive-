// Lighthouse CI (mobil) gegen dist/ – nutzt das von Playwright installierte Chromium, falls CHROME_PATH fehlt.
import { chromium } from "@playwright/test"
import { execSync } from "node:child_process"
const env = { ...process.env, CHROME_PATH: process.env.CHROME_PATH || chromium.executablePath() }
execSync("npx lhci autorun", { stdio: "inherit", env })
