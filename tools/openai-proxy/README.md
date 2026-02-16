# OpenAI Proxy

Local FastAPI proxy for calling OpenAI from the Scala.js web app.

**Why a proxy?**
- Browsers must not call OpenAI directly (API key leakage, CORS).
- The web app uses `fetch()` to call this proxy, which then calls OpenAI.

## Quick start

```bash
cd tools/openai-proxy
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Create a local `.env` (NOT committed):

```bash
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini
```

Run:

```bash
python app.py
```

Health check: http://localhost:8000/health

> Note: `/health` only reports whether a key is present (`hasKey`). It does not validate whether the key is correct.

## Local vs. cloud usage

### Local (recommended for development)

1) Start the proxy locally (see Quick start).
2) In the **frontend**, set the LLM proxy URL (or keep the default localhost).

Default if not set:

```
http://localhost:8000/api/llm/complete
```

In **Node/SSR environments**, the frontend also tries to load `LLM_PROXY_URL` from a `.env`
file (first `tools/openai-proxy/.env`, then project root `.env`) before falling back to the default.
In the browser, use `window.LLM_PROXY_URL` instead.

### Cloud server

**On the server:**

1) Install and configure the proxy exactly as in the local setup.
2) Provide `.env` with at least:

```bash
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini
```

3) Start the proxy on a public interface:

```bash
uvicorn app:app --host 0.0.0.0 --port 8000
# or
python app.py
```

4) (Recommended) Put a reverse proxy (Nginx/Traefik) in front and use HTTPS.

**In the frontend:**

- `LLM_PROXY_URL` → `https://YOUR_DOMAIN/api/llm/complete`

If you use the ML endpoints (mini-ML):

- `BlockFeedbackConfig.mlLogUrl` → `https://YOUR_DOMAIN/api/ml/log-example`
- `BlockFeedbackConfig.mlModelUrl` → `https://YOUR_DOMAIN/api/ml/model`

Otherwise the app will try to reach `127.0.0.1` and fail on other machines.

### CORS (optional)

By default the proxy allows all origins. If you want to lock it down, edit
`tools/openai-proxy/app.py` and set `allow_origins` to your domain.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| POST | /api/llm/complete | Main LLM endpoint (streaming text/plain) |
| POST | /chat | Alias for older clients |
| GET | /health | Health + key presence |
| POST | /api/ml/log-example | Append a JSONL training example |
| GET | /api/ml/model | Serve exported mini‑ML model JSON |

Logs are stored in:
- `tools/openai-proxy/chatlogs/`
- `tools/openai-proxy/ml-logs/`

## Request formats

**Simple**

```json
{
  "prompt": "prompt text",
  "systemPrompt": "Optional system prompt",
  "model": "gpt-4o-mini",
  "maxWords": 70
}
```

**Compatibility**

```json
{
  "llmPrompt": {
    "systemPrompt": "...",
    "workbookPrompt": "...",
    "exercisePrompt": "...",
    "studentAnswer": "..."
  }
}
```

## Configuration

### Proxy (`tools/openai-proxy/.env`)

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| OPENAI_API_KEY | yes | - | OpenAI API key |
| OPENAI_MODEL | no | gpt-4o-mini | Model name |
| UPSTREAM_TIMEOUT_S | no | 25 | Timeout for upstream OpenAI requests |
| HOST | no | 127.0.0.1 | Bind host for uvicorn |
| PORT | no | 8000 | Bind port for uvicorn |

> The proxy reads `.env` from `tools/openai-proxy/.env` and `tools/openai-proxy/.venv/.env` (via python-dotenv).

### Frontend (LLM endpoint selection)

- `LLM_PROXY_URL` (optional)
  - Node/SSR: read from `.env` (first `tools/openai-proxy/.env`, then repo `.env`).
  - Browser: set `window.LLM_PROXY_URL`.

## Notes

- On a different machine, users still need to add their own key once (you cannot safely ship a key inside the repo).
- To change the model: set `OPENAI_MODEL` in `.env`.
