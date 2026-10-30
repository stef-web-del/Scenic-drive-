import "@testing-library/jest-dom/vitest"
import { afterEach, vi } from "vitest"
import { cleanup } from "@testing-library/react"

afterEach(() => cleanup())

// jsdom-Lücken, die Radix/vaul/recharts brauchen
const mm = (q: string) => ({ matches: /min-width: 768px/.test(q) ? (globalThis as { __desktop?: boolean }).__desktop ?? true : false, media: q, onchange: null,
  addEventListener: () => {}, removeEventListener: () => {}, addListener: () => {}, removeListener: () => {}, dispatchEvent: () => false })
Object.defineProperty(window, "matchMedia", { writable: true, value: vi.fn(mm) })
class RO { observe() {} unobserve() {} disconnect() {} }
globalThis.ResizeObserver ??= RO as unknown as typeof ResizeObserver
Element.prototype.scrollIntoView ??= () => {}
Element.prototype.hasPointerCapture ??= () => false
Element.prototype.releasePointerCapture ??= () => {}
URL.createObjectURL ??= () => "blob:x"
URL.revokeObjectURL ??= () => {}
