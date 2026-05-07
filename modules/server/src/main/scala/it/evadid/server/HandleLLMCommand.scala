package it.evadid.server

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.distribution.{ExecutionCommand, ExecutionInfo, ExecutionResult}
import play.api.libs.json.Json

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import scala.util.Success

class HandleLLMCommand {

  private val openAiHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()

  private def env(name: String): Option[String] =
    Option(System.getenv(name)).map(_.trim).filter(_.nonEmpty)

  def handle(executionCommand: ExecutionCommand): ExecutionInfo = {
    val apiKey = env("OPENAI_API_KEY").getOrElse(throw new IllegalStateException("OPENAI_API_KEY is not configured"))
    val model = env("OPENAI_MODEL").getOrElse("gpt-4o-mini")
    val systemPrompt = executionCommand.params.getOrElse("systemPrompt", "")
    val messengerRaw = executionCommand.params.getOrElse("messengerModel", throw new IllegalArgumentException("Missing 'messengerModel' parameter"))
    val messenger = upickle.default.read[MessengerModel](messengerRaw)

    val messagesJson = Json.arr(
      Json.obj("role" -> "system", "content" -> systemPrompt)
    ) ++ Json.arr(messenger.orderedMessages.map { msg =>
      Json.obj(
        "role" -> (if msg.senderRole == MessengerModel.SenderRole.USER then "user" else "assistant"),
        "content" -> msg.text
      )
    }*)

    val payload = Json.obj("model" -> model, "messages" -> messagesJson).toString()

    val request = HttpRequest.newBuilder()
      .uri(URI.create("https://api.openai.com/v1/chat/completions"))
      .timeout(Duration.ofSeconds(60))
      .header("Authorization", s"Bearer $apiKey")
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

    val result = ExecutionResult(Map("generatedText" -> generatedText), "", "")
    ExecutionInfo(executionCommand, Success(result), None)
  }
}
