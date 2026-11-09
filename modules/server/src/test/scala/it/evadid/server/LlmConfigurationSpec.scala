package it.evadid.server

import it.evadid.llm.LlmPartialConfiguration
import it.evadid.distribution.commandTypes.LLMCommands.*
import it.evadid.util.logging.BasicLogger
import munit.FunSuite
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import scala.concurrent.Await
import scala.concurrent.duration.*

class LlmConfigurationSpec extends FunSuite {
  private val environment = Map("OPENAI_API_URL" -> "https://backend.example/v1/chat/completions", "OPENAI_API_KEY" -> "backend-secret", "OPENAI_MODEL" -> "backend-model")
  test("defaults resolve while custom endpoints never inherit the backend key") {
    val defaults = CompleteChatWithLLMCommand.resolve(LlmPartialConfiguration(), environment.get)
    assertEquals(defaults.apiKey, Some("backend-secret"))
    val custom = CompleteChatWithLLMCommand.resolve(LlmPartialConfiguration(endpoint = Some("http://localhost:1234/completions")), environment.get)
    assertEquals(custom.apiKey, None)
    assert(!defaults.toString.contains("backend-secret"))
    intercept[IllegalArgumentException](CompleteChatWithLLMCommand.resolve(LlmPartialConfiguration(endpoint = Some("https://user:password@example.com")), environment.get))
    intercept[IllegalArgumentException](CompleteChatWithLLMCommand.resolve(LlmPartialConfiguration(maxTokens = Some(0)), environment.get))
  }
  test("feedback HTTP request uses supplied model key and token limit without logging credentials") {
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
      val config = LlmPartialConfiguration(Some(s"http://127.0.0.1:${server.getAddress.getPort}/chat"), Some("own-model"), Some("session-secret"), Some(123))
      val request = FeedbackLlmRequest("question", "instructions", config)
      assertEquals(Await.result(CompleteChatWithLLMCommand.handleFeedbackLlmRequest(request, BasicLogger()), 10.seconds), "fixture response")
      val (authorization, body) = captured.poll(1, java.util.concurrent.TimeUnit.SECONDS)
      assertEquals(authorization, "Bearer session-secret")
      val json = ujson.read(body)
      assertEquals(json("model").str, "own-model")
      assertEquals(json("max_tokens").num, 123d)
      val logged = feedbackLlmCommandFactory.toCommand(request).stripPrivacySensitiveInformation().json.toString
      assert(!logged.contains("session-secret"))
      assert(!logged.contains("question"))
      val chat = MessengerChatCompletionRequest("instructions", it.evadid.core.datastructures.chat.MessengerModel(Nil), config)
      assert(!completeLLMCommandFactory.toCommand(chat).stripPrivacySensitiveInformation().json.toString.contains("session-secret"))
    } finally server.stop(0)
  }
}
