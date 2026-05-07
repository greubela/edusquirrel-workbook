package it.evadid.server

import it.evadid.core.datastructures.chat.{MessengerChatCompletionRequest, MessengerModel}
import it.evadid.core.util.io.TypeConverter
import it.evadid.distribution.{ExecutionInfo, ExecutionResult}
import it.evadid.distribution.executor.ExecutableCommand
import play.api.libs.json.Json

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import scala.util.Success

case class CompleteChatWithLLMCommand(apiKey: String, model: String) extends ExecutableCommand[MessengerChatCompletionRequest] {

  private val openAiHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
  
  override def name: String = "execute-llm-request"

  override def dataConverter: TypeConverter[Map[String, String], MessengerChatCompletionRequest] = new TypeConverter[Map[String, String], MessengerChatCompletionRequest] {

    override def convertToO(in: Map[String, String]): MessengerChatCompletionRequest =
      upickle.default.read[MessengerChatCompletionRequest](in("serialized-data"))(using MessengerModel.given_ReadWriter_MessengerChatCompletionRequest)
    
    override def convertToI(in: MessengerChatCompletionRequest): Map[String, String] = Map(
      "serialized-data" -> upickle.default.write(in)(using MessengerModel.given_ReadWriter_MessengerChatCompletionRequest)
    )
  }

  override def handleExecution(data: MessengerChatCompletionRequest): ExecutionResult = {

    val messagesJson = Json.arr(
      Json.obj("role" -> "system", "content" -> data.systemPrompt)
    ) ++ Json.arr(data.messengerModel.orderedMessages.map { msg =>
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

    ExecutionResult(Map("generatedText" -> generatedText), "", "")
  }

}
