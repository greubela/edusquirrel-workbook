from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
import openai
import os
import dotenv
import json
import asyncio
from datetime import datetime
import traceback

# Load .env API key
dotenv.load_dotenv()
openai.api_key = os.getenv("OPENAI_API_KEY")
client = openai.AsyncOpenAI()

app = FastAPI()

# CORS setup
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.post("/chat")
async def chat(request: Request):
    data = await request.json()
    ip = request.client.host or "unknown"
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")

    try:
        prompt_data = data.get("llmPrompt")
        if not prompt_data:
            raise ValueError("Missing 'llmPrompt' in request.")

        system_prompt = prompt_data["systemPrompt"]
        workbook_prompt = prompt_data["workbookPrompt"]
        exercise_prompt = prompt_data["exercisePrompt"]
        student_answer = prompt_data["studentAnswer"]

        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"[Teacher]:\n{workbook_prompt}\n\n{exercise_prompt}"},
            {"role": "user", "content": f"[Student]:\n{student_answer}"}
        ]

        # Log input
        os.makedirs("chatlogs", exist_ok=True)
        log_path = f"chatlogs/chat-{ip}-{timestamp}.log"
        with open(log_path, "w", encoding="utf-8") as log_file:
            json.dump({"messages": messages}, log_file, indent=2, ensure_ascii=False)

        async def generate():
            try:
                stream = await client.chat.completions.create(
                    model="gpt-4",
                    messages=messages,
                    stream=True
                )
                async for chunk in stream:
                    delta = chunk.choices[0].delta
                    if delta and delta.content:
                        yield delta.content
            except Exception as e:
                yield f"[ERROR]: {str(e)}\n{traceback.format_exc()}"

        return StreamingResponse(generate(), media_type="text/plain")

    except Exception as e:
        return StreamingResponse(
            content=(line for line in [f"[ERROR]: {str(e)}"]),
            media_type="text/plain"
        )
