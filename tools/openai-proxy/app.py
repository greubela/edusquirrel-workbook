from __future__ import annotations

import asyncio
import json
import os
import traceback
from datetime import datetime
from typing import Any, AsyncIterator, Dict, Optional

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

try:
    from dotenv import load_dotenv

    here = os.path.dirname(__file__)
    load_dotenv(os.path.join(here, ".env"))
    load_dotenv(os.path.join(here, ".venv", ".env"))
    load_dotenv()
except Exception:
    pass

try:
    from openai import AsyncOpenAI
except Exception as e:
    raise RuntimeError(
        "Missing dependency 'openai'. Run: pip install -r requirements.txt"
    ) from e


def _now_stamp() -> str:
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def _safe_client_ip(request: Request) -> str:
    try:
        return request.client.host or "unknown"
    except Exception:
        return "unknown"


def _ensure_logs_dir() -> str:
    path = os.path.join(os.path.dirname(__file__), "chatlogs")
    os.makedirs(path, exist_ok=True)
    return path


def _safe_write_json(path: str, payload: Dict[str, Any]) -> None:
    try:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(payload, f, indent=2, ensure_ascii=False)
    except Exception:
        pass


def _word_limit(text: str, max_words: int) -> str:
    if max_words <= 0:
        return ""
    words = [w for w in text.split() if w]
    if len(words) <= max_words:
        return text
    return " ".join(words[:max_words])


OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

UPSTREAM_TIMEOUT_S = float(os.getenv("UPSTREAM_TIMEOUT_S", "25"))

client = AsyncOpenAI(api_key=OPENAI_API_KEY)

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def _extract_prompt_payload(data: Dict[str, Any]) -> Dict[str, Any]:
    """Accept multiple payload shapes for compatibility.

    Supported:
      1) {"prompt": "...", "systemPrompt": "...", "model": "..."}
      2) {"llmPrompt": {"systemPrompt":..., "workbookPrompt":..., "exercisePrompt":..., "studentAnswer":...}}

    Returns dict with keys: system_prompt, user_prompt, model, max_words
    """

    model = data.get("model") or OPENAI_MODEL
    max_words = int(data.get("maxWords") or 70)

    if "llmPrompt" in data and isinstance(data["llmPrompt"], dict):
        p = data["llmPrompt"]
        system_prompt = p.get("systemPrompt", "")
        workbook_prompt = p.get("workbookPrompt", "")
        exercise_prompt = p.get("exercisePrompt", "")
        student_answer = p.get("studentAnswer", "")

        user_prompt = f"[Teacher]:\n{workbook_prompt}\n\n{exercise_prompt}\n\n[Student]:\n{student_answer}".strip()
        return {
            "system_prompt": system_prompt,
            "user_prompt": user_prompt,
            "model": model,
            "max_words": max_words,
        }

    # simple prompt mode
    system_prompt = data.get("systemPrompt", "")
    user_prompt = data.get("prompt")
    if not user_prompt:
        raise ValueError("Missing 'prompt' (or 'llmPrompt') in request.")

    return {
        "system_prompt": system_prompt,
        "user_prompt": str(user_prompt),
        "model": model,
        "max_words": max_words,
    }


async def _stream_chat_completion(messages: list[dict[str, str]], model: str) -> AsyncIterator[str]:
    """Yields plain text chunks."""

    async def _start_stream():
        return await client.chat.completions.create(
            model=model,
            messages=messages,
            stream=True,
        )

    stream = await asyncio.wait_for(_start_stream(), timeout=UPSTREAM_TIMEOUT_S)

    async for chunk in stream:
        try:
            delta = chunk.choices[0].delta
            if delta and getattr(delta, "content", None):
                yield delta.content
        except Exception:
            continue


def _messages(system_prompt: str, user_prompt: str) -> list[dict[str, str]]:
    msgs: list[dict[str, str]] = []
    if system_prompt:
        msgs.append({"role": "system", "content": system_prompt})
    msgs.append({"role": "user", "content": user_prompt})
    return msgs


@app.get("/health")
async def health() -> Dict[str, Any]:
    return {
        "ok": True,
        "hasKey": bool(OPENAI_API_KEY),
        "model": OPENAI_MODEL,
    }


@app.post("/api/llm/complete")
async def complete(request: Request):
    ip = _safe_client_ip(request)
    timestamp = _now_stamp()

    try:
        if not OPENAI_API_KEY:
            raise ValueError("OPENAI_API_KEY is not set. Create tools/openai-proxy/.env")

        data = await request.json()
        payload = _extract_prompt_payload(data)

        system_prompt = payload["system_prompt"]
        user_prompt = payload["user_prompt"]
        model = payload["model"]
        max_words = int(payload["max_words"])

        messages = _messages(system_prompt, user_prompt)

        logs_dir = _ensure_logs_dir()
        log_path = os.path.join(logs_dir, f"chat-{ip}-{timestamp}.json")
        _safe_write_json(log_path, {"model": model, "messages": messages})

        response_log_path = os.path.join(logs_dir, f"chat-{ip}-{timestamp}-response.json")

        async def generate() -> AsyncIterator[str]:
            parts: list[str] = []
            try:
                async for chunk in _stream_chat_completion(messages, model=model):
                    parts.append(chunk)
                    yield chunk
                text = "".join(parts)
                _safe_write_json(
                    response_log_path,
                    {
                        "model": model,
                        "response": text,
                        "wordCount": len([w for w in text.split() if w]),
                        "maxWordsRequested": max_words,
                    },
                )
            except Exception as e:
                err_text = f"[ERROR]: {str(e)}\n{traceback.format_exc()}"
                _safe_write_json(
                    response_log_path,
                    {
                        "model": model,
                        "error": str(e),
                        "trace": traceback.format_exc(),
                    },
                )
                yield err_text

        return StreamingResponse(generate(), media_type="text/plain")

    except Exception as e:
        async def err() -> AsyncIterator[str]:
            yield f"[ERROR]: {str(e)}"

        return StreamingResponse(err(), media_type="text/plain")



@app.post("/chat")
async def chat(request: Request):
    return await complete(request)


if __name__ == "__main__":
    try:
        import uvicorn  # type: ignore
    except Exception as e:
        raise RuntimeError(
            "uvicorn is required to run this FastAPI app. "
            "Install it via: pip install -r requirements.txt"
        ) from e

    host = os.getenv("HOST", "127.0.0.1")
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run(app, host=host, port=port, log_level="info")
