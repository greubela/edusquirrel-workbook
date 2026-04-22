/**
 * Cloudflare Worker – lightweight OpenAI streaming proxy for EduSquirrel.
 *
 * Endpoints:
 *   GET  /health            { ok, hasKey, model }
 *   POST /api/llm/complete  streaming text/plain (same contract as the FastAPI proxy)
 *
 * Secret: OPENAI_API_KEY  (set via `npx wrangler secret put OPENAI_API_KEY`)
 * Var:    OPENAI_MODEL    (default gpt-4o-mini, set in wrangler.toml)
 *
 * NOTE: The deployed pytutorai-proxy worker uses the exact same code, so the
 * EduSquirrel frontend can simply point LLM_PROXY_URL at either deployment.
 */

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

function corsJson(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}

function corsStream(readable) {
  return new Response(readable, {
    headers: { ...CORS_HEADERS, "Content-Type": "text/plain; charset=utf-8" },
  });
}

/** Parse the incoming payload (same two shapes as the FastAPI proxy). */
function extractPrompt(data, defaultModel) {
  const model = data.model || defaultModel || "gpt-4o-mini";
  const maxWords = parseInt(data.maxWords, 10) || 70;

  if (data.llmPrompt && typeof data.llmPrompt === "object") {
    const p = data.llmPrompt;
    const systemPrompt = p.systemPrompt || "";
    const userPrompt =
      `[Teacher]:\n${p.workbookPrompt || ""}\n\n${p.exercisePrompt || ""}\n\n[Student]:\n${p.studentAnswer || ""}`.trim();
    return { systemPrompt, userPrompt, model, maxWords };
  }

  const systemPrompt = data.systemPrompt || "";
  const userPrompt = data.prompt;
  if (!userPrompt) throw new Error("Missing 'prompt' (or 'llmPrompt') in request.");
  return { systemPrompt, userPrompt: String(userPrompt), model, maxWords };
}

function buildMessages(systemPrompt, userPrompt) {
  const msgs = [];
  if (systemPrompt) msgs.push({ role: "system", content: systemPrompt });
  msgs.push({ role: "user", content: userPrompt });
  return msgs;
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);

    if (url.pathname === "/health" && request.method === "GET") {
      return corsJson({
        ok: true,
        hasKey: Boolean(env.OPENAI_API_KEY),
        model: env.OPENAI_MODEL || "gpt-4o-mini",
      });
    }

    if (url.pathname === "/api/llm/complete" && request.method === "POST") {
      try {
        if (!env.OPENAI_API_KEY) {
          throw new Error("OPENAI_API_KEY secret is not set.");
        }

        const data = await request.json();
        const { systemPrompt, userPrompt, model } = extractPrompt(
          data,
          env.OPENAI_MODEL,
        );

        const messages = buildMessages(systemPrompt, userPrompt);

        const upstream = await fetch(
          "https://api.openai.com/v1/chat/completions",
          {
            method: "POST",
            headers: {
              Authorization: `Bearer ${env.OPENAI_API_KEY}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({ model, messages, stream: true }),
          },
        );

        if (!upstream.ok) {
          const errText = await upstream.text();
          throw new Error(`OpenAI ${upstream.status}: ${errText.slice(0, 300)}`);
        }

        const { readable, writable } = new TransformStream();
        const writer = writable.getWriter();
        const encoder = new TextEncoder();

        (async () => {
          const reader = upstream.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";

          try {
            while (true) {
              const { done, value } = await reader.read();
              if (done) break;
              buffer += decoder.decode(value, { stream: true });

              const lines = buffer.split("\n");
              buffer = lines.pop() || "";

              for (const line of lines) {
                const trimmed = line.trim();
                if (!trimmed.startsWith("data: ")) continue;
                const payload = trimmed.slice(6);
                if (payload === "[DONE]") continue;
                try {
                  const chunk = JSON.parse(payload);
                  const content = chunk.choices?.[0]?.delta?.content;
                  if (content) {
                    await writer.write(encoder.encode(content));
                  }
                } catch {
                  // skip malformed chunks
                }
              }
            }
          } catch (err) {
            await writer.write(
              encoder.encode(`\n[ERROR]: ${err.message}`),
            );
          } finally {
            await writer.close();
          }
        })();

        return corsStream(readable);
      } catch (err) {
        return corsStream(
          new ReadableStream({
            start(ctrl) {
              ctrl.enqueue(new TextEncoder().encode(`[ERROR]: ${err.message}`));
              ctrl.close();
            },
          }),
        );
      }
    }

    return corsJson({ error: "Not found" }, 404);
  },
};
