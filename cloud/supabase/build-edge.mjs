// Supabase'e yüklenecek tek dosyalık işlevi üretir (uzak import yok).
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const worker = readFileSync(join(here, "../worker/dakk-worker.js"), "utf8").replace(/^export default worker;\s*$/m, "");
const entry = readFileSync(join(here, "edge-entry.js"), "utf8");
process.stdout.write("// @ts-nocheck\n// Dakk sunucusu (Supabase Edge) - kaynak: cloud/worker/dakk-worker.js + cloud/supabase/edge-entry.js\n" + worker + "\n" + entry);
