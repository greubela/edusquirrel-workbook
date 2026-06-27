package it.evadid.core.datastructures.chat

import it.evadid.core.util.io.serializer.DefaultSerializer

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

  def toJson: String = DefaultSerializer.serializerMessageModelJson.serialize(this)
}

object MessengerModel {

  def fromJson(json: String): MessengerModel = DefaultSerializer.serializerMessageModelJson.deserialize(json)

  //def testCompletion: MessengerModel = MessengerModel(List(Message("Please write a short poem :)", Person("test", "test", SenderRole.USER, None))))

  lazy val empty: MessengerModel = MessengerModel(List())

  val pWorkbook = Person("Workbook", "workbook", SenderRole.AGENT, None)
  val pStudent = Person("Student", "student", SenderRole.USER, None)
  val pAgent = Person("Agent", "turtle-stitch-helper", SenderRole.AGENT, None)
  val pTeacher = Person("Teacher", "teacher", SenderRole.USER, None)


  val prefaceExercise = Message("@assistant: the current exercise reads as follows:", pWorkbook)
  val prefaceStudentAnswer = Message("@assistant: the student's answer reads as follows:", pStudent)
  val prefaceGuidelines = Message("@assistant: the teacher gave the following guidelines for giving feedback:", pWorkbook)
  val langHint = Message("@assistant: Please provide feedback in the language the student used last!", pWorkbook)


  def getScaffoldingInitMessage(exerciseText: String, studentAnswer: String, scaffoldingHints: List[String]): MessengerModel = {
    MessengerModel(
      List(
        prefaceExercise,
        Message(exerciseText, pTeacher),
        prefaceStudentAnswer,
        Message(studentAnswer, pStudent),
        prefaceGuidelines
      )
        ++ scaffoldingHints.map(Message(_, pWorkbook))
        ++ List(langHint))
  }


}
