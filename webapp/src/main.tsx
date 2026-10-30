import { StrictMode, Suspense, lazy, useSyncExternalStore } from "react"
import { createRoot } from "react-dom/client"

import "./index.css"
import App from "./App.tsx"
const AbHier = lazy(() => import("@/components/AbHier"))
const useHash = () => useSyncExternalStore((cb) => { addEventListener("hashchange", cb); return () => removeEventListener("hashchange", cb) }, () => location.hash)
function Root() { return useHash().startsWith("#ab-hier") ? <Suspense fallback={null}><AbHier /></Suspense> : <App /> }
import { ThemeProvider } from "@/components/theme-provider.tsx"

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider>
      <Root />
    </ThemeProvider>
  </StrictMode>
)
