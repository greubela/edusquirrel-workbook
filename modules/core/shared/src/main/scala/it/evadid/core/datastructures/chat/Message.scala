package it.evadid.core.datastructures.chat


import it.evadid.core.datastructures.chat.MessengerModel.*

import java.time.Instant
import it.evadid.core.util.io.serializer.DefaultSerializer.given

import upickle.default.*

case class Message(text: String, author: ChatParticipant,
    @upickle.implicits.serializeDefaults(true) timestamp: Instant = Instant.now()) derives ReadWriter


object Message {

}
