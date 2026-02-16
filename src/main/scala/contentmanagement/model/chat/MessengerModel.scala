package contentmanagement.model.chat

import contentmanagement.model.chat.MessengerModel.{Message, SenderRole}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}

case class MessengerModel(messages: List[Message]) {

  def orderedMessages: List[Message] =
    messages.sortBy(_.timestampEpochMillis.toLongOption.getOrElse(0L))

  def addMessage(message: Message): MessengerModel =
    copy(messages = orderedMessages :+ message)

  def addMessage(text: String, author: MessengerModel.Person, senderRole: SenderRole, timestampEpochMillis: Long = System.currentTimeMillis()): MessengerModel =
    addMessage(Message(text, timestampEpochMillis.toString, author, senderRole))
}

object MessengerModel {
  sealed trait Person {
    def name: LanguageMap[HumanLanguage]
    def avatarSvg: Option[String] = None
  }

  case class BasicPerson(name: LanguageMap[HumanLanguage], override val avatarSvg: Option[String] = None) extends Person

  enum SenderRole {
    case USER
    case TEACHER
  }

  case class Message(text: String, timestampEpochMillis: String, author: Person, senderRole: SenderRole = SenderRole.USER)

  def sampleConversationWithStudentAndAgent(): MessengerModel = {
    val student = BasicPerson(LanguageMap.universalMap("Student"))
    val agent = BasicPerson(LanguageMap.universalMap("Agent"))

    val startTime = System.currentTimeMillis() - 20.minutes.toMillis
    val minute = 1.minute.toMillis

    val messages = List(
      Message("Hi! Can you help me with this task?", (startTime + minute * 0).toString, student, SenderRole.USER),
      Message("Of course! What do you need help with?", (startTime + minute * 1).toString, agent, SenderRole.TEACHER),
      Message("I need to understand how loops work.", (startTime + minute * 2).toString, student, SenderRole.USER),
      Message("Great topic. A loop repeats code while a condition is true.", (startTime + minute * 3).toString, agent, SenderRole.TEACHER),
      Message("So a for-loop is for counting steps?", (startTime + minute * 4).toString, student, SenderRole.USER),
      Message("Exactly. A while-loop is better when the number of repetitions is unknown.", (startTime + minute * 5).toString, agent, SenderRole.TEACHER),
      Message("Can you give me a small example?", (startTime + minute * 6).toString, student, SenderRole.USER),
      Message("Sure: for (i <- 1 to 3) println(i) prints 1, 2, 3.", (startTime + minute * 7).toString, agent, SenderRole.TEACHER),
      Message("Nice, that makes sense now.", (startTime + minute * 8).toString, student, SenderRole.USER),
      Message("Awesome! Want a quick practice challenge next?", (startTime + minute * 9).toString, agent, SenderRole.TEACHER)
    )

    MessengerModel(messages)
  }
}
