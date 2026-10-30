// Dekodiert lines.bin (siehe scripts/compact.py) abseits des Main-Threads.
import { decodeLines } from "./decode"
self.onmessage = async (e: MessageEvent<string>) => {
  try {
    const r = await fetch(e.data); if (!r.ok) throw new Error("lines.bin: HTTP " + r.status)
    postMessage({ lines: decodeLines(await r.arrayBuffer()), done: true })
  } catch (err) { postMessage({ error: String(err) }) }
}
