package it.evadid.server

import it.evadid.core.datastructures.chat.{Message, MessengerModel}
import it.evadid.distribution.commandTypes.LLMCommands.{FeedbackLlmRequest, MessengerChatCompletionRequest}
import it.evadid.llm.LlmConfiguration
import it.evadid.util.logging.Logger
import play.api.libs.json.{JsArray, Json}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse, HttpTimeoutException}
import java.io.IOException
import javax.net.ssl.SSLException
import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object CompleteChatWithLLMCommand {
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
    .followRedirects(HttpClient.Redirect.NEVER).build()

  private[server] def resolve(provided: Option[LlmConfiguration], env: String => Option[String]): LlmConfiguration = {
    // A supplied configuration is complete: never fill its fields from server credentials.
    val configuration = provided.getOrElse(LlmConfiguration(
      env("OPENAI_API_URL").getOrElse(LlmConfiguration.defaultEndpoint),
      env("OPENAI_MODEL").getOrElse(LlmConfiguration.defaultModel),
      env("OPENAI_API_KEY"),
      env("OPENAI_MAX_TOKENS").map(value => Try(value.toInt)
        .getOrElse(throw IllegalArgumentException("Invalid backend LLM token limit")))))
    val target = Try(URI.create(configuration.endpoint)).toOption.filter(uri =>
      Set("https", "http")(Option(uri.getScheme).getOrElse("")) && uri.getHost != null && uri.getUserInfo == null && uri.getFragment == null)
      .getOrElse(throw IllegalArgumentException("LLM endpoint must be an absolute HTTP(S) URL without embedded credentials"))
    require(configuration.apiKey.forall(key => key.nonEmpty && !key.exists(_.isControl)), "Invalid LLM API key")
    require(configuration.model.trim.nonEmpty, "LLM model must not be empty")
    require(configuration.maxTokens.forall(_ > 0), "LLM token limit must be positive")
    if target.toString == LlmConfiguration.defaultEndpoint then require(configuration.apiKey.nonEmpty, "LLM API key is not configured")
    configuration
  }

  private def complete(system: String, messages: JsArray, provided: Option[LlmConfiguration]): Future[String] = Future {
    val configuration = resolve(provided, name => Option(System.getenv(name)))
    val body = Json.obj("model" -> configuration.model, "messages" ->
      (Json.arr(Json.obj("role" -> "system", "content" -> system)) ++ messages)) ++
      configuration.maxTokens.map(value => Json.obj("max_tokens" -> value)).getOrElse(Json.obj())
    val builder = HttpRequest.newBuilder(URI.create(configuration.endpoint)).timeout(Duration.ofSeconds(60))
      .header("Content-Type", "application/json")
    configuration.apiKey.foreach(key => builder.header("Authorization", "Bearer " + key))
    val response = withRequestContext { client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body.toString)).build(), HttpResponse.BodyHandlers.ofString()) }
    decodeResponse(response.statusCode(), response.body())
  }(using ExecutionContext.global)

  /** Avoid retaining provider/network exceptions: they can echo keys, prompts or URL query parameters. */
  private[server] def withRequestContext[A](send: => A): A =
    try send
    catch {
      case _: HttpTimeoutException => throw IllegalStateException("LLM request timed out; check provider availability or try a shorter request.")
      case _: SSLException => throw IllegalStateException("LLM TLS connection failed; check the provider certificate and server trust configuration.")
      case _: IOException => throw IllegalStateException("Could not reach the LLM provider; check the endpoint, DNS and server network access.")
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        throw IllegalStateException("LLM request was interrupted before completion; retry after the server is available.")
    }

  private[server] def decodeResponse(status: Int, body: String): String = {
    // Provider responses can echo authorization headers or payloads; never retain their body in errors.
    if status / 100 != 2 then {
      val reason = status match {
        case 401 | 403 => "The provider rejected access; check the API key and model permissions."
        case 404 => "The provider could not find the endpoint or model; check the configured API URL and model."
        case 429 => "The provider rate limit or quota was exceeded; retry later or check available credits."
        case code if code >= 500 => "The provider is unavailable or failed internally; retry later."
        case _ => "The provider rejected the request; check model and request configuration."
      }
      throw IllegalStateException(s"LLM request failed (HTTP $status): $reason")
    }
    val json = Try(Json.parse(body)).getOrElse(throw IllegalStateException("Could not decode LLM response: provider returned invalid JSON; check the API endpoint and proxy."))
    (json \ "choices").asOpt[JsArray].flatMap(_.value.headOption)
      .flatMap(choice => (choice \ "message" \ "content").asOpt[String])
      .getOrElse(throw IllegalStateException("Could not decode LLM response: missing choices[0].message.content; an OpenAI-compatible chat completion response is required."))
  }

  def handleLlmChatRequest(request: MessengerChatCompletionRequest, logger: Logger): Future[Message] = {
    val messages = JsArray(request.messengerModel.orderedMessages.map(message =>
      Json.obj("role" -> message.author.role.llmName, "content" -> message.text)))
    complete(request.systemPrompt, messages, request.llmConfiguration)
      .map(text => Message(text, MessengerModel.pAgent))(using ExecutionContext.global)
  }

  def handleFeedbackLlmRequest(request: FeedbackLlmRequest, logger: Logger): Future[String] =
    complete(request.systemPrompt, Json.arr(Json.obj("role" -> "user", "content" -> request.prompt)), request.llmConfiguration)
}
