package it.evadid.core.datastructures.chat

import it.evadid.core.datastructures.chat.SenderRole.USER
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
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

  val pWorkbook = Person("Workbook System", "workbook", SenderRole.SYSTEM, Some("SYS"))
  //val pStudent = Person("Student", "student", SenderRole.USER, Some("ST"))
  val pAgent = Person("AI Agent", "turtle-stitch-helper", SenderRole.AGENT, Some("AI"))
  val pTeacher = Person("Teacher", "teacher", SenderRole.TEACHER, Some("T"))
  val pFallbackStudent = Person("Student", "student", USER, Some("ST"))

  // may not be vals because of the implicit timestamp!
  def prefaceExercise = Message("@assistant: the current exercise reads as follows:", pWorkbook, LocalDateTime.now())

  def prefaceStudentAnswer = Message("@assistant: the student's answer reads as follows. Note that the student might not have finished typing. In this case, just ask whether there are any questions:", pWorkbook, LocalDateTime.now())

  def prefaceGuidelines = Message("@assistant: the teacher explained the reasoning behind the exercise as follows to you:", pWorkbook, LocalDateTime.now())

  def langHint(lang: HumanLanguage) = Message(s"@assistant: Please provide feedback to the student. Do so in ${lang.name} or the language the student used last!", pWorkbook, LocalDateTime.now())

  def getScaffoldingInitMessage(pStudent: Person, exerciseText: String, studentAnswer: String, scaffoldingHints: List[String], curLanguage: HumanLanguage): MessengerModel = {
    val exerciseMsgs = List(prefaceExercise, Message(exerciseText, pTeacher))
    val answerMsgs =
      if (studentAnswer.replace("\\s", "").trim.isEmpty) List()
      else List(prefaceStudentAnswer, Message(studentAnswer, pStudent))
    val guidelines = List(prefaceGuidelines) ++ scaffoldingHints.map(str => Message("@assistant: " + str, pTeacher))
    val langHints = List(langHint(curLanguage))

    MessengerModel(exerciseMsgs ++ guidelines ++ langHints ++ answerMsgs)
  }


}
