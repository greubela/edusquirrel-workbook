package it.evadid.core.datastructures.chat

import it.evadid.core.datastructures.chat.MessengerModel.*

import java.time.LocalDateTime
import it.evadid.core.util.io.serializer.DefaultSerializer.given

import upickle.default.*

case class Message(text: String, author: ChatParticipant, timestamp: LocalDateTime = LocalDateTime.now()) derives ReadWriter


object Message {

}
