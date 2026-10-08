package it.evadid.core.datastructures.chat

import it.evadid.core.datastructures.chat.MessengerModel.*

import java.time.LocalDateTime


case class Message(text: String, author: Person, timestamp: LocalDateTime = LocalDateTime.now())


object Message {
  given upickle.default.ReadWriter[Message] = it.evadid.core.util.io.serializer.DefaultSerializer.rwMessage
}
