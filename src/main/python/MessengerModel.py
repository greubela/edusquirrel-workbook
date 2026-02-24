import json
from dataclasses import dataclass
from enum import Enum
from typing import Any, List, Optional


class SenderRole(Enum):
    USER = "USER"
    TEACHER = "TEACHER"


@dataclass
class Person:
    name: str
    avatarSvg: Optional[str] = None


@dataclass
class Message:
    text: str
    timestampEpochMillis: str
    author: Person
    senderRole: SenderRole = SenderRole.USER


@dataclass
class MessengerModel:
    messages: List[Message]


def messenger_model_from_dict(payload: dict[str, Any]) -> MessengerModel:
    def parse_message(message_data: dict[str, Any]) -> Message:
        person_data = message_data["author"]
        return Message(
            text=message_data["text"],
            timestampEpochMillis=message_data["timestampEpochMillis"],
            author=Person(
                name=person_data["name"],
                avatarSvg=person_data.get("avatarSvg"),
            ),
            senderRole=SenderRole(message_data.get("senderRole", SenderRole.USER.value)),
        )

    return MessengerModel(messages=[parse_message(message) for message in payload.get("messages", [])])


def messenger_model_from_json(json_string: str) -> MessengerModel:
    payload = json.loads(json_string)
    return messenger_model_from_dict(payload)
