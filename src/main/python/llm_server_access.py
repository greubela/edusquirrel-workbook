from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
import openai
import os
import dotenv
import json
from datetime import datetime
import traceback

from MessengerModel import SenderRole, messenger_model_from_dict

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
        system_prompt = data.get("systemPrompt")
        if not system_prompt:
            raise ValueError("Missing 'systemPrompt' in request.")

        messenger_model_data = data.get("messengerModel")
        if messenger_model_data is None:
            raise ValueError("Missing 'messengerModel' in request.")

        messenger_model = messenger_model_from_dict(messenger_model_data)

        role_map = {
            SenderRole.USER: "user",
            SenderRole.TEACHER: "assistant",
        }

        messages = [{"role": "system", "content": system_prompt}]
        for message in messenger_model.messages:
            role = role_map.get(message.senderRole, "user")
            messages.append({"role": role, "content": message.text})

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
                    stream=True,
                )
                async for chunk in stream:
                    delta = chunk.choices[0].delta
                    if delta and delta.content:
                        yield delta.content
            except Exception as exc:
                yield f"[ERROR]: {str(exc)}\n{traceback.format_exc()}"

        return StreamingResponse(generate(), media_type="text/plain")

    except Exception as exc:
        return StreamingResponse(
            content=(line for line in [f"[ERROR]: {str(exc)}"]),
            media_type="text/plain",
        )
