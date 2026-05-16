// External config file (loaded before any Scala.js bundle).
// Avoids CSP 'unsafe-inline' for inline <script> tags.
//
// LLM requests go to the Scala backend server (POST /api/llm/complete).
// Same contract as the old Cloudflare Worker: { prompt, systemPrompt } -> text/plain.
// For local dev: sbt buildServerFast, then server runs on port 9000.
// For production: replace with the deployed server URL.
window.LLM_PROXY_URL =
  "http://localhost:9000/api/llm/complete";

// Path to the Pyodide Web Worker. Resolved relative to the page that loads
// this config. All app pages live one folder below the site root, hence "..".
window.PYODIDE_WORKER_URL = "../js/pyodide-worker.js";
