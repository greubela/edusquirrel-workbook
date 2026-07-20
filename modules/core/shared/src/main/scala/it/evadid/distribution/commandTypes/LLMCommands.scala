package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.chat.*
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.*
import it.evadid.distribution.command.ExecutionCommandFactory

object LLMCommands {


  case class MessengerChatCompletionResponse(newTextGenerated: String)

  case class MessengerChatCompletionRequest(systemPrompt: String, messengerModel: MessengerModel) {

  }

  val completeLLMCommandFactory: ExecutionCommandFactory[MessengerChatCompletionRequest, Message] = ExecutionCommandFactory(
    "complete-llm-request",
    DefaultSerializer.serializerChatRequestJson,
    DefaultSerializer.serializerMessageJson
  )

  case class FeedbackLlmRequest(prompt: String, systemPrompt: String)

  val feedbackLlmCommandFactory: ExecutionCommandFactory[FeedbackLlmRequest, String] =
    ExecutionCommandFactory(
      "feedback-llm-request",
      DefaultSerializer.serializerFeedbackLlmRequestJson,
      DefaultSerializer.serializerStringJson
    )


}
