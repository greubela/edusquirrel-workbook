package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.chat.*
import it.evadid.core.datastructures.chat.MessengerModel.*
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.util.io.serializer.DistributionSerializer
import it.evadid.distribution.*
import it.evadid.distribution.command.{ExecutionCommandFactory, ExecutionResult}

object LLMCommands {


  case class MessengerChatCompletionResponse(newTextGenerated: String)

  case class MessengerChatCompletionRequest(systemPrompt: String, messengerModel: MessengerModel) {
    def continueWith(response: String, userEntity: String): MessengerModel = {
      messengerModel.addMessage(response, BasicPerson(LanguageMap.universalMap(userEntity+"")), SenderRole.AGENT)
    }
  }

  val completeLLMCommandFactory: ExecutionCommandFactory[MessengerChatCompletionRequest, MessengerModel] =   ExecutionCommandFactory(
    "complete-llm-request",
    DistributionSerializer.serializerChatRequestJson,
    DistributionSerializer.serializerMessageModelJson
  )


}
