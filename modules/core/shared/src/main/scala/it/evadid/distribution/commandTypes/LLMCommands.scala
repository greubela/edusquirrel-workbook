package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.chat.*
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory

object LLMCommands {


  case class MessengerChatCompletionResponse(newTextGenerated: String) derives upickle.default.ReadWriter

  case class MessengerChatCompletionRequest(systemPrompt: String, messengerModel: MessengerModel) derives upickle.default.ReadWriter {

  }

  val completeLLMCommandFactory: ExecutionCommandFactory[MessengerChatCompletionRequest, Message] = ExecutionCommandFactory(
    "complete-llm-request",
    DefaultSerializer.serializerChatRequestJson,
    DefaultSerializer.serializerMessageJson
  )

  case class FeedbackLlmRequest(prompt: String, systemPrompt: String) derives upickle.default.ReadWriter

  val feedbackLlmCommandFactory: ExecutionCommandFactory[FeedbackLlmRequest, String] =
    ExecutionCommandFactory(
      "feedback-llm-request",
      DefaultSerializer.serializerFeedbackLlmRequestJson,
      DefaultSerializer.serializerStringJson
    )


}
