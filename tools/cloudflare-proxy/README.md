# Cloudflare Worker Proxy for EduSquirrel

This worker forwards `/api/llm/complete` requests to OpenAI and streams the
response back as plain text. The API key stays on the worker, not in the
browser.

> The EduSquirrel frontend currently points `LLM_PROXY_URL` at the existing
> **`pytutorai-proxy`** deployment from **`ultrich-ea2`** on GitHub.
> A separate deployment is only needed for a different quota, another model,
> or a custom domain.

---

## Prerequisites

| Tool | Install |
|------|---------|
| **Node.js** ≥ 18 | [nodejs.org](https://nodejs.org/) |
| **npm** | comes with Node |
| **Cloudflare account** | [dash.cloudflare.com/sign-up](https://dash.cloudflare.com/sign-up) (free) |
| **OpenAI API key** | [platform.openai.com/api-keys](https://platform.openai.com/api-keys) |

---

## Deploy your own proxy

### 1. Install dependencies

```bash
cd tools/cloudflare-proxy
npm install
```

### 2. Log into Cloudflare

```bash
npx wrangler login
```

### 3. Store your OpenAI key as a Cloudflare secret

```bash
npx wrangler secret put OPENAI_API_KEY
```

Wrangler asks for the key at the prompt. The secret does not appear in this repo.

### 4. (Optional) Pick another model

Edit `wrangler.toml`:

```toml
[vars]
OPENAI_MODEL = "gpt-4o-mini"   # or "gpt-4o", "gpt-4-turbo", ...
```

### 5. Deploy

```bash
npm run deploy
```

Wrangler prints the worker URL:

```
Published edusquirrel-proxy (1.2s)
  https://edusquirrel-proxy.<your-subdomain>.workers.dev
```

### 6. Verify

```bash
curl https://edusquirrel-proxy.<your-subdomain>.workers.dev/health
```

Expected response:

```json
{ "ok": true, "hasKey": true, "model": "gpt-4o-mini" }
```

### 7. Set the frontend URL

Edit [`homepage/js/config.js`](../../homepage/js/config.js):

```js
window.LLM_PROXY_URL =
  "https://edusquirrel-proxy.<your-subdomain>.workers.dev/api/llm/complete";
```

After commit and push, GitHub Pages picks up the new URL on the next deploy.

---

## Local development

```bash
npm run dev
```

Local server: http://localhost:8787

For local runs, store the key in a gitignored `.dev.vars` file:

```env
OPENAI_API_KEY=sk-...
```

Test:

```bash
curl -X POST http://localhost:8787/api/llm/complete \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Say hello in one short sentence.", "systemPrompt": "Be concise."}'
```

---

## API reference

### `GET /health`

```json
{ "ok": true, "hasKey": true, "model": "gpt-4o-mini" }
```

### `POST /api/llm/complete`

Returns the model response as `text/plain`.

Simple request:
```json
{ "prompt": "What is 2+2?", "systemPrompt": "Answer briefly." }
```

Structured request used by EduSquirrel block feedback:
```json
{
  "llmPrompt": {
    "systemPrompt": "You are a tutor.",
    "workbookPrompt": "Exercise context...",
    "exercisePrompt": "Specific task...",
    "studentAnswer": "print('hello')"
  }
}
```

### `OPTIONS *`

CORS preflight (`Access-Control-Allow-Origin: *`).

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `hasKey: false` after deploy | Run `npx wrangler secret put OPENAI_API_KEY` again |
| CORS errors in browser | Check `LLM_PROXY_URL` (no trailing slash, includes `/api/llm/complete`) |
| `OpenAI 401` in stream | Invalid/expired API key — rotate it |
| `OpenAI 429` in stream | OpenAI rate limit — switch to a cheaper model |
| Worker 522/524 | Upstream timeout — try a smaller model or shorter prompt |

---

## Cost

- Cloudflare Workers free tier: 100 000 requests/day
- OpenAI: pay-as-you-go (`gpt-4o-mini` is ~$0.15 / 1M input tokens)
