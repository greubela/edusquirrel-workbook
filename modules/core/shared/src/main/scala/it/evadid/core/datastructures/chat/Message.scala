package it.evadid.core.datastructures.chat

import it.evadid.core.datastructures.chat.MessengerModel.*


case class Message(text: String, timestampEpochMillis: String, author: Person, senderRole: SenderRole = SenderRole.USER)
