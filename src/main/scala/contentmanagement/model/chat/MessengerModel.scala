package contentmanagement.model.chat


import contentmanagement.model.chat.MessengerModel.Message
import contentmanagement.model.language.{HumanLanguage, LanguageMap}


case class MessengerModel(messages: List[Message]) {

}

object MessengerModel {
  sealed trait Person {
    def name: LanguageMap[HumanLanguage]
  }

  case class Message(text: String, timestampEpochMillis: String, author: Person)

}