package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.chat.*
import it.evadid.distribution.command.ExecutionCommandFactory
import upickle.default.*
import it.evadid.llm.LlmConfiguration

object LLMCommands {

  case class MessengerChatCompletionResponse(newTextGenerated: String)  derives ReadWriter

  case class MessengerChatCompletionRequest(systemPrompt: String, messengerModel: MessengerModel,
      llmConfiguration: Option[LlmConfiguration] = None) derives ReadWriter

  lazy val completeLLMCommandFactory: ExecutionCommandFactory[MessengerChatCompletionRequest, Message] = ExecutionCommandFactory[MessengerChatCompletionRequest, Message](
    "complete-llm-request", privacyFilter = Some(_ => ujson.Obj("redacted" -> true))
  )

  case class FeedbackLlmRequest(prompt: String, systemPrompt: String,
      llmConfiguration: Option[LlmConfiguration] = None, programmingHint: Boolean = false)

  object FeedbackLlmRequest {
    private val fields: ReadWriter[FeedbackLlmRequest] = macroRW

    /** Reading requires both prompts as JSON strings; upickle alone would also accept null, numbers and booleans.
      * A malformed request fails with a fixed message and no cause, since decoder errors would quote the learner's
      * prompt into logs and responses.
      */
    given ReadWriter[FeedbackLlmRequest] = readwriter[ujson.Value].bimap[FeedbackLlmRequest](writeJs(_)(using fields), json =>
      Option.when(json.objOpt.exists(fields => List("prompt", "systemPrompt").forall(field => fields.get(field).exists(_.strOpt.nonEmpty))))(json)
        .flatMap(valid => scala.util.Try(read[FeedbackLlmRequest](valid)(using fields)).toOption)
        .getOrElse(throw IllegalArgumentException("Invalid feedback request.")))
  }

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
