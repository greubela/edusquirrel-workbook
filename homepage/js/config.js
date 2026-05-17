// External config file (loaded before any Scala.js bundle).
// Avoids CSP 'unsafe-inline' for inline <script> tags.
//
// LLM requests go to the Scala backend server (POST /api/llm/complete).
// Same contract as the old Cloudflare Worker: { prompt, systemPrompt } -> text/plain.
// Production server: https://ypcgzj23.trafficplex.cloud
// For local dev, override to: http://localhost:9000/api/llm/complete
window.LLM_PROXY_URL =
  "https://ypcgzj23.trafficplex.cloud/api/llm/complete";

// Path to the Pyodide Web Worker. Resolved relative to the page that loads
// this config. All app pages live one folder below the site root, hence "..".
window.PYODIDE_WORKER_URL = "../js/pyodide-worker.js";
