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
}
