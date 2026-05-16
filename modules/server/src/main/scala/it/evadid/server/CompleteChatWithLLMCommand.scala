package it.evadid.server

import it.evadid.core.datastructures.chat.{MessengerModel, SenderRole}
import it.evadid.distribution.commandTypes.LLMCommands.MessengerChatCompletionRequest
import it.evadid.util.Logger
import play.api.libs.json.Json

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}

object CompleteChatWithLLMCommand {

  private val openAiHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

  private val apiModel: String = it.evadid.util.JvmUtils.env("OPENAI_MODEL").getOrElse("gpt-4o-mini")

  val apiKeyOp: Option[String] = it.evadid.util.JvmUtils.env("OPENAI_API_KEY")

  /** Takes prompt and systemPrompt, returns plain text. Used by /api/llm/complete. */
  def completePlain(prompt: String, systemPrompt: String): Future[String] =
    if apiKeyOp.isEmpty then Future.failed(new RuntimeException("OPENAI_API_KEY is not set"))
    else Future {
      val messages = Json.arr(
        Json.obj("role" -> "system", "content" -> systemPrompt),
        Json.obj("role" -> "user",   "content" -> prompt)
      )
      val payload = Json.obj("model" -> apiModel, "messages" -> messages).toString()
      val req = HttpRequest.newBuilder()
        .uri(URI.create("https://api.openai.com/v1/chat/completions"))
        .timeout(Duration.ofSeconds(60))
        .header("Authorization", s"Bearer ${apiKeyOp.get}")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()
      val resp = openAiHttpClient.send(req, HttpResponse.BodyHandlers.ofString())
      if resp.statusCode() / 100 != 2 then
        throw new RuntimeException(s"OpenAI API error ${resp.statusCode()}: ${resp.body()}")
      val json = Json.parse(resp.body())
      (json \ "choices").asOpt[play.api.libs.json.JsArray]
        .flatMap(_.value.headOption)
        .flatMap(c => (c \ "message" \ "content").asOpt[String])
        .getOrElse(throw new RuntimeException("OpenAI response missing choices[0].message.content"))
    }(using ExecutionContext.global)

  def handleLlmChatRequest(mesRequest: MessengerChatCompletionRequest, logger: Logger): Future[MessengerModel] = {
    if (apiKeyOp.isEmpty) Future.failed(new RuntimeException("OPENAI_API_KEY is not set"))
    else Future {
      val apiKey = apiKeyOp.get
      logger.logInfo(s"apiKey is present on server!")
      logger.logInfo(s"sending request to OpenAI API (model $apiModel)")
      val orderedMessages = mesRequest.messengerModel.orderedMessages.map { msg =>
        Json.obj(
          "role" -> msg.author.role.llmName,
          "content" -> msg.text
        )
      }
      // Build a flat JSON array for OpenAI: [systemMessage, message1, message2, ...]
      val messagesJson = Json.arr(
        Json.obj("role" -> "system", "content" -> mesRequest.systemPrompt)
      ) ++ play.api.libs.json.JsArray(orderedMessages)

      val payload = Json.obj("model" -> apiModel, "messages" -> messagesJson).toString()
      logger.logInfo(s"sending messages (json): $messagesJson")

      val request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.openai.com/v1/chat/completions"))
        .timeout(Duration.ofSeconds(60))
        .header("Authorization", s"Bearer ${apiKey}")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()

      val response = openAiHttpClient.send(request, HttpResponse.BodyHandlers.ofString())


      if response.statusCode() / 100 != 2 then {
        logger.logError(s"OpenAI API error ${response.statusCode()}: ${response.body()}")
        throw new RuntimeException(s"OpenAI API error ${response.statusCode()}: ${response.body()}")
      } else {
        logger.logInfo(s"received response from OpenAI API with stuatus code: ${response.statusCode()}")
      }

      val json = Json.parse(response.body())
      val generatedText = (json \ "choices").asOpt[play.api.libs.json.JsArray]
        .flatMap(_.value.headOption)
        .flatMap(choice => (choice \ "message" \ "content").asOpt[String])
        .getOrElse(throw new RuntimeException("OpenAI response did not contain choices[0].message.content"))

      logger.logInfo(s"received generated text: $generatedText")

      mesRequest.continueWith(generatedText, "agent")
    }(using ExecutionContext.global)
  }

}


/*


case class CompleteChatWithLLMCommand(apiKey: String, model: String) extends ExecutableCommand[MessengerChatCompletionRequest] {



  override def handleExecution(data: MessengerChatCompletionRequest): ExecutionResult = {

   *)



  }

}
*/
