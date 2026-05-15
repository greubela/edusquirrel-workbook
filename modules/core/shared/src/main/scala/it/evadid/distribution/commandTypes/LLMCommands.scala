package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.chat.*
import it.evadid.core.datastructures.chat.MessengerModel.*
import it.evadid.core.datastructures.language.AppLanguage.{Danish, English, German, HumanLanguage}
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.util.io.serializer.DistributionSerializer
import it.evadid.distribution.*
import it.evadid.distribution.command.{ExecutionCommandFactory, ExecutionResult}

object LLMCommands {

  val langPreference: List[HumanLanguage] = List(English, German, Danish)
  val assistantPerson: Person = new Person("assistant", "it.evadid.assistant", SenderRole.AGENT, None)
  val workbookPerson: Person = new Person("workbook", "it.evadid.workbook", SenderRole.WORKBOOK, None)
  val teacherPerson: Person = new Person("teacher", "it.evadid.teacher", SenderRole.TEACHER, None)

  case class MessengerChatCompletionResponse(newTextGenerated: String)

  case class MessengerChatCompletionRequest(systemPrompt: String, messengerModel: MessengerModel) {
    def continueWith(response: String, userEntity: String): MessengerModel = {
      messengerModel.addMessage(response, assistantPerson)
    }
  }

  val completeLLMCommandFactory: ExecutionCommandFactory[MessengerChatCompletionRequest, MessengerModel] =   ExecutionCommandFactory(
    "complete-llm-request",
    DistributionSerializer.serializerChatRequestJson,
    DistributionSerializer.serializerMessageModelJson
  )


}
