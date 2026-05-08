package it.evadid.server

import it.evadid.core.datastructures.chat.{MessengerModel, SenderRole}
import it.evadid.distribution.command.*
import it.evadid.distribution.commandTypes.LLMCommands.MessengerChatCompletionRequest
import it.evadid.util.Logger
import play.api.libs.json.Json

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}

object CompleteChatWithLLMCommand {

  private val openAiHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

  def handleLlmChatRequest(mesRequest: MessengerChatCompletionRequest, logger: Logger): Future[MessengerModel] = Future {

    val orderedMessages = mesRequest.messengerModel.orderedMessages.map { msg =>
      Json.obj(
        "role" -> (if msg.senderRole == SenderRole.USER then "user" else "assistant"),
        "content" -> msg.text
      )
    }

    // Build a flat JSON array for OpenAI: [systemMessage, message1, message2, ...]
    val messagesJson = Json.arr(
      Json.obj("role" -> "system", "content" -> mesRequest.systemPrompt)
    ) ++ play.api.libs.json.JsArray(orderedMessages)

    val payload = Json.obj("model" -> it.evadid.util.JvmUtils.envOrError("OPENAI_MODEL"), "messages" -> messagesJson).toString()

    val apiKey = it.evadid.util.JvmUtils.envOrError("OPENAI_API_KEY")
    logger.logInfo(s"Using API key (prefix): ${apiKey.take(8)}...")
    
    val request = HttpRequest.newBuilder()
      .uri(URI.create("https://api.openai.com/v1/chat/completions"))
      .timeout(Duration.ofSeconds(60))
      .header("Authorization", s"Bearer ${apiKey}")
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(payload))
      .build()

    val response = openAiHttpClient.send(request, HttpResponse.BodyHandlers.ofString())

    if response.statusCode() / 100 != 2 then
      throw new RuntimeException(s"OpenAI API error ${response.statusCode()}: ${response.body()}")

    val json = Json.parse(response.body())
    val generatedText = (json \ "choices").asOpt[play.api.libs.json.JsArray]
      .flatMap(_.value.headOption)
      .flatMap(choice => (choice \ "message" \ "content").asOpt[String])
      .getOrElse(throw new RuntimeException("OpenAI response did not contain choices[0].message.content"))

    mesRequest.continueWith(generatedText, "agent")
  }(using ExecutionContext.global)

}


/*


case class CompleteChatWithLLMCommand(apiKey: String, model: String) extends ExecutableCommand[MessengerChatCompletionRequest] {

  

  override def handleExecution(data: MessengerChatCompletionRequest): ExecutionResult = {

   *)



  }

}
*/
