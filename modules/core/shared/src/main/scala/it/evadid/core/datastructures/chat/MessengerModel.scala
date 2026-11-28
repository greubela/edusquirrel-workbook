package it.evadid.core.datastructures.chat


import it.evadid.core.datastructures.chat.SenderRole.USER
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import upickle.ReadWriter

import java.time.Instant
import upickle.default.*

case class MessengerModel(messages: Seq[Message]) derives ReadWriter{

  def orderedMessages: Seq[Message] =
    messages.sortBy(_.timestamp)

  def addMessage(message: Message): MessengerModel = {
    val newMessages: Seq[Message] = (messages :+ message).sortBy(_.timestamp)
    MessengerModel(newMessages)
  }

  def addMessage(text: String, author: ChatParticipant, timestamp: Instant = Instant.now()): MessengerModel =
    addMessage(Message(text, author, timestamp))

}

object MessengerModel {

  def fromJson(json: String): MessengerModel = Serializer.fromUpickleJson(MessengerModel.derived$ReadWriter).deserialize(json)

  //def testCompletion: MessengerModel = MessengerModel(List(Message("Please write a short poem :)", Person("test", "test", SenderRole.USER, None))))

  lazy val empty: MessengerModel = MessengerModel(List())

  val pWorkbook = ChatParticipant("Workbook System", "workbook", SenderRole.SYSTEM, Some("SYS"))
  //val pStudent = Person("Student", "student", SenderRole.USER, Some("ST"))
  val pAgent = ChatParticipant("AI Agent", "turtle-stitch-helper", SenderRole.AGENT, Some("AI"))
  val pTeacher = ChatParticipant("Teacher", "teacher", SenderRole.TEACHER, Some("T"))
  val pFallbackStudent = ChatParticipant("Student", "student", USER, Some("ST"))

  // may not be vals because of the implicit timestamp!
  def prefaceExercise = Message("@assistant: the current exercise reads as follows:", pWorkbook, Instant.now())

  def prefaceStudentAnswer = Message("@assistant: the student's answer reads as follows. Note that the student might not have finished typing. In this case, just ask whether there are any questions:", pWorkbook, Instant.now())

  def prefaceGuidelines = Message("@assistant: the teacher explained the reasoning behind the exercise as follows to you:", pWorkbook, Instant.now())

  def langHint(lang: HumanLanguage) = Message(s"@assistant: Please provide feedback to the student. Do so in ${lang.name} or the language the student used last!", pWorkbook, Instant.now())

  def getScaffoldingInitMessage(pStudent: ChatParticipant, exerciseText: String, studentAnswer: String, scaffoldingHints: Seq[String], curLanguage: HumanLanguage): MessengerModel = {
    val exerciseMsgs = List(prefaceExercise, Message(exerciseText, pTeacher))
    val answerMsgs =
      if (studentAnswer.trim.isEmpty) List()
      else List(prefaceStudentAnswer, Message(studentAnswer, pStudent))
    val guidelines = List(prefaceGuidelines) ++ scaffoldingHints.map(str => Message("@assistant: " + str, pTeacher))
    val langHints = List(langHint(curLanguage))

    MessengerModel(exerciseMsgs ++ guidelines ++ langHints ++ answerMsgs)
  }


}
