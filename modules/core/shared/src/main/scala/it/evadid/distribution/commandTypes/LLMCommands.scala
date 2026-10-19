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
      "feedback-llm-request",
      privacyFilter = Some { payload =>
        def length(field: String): ujson.Value = payload.objOpt.flatMap { fields =>
          fields.get(field).flatMap(_.strOpt).filter(_ != null).map(value => ujson.Num(value.length)).orElse {
            Option.when(fields.get("redacted").contains(ujson.Bool(true)))(fields.get(field + "Length")).flatten.filter {
              case ujson.Num(value) => value >= 0 && value <= Int.MaxValue && value % 1 == 0
              case _ => false
            }
          }
        }.getOrElse(ujson.Null)
        ujson.Obj("redacted" -> true, "promptLength" -> length("prompt"), "systemPromptLength" -> length("systemPrompt"))
      }
    )


}
