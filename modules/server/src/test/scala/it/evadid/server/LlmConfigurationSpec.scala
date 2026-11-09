package it.evadid.server

import it.evadid.llm.LlmConfiguration
import it.evadid.distribution.commandTypes.LLMCommands.*
import it.evadid.util.logging.BasicLogger
import munit.FunSuite
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import scala.concurrent.Await
import scala.concurrent.duration.*

class LlmConfigurationSpec extends FunSuite {
  private val environment = Map("OPENAI_API_URL" -> "https://backend.example/v1/chat/completions", "OPENAI_API_KEY" -> "backend-secret", "OPENAI_MODEL" -> "backend-model")
  test("absent configuration uses server defaults; supplied configuration is used without merging") {
    val defaults = CompleteChatWithLLMCommand.resolve(None, environment.get)
    assertEquals(defaults, LlmConfiguration("https://backend.example/v1/chat/completions", "backend-model", Some("backend-secret")))
    val custom = LlmConfiguration(defaults.endpoint, "own-model", None)
    assertEquals(CompleteChatWithLLMCommand.resolve(Some(custom), _ => throw AssertionError("Server defaults must not be read")), custom)
    assert(!defaults.toString.contains("backend-secret"))
    intercept[IllegalArgumentException](CompleteChatWithLLMCommand.resolve(Some(custom.copy(endpoint = "https://user:password@example.com")), environment.get))
    intercept[IllegalArgumentException](CompleteChatWithLLMCommand.resolve(Some(custom.copy(maxTokens = Some(0))), environment.get))
  }
  test("feedback and chat HTTP requests use supplied configuration without logging credentials") {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val captured = new java.util.concurrent.LinkedBlockingQueue[(String, String)]()
    server.createContext("/chat", exchange => {
      captured.add((exchange.getRequestHeaders.getFirst("Authorization"), new String(exchange.getRequestBody.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)))
      val body = """{"choices":[{"message":{"content":"fixture response"}}]}""".getBytes(java.nio.charset.StandardCharsets.UTF_8)
      exchange.sendResponseHeaders(200, body.length)
      exchange.getResponseBody.write(body)
      exchange.close()
    })
    server.start()
    try {
      val config = LlmConfiguration(s"http://127.0.0.1:${server.getAddress.getPort}/chat", "own-model", Some("session-secret"), Some(123))
      val request = FeedbackLlmRequest("question", "instructions", Some(config))
      assertEquals(Await.result(CompleteChatWithLLMCommand.handleFeedbackLlmRequest(request, BasicLogger()), 10.seconds), "fixture response")
      val (authorization, body) = captured.poll(1, java.util.concurrent.TimeUnit.SECONDS)
      assertEquals(authorization, "Bearer session-secret")
      val json = ujson.read(body)
      assertEquals(json("model").str, "own-model")
      assertEquals(json("max_tokens").num, 123d)
      val logged = it.evadid.distribution.command.ExecutionCommand(feedbackLlmCommandFactory.name, upickle.default.writeJs(request)).stripPrivacySensitiveInformation().json.toString
      assert(!logged.contains("session-secret"))
      assert(!logged.contains("question"))
      val chat = MessengerChatCompletionRequest("instructions", it.evadid.core.datastructures.chat.MessengerModel(Nil), Some(config))
      assertEquals(Await.result(CompleteChatWithLLMCommand.handleLlmChatRequest(chat, BasicLogger()), 10.seconds).text, "fixture response")
      assert(!it.evadid.distribution.command.ExecutionCommand(completeLLMCommandFactory.name, upickle.default.writeJs(chat)).stripPrivacySensitiveInformation().json.toString.contains("session-secret"))
    } finally server.stop(0)
  }
}
