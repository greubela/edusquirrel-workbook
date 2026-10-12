package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.chat.*
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory
import upickle.default.*

object LLMCommands {

  case class MessengerChatCompletionResponse(newTextGenerated: String)  derives ReadWriter

  case class MessengerChatCompletionRequest(systemPrompt: String, messengerModel: MessengerModel)  derives ReadWriter {

  }

  lazy val completeLLMCommandFactory: ExecutionCommandFactory[MessengerChatCompletionRequest, Message] = ExecutionCommandFactory[MessengerChatCompletionRequest, Message](
    "complete-llm-request"
  )

  case class FeedbackLlmRequest(prompt: String, systemPrompt: String) derives ReadWriter

  lazy val feedbackLlmCommandFactory: ExecutionCommandFactory[FeedbackLlmRequest, String] =
    ExecutionCommandFactory[FeedbackLlmRequest, String](
      "feedback-llm-request"
    )


}
