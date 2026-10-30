import { mergeConfig, defineConfig } from "vitest/config"
import viteConfig from "./vite.config.ts"

export default mergeConfig(viteConfig, defineConfig({
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    css: false,
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "json-summary"],
      reportsDirectory: "coverage",
      include: ["src/lib/**", "src/components/*.tsx", "src/App.tsx"],
      exclude: ["src/components/MapView.tsx", "src/components/theme-provider.tsx", "src/lib/lines.worker.ts", "src/**/*.test.*"],
      thresholds: { lines: 80, functions: 75, statements: 80, branches: 70 },
    },
  },
}))
