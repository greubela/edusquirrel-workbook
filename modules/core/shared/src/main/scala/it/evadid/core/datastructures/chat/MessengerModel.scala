package it.evadid.core.datastructures.chat

import it.evadid.core.datastructures.chat.MessengerModel.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.util.io.serializer.DistributionSerializer
import it.evadid.distribution.*

import java.time.LocalDateTime

case class MessengerModel(messages: List[Message]) {

  def orderedMessages: List[Message] =
    messages.sortBy(_.timestamp)

  def addMessage(message: Message): MessengerModel = {
    val newMessages: List[Message] = orderedMessages :+ message
    MessengerModel(newMessages)
  }

  def addMessage(text: String, author: Person, timestamp: LocalDateTime = LocalDateTime.now()): MessengerModel =
    addMessage(Message(text, author, timestamp))

  def toJson: String = DistributionSerializer.serializerMessageModelJson.serialize(this)
}

object MessengerModel {

  def fromJson(json: String): MessengerModel = DistributionSerializer.serializerMessageModelJson.deserialize(json)

  def testCompletion: MessengerModel = MessengerModel(List(Message("Please write a short poem :)", Person("test", "test", SenderRole.USER, None))))
  

}
