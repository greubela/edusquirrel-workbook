from __future__ import annotations

import asyncio
import json
import os
import re
import traceback
from datetime import datetime
from typing import Any, AsyncIterator, Dict, Optional

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse

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


def _safe_log_tag(tag: Optional[str]) -> Optional[str]:
    if not tag:
        return None
    t = str(tag).strip().lower()
    if not t:
        return None
    # Replace non-filename chars with underscore and cap length.
    t = re.sub(r"[^a-z0-9._-]+", "_", t)
    t = t.strip("_-")
    if not t:
        return None
    return t[:64]


def _ensure_logs_dir() -> str:
    path = os.path.join(os.path.dirname(__file__), "chatlogs")
    os.makedirs(path, exist_ok=True)
    return path


def _ensure_ml_logs_dir() -> str:
    path = os.path.join(os.path.dirname(__file__), "ml-logs")
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

# Set DETAILED_LOGGING=true to include studentCode, debugMeta and the full prompt in chat logs.
DETAILED_LOGGING: bool = os.getenv("DETAILED_LOGGING", "false").strip().lower() in ("1", "true", "yes")

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


def _safe_extract_json(text: str) -> Any:
    """Best-effort extraction of a JSON value from model text."""

    t = (text or "").strip()
    if not t:
        raise ValueError("Empty response")

    # Fast path: full JSON
    try:
        return json.loads(t)
    except Exception:
        pass

    # Heuristic: find first JSON array/object span
    start = None
    for i, ch in enumerate(t):
        if ch in "[{":
            start = i
            break
    if start is None:
        raise ValueError("No JSON found in response")

    end = None
    for j in range(len(t) - 1, start, -1):
        if t[j] in "]}":
            end = j + 1
            break
    if end is None:
        raise ValueError("No JSON end found in response")

    return json.loads(t[start:end])


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

        raw_tag = data.get("logTag") or data.get("exerciseId")
        if raw_tag is None and isinstance(data.get("llmPrompt"), dict):
            raw_tag = data.get("llmPrompt", {}).get("exerciseId")

        log_tag = _safe_log_tag(raw_tag) or "chat"

        student_code_raw = data.get("studentCode") or ""
        student_code = student_code_raw.splitlines()
        debug_meta = data.get("debugMeta") or {}

        logs_dir = _ensure_logs_dir()
        log_path = os.path.join(logs_dir, f"{log_tag}-{ip}-{timestamp}.json")

        async def generate() -> AsyncIterator[str]:
            parts: list[str] = []
            try:
                async for chunk in _stream_chat_completion(messages, model=model):
                    parts.append(chunk)
                    yield chunk
                text = "".join(parts)
                entry: Dict[str, Any] = {
                    "timestamp": timestamp,
                    "model": model,
                    "logTag": raw_tag,
                    "response": text.splitlines(),
                    "wordCount": len([w for w in text.split() if w]),
                    "maxWordsRequested": max_words,
                }
                if DETAILED_LOGGING:
                    entry["studentCode"] = student_code
                    entry["debugMeta"] = debug_meta
                    entry["prompt"] = {"system": system_prompt.splitlines(), "user": user_prompt.splitlines()}
                _safe_write_json(log_path, entry)
            except Exception as e:
                err_text = f"[ERROR]: {str(e)}\n{traceback.format_exc()}"
                entry = {
                    "timestamp": timestamp,
                    "model": model,
                    "logTag": raw_tag,
                    "error": str(e),
                    "trace": traceback.format_exc(),
                }
                if DETAILED_LOGGING:
                    entry["studentCode"] = student_code
                    entry["debugMeta"] = debug_meta
                    entry["prompt"] = {"system": system_prompt, "user": user_prompt}
                _safe_write_json(log_path, entry)
                yield err_text

        return StreamingResponse(generate(), media_type="text/plain")

    except Exception as e:
        async def err() -> AsyncIterator[str]:
            yield f"[ERROR]: {str(e)}"

        return StreamingResponse(err(), media_type="text/plain")


@app.post("/api/ml/log-example")
async def ml_log_example(request: Request) -> Dict[str, Any]:
    """Append one JSON object as a single JSONL line.

    Intended for collecting offline-training examples from the Scala.js app.
    """

    ip = _safe_client_ip(request)

    try:
        data = await request.json()
        if not isinstance(data, dict):
            raise ValueError("Expected JSON object")

        # Enrich minimally for debugging / provenance.
        data.setdefault("clientIp", ip)
        data.setdefault("receivedAtUtc", datetime.utcnow().isoformat() + "Z")

        logs_dir = _ensure_ml_logs_dir()
        path = os.path.join(logs_dir, "training.jsonl")
        with open(path, "a", encoding="utf-8") as f:
            f.write(json.dumps(data, ensure_ascii=False) + "\n")

        return {"ok": True}
    except Exception as e:
        return {"ok": False, "error": str(e)}


@app.get("/api/ml/model")
async def ml_model() -> JSONResponse:
    """Serve the exported model JSON for Scala.js to fetch."""

    path = os.path.join(os.path.dirname(__file__), "ml-model.json")
    if not os.path.exists(path):
        return JSONResponse({"ok": False, "error": "ml-model.json not found"}, status_code=404)

    try:
        with open(path, "r", encoding="utf-8") as f:
            obj = json.load(f)
        return JSONResponse(obj)
    except Exception as e:
        return JSONResponse({"ok": False, "error": str(e)}, status_code=500)



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
