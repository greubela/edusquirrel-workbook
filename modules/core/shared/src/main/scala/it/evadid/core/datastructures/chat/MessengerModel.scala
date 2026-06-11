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

  lazy val turtleStitchHelperExample: MessengerModel = {
    val pWorkbook = Person("Workbook", "workbook", SenderRole.AGENT, None)
    val pStudent = Person("Student", "student", SenderRole.USER, None)
    val pAgent = Person("Agent", "turtle-stitch-helper", SenderRole.AGENT, None)
    val pTeacher = Person("Teacher", "teacher", SenderRole.USER, None)

    val messages: List[Message] = List(
      Message("@assistant: the current exercise reads as follows:", pWorkbook),
      Message("Describe how you could create a pentagon using the techniques you currently know and argue why this approach does not scale well when further increasing the number of corners.", pTeacher),
      Message("@assistant: the textarea for the student submitted solution reads as follows:", pWorkbook),
      Message("Because I have to change the code again and it can not stay the same way.", pStudent),
      Message("@assistant: the teacher gave the following guidelines for giving feedback:", pWorkbook),
      Message("The central goal is for the students to understand that they should not just keep on adding new lines at the bottom of the program (and adjusting the angles) when going from a square to a pentagon etc. The next exercise will introduce loops. While helping the student, please do not give this away the solution but rather ask questions like 'what would you need to change', or 'how many things would you need to change if you´d add another corner', 'how about if you then add another', ... to guide them in the correct direction", pTeacher),
      Message("@assistant: Please provide feedback in ENGLISH.", pWorkbook)
    )
    MessengerModel(messages)
  }

}
