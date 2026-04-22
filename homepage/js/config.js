// External config file (loaded before any Scala.js bundle).
// Avoids CSP 'unsafe-inline' for inline <script> tags.
//
// We currently reuse the existing PyTutorAI Cloudflare Worker because it ships
// the exact same /api/llm/complete contract. To use a dedicated EduSquirrel
// worker, deploy tools/cloudflare-proxy/ and replace the URL below.
window.LLM_PROXY_URL =
  "https://pytutorai-proxy.ultrichedima.workers.dev/api/llm/complete";

// Path to the Pyodide Web Worker. Resolved relative to the page that loads
// this config. All app pages live one folder below the site root, hence "..".
window.PYODIDE_WORKER_URL = "../js/pyodide-worker.js";
