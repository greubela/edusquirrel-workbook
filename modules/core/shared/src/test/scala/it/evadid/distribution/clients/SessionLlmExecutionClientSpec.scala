package it.evadid.distribution.clients

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.llm.{LlmConfiguration, LlmPartialConfiguration}
import it.evadid.util.logging.{BasicLogger, Logger}
import munit.FunSuite
import upickle.default.*
import scala.concurrent.{Future, ExecutionContext}

class SessionLlmExecutionClientSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  test("LLM transport applies current session settings without mutating requests") {
    val configuration = LlmConfiguration("https://own.example/chat", "own-model", Some("session-key"), Some(50))
    val names = List(LLMCommands.completeLLMCommandFactory.name, LLMCommands.feedbackLlmCommandFactory.name)
    Future.sequence(names.map { name =>
      val original = ExecutionCommand(name, ujson.Obj("llmConfiguration" -> writeJs(LlmPartialConfiguration())))
      val received = scala.concurrent.Promise[ExecutionCommand]()
      val underlying = new ExecutionClient {
        override def canExecuteCommand(command: ExecutionCommand): Boolean = true
        override private[clients] def handleCommandExecution(logger: Logger, command: ExecutionCommand): Future[ExecutionClientResponse] = {
          received.success(command)
          Future.failed(new IllegalStateException("transport fixture"))
        }
      }
      val client = SessionLlmExecutionClient(underlying, () => Some(configuration))
      client.executeResponse(BasicLogger(), original).failed.flatMap { _ => received.future }.map { sent =>
        assertEquals(read[LlmPartialConfiguration](sent.json("llmConfiguration")), configuration.partial)
        assert(!original.json.toString.contains("session-key"))
        assert(!sent.stripPrivacySensitiveInformation().json.toString.contains("session-key"))
      }
    })
  }
  test("an explicitly selected endpoint never receives a different session endpoint's key") {
    val received = scala.concurrent.Promise[ExecutionCommand]()
    val underlying = new ExecutionClient {
      override def canExecuteCommand(command: ExecutionCommand): Boolean = true
      override private[clients] def handleCommandExecution(logger: Logger, command: ExecutionCommand): Future[ExecutionClientResponse] = {
        received.success(command)
        Future.failed(new IllegalStateException("transport fixture"))
      }
    }
    val client = SessionLlmExecutionClient(underlying, () => Some(LlmConfiguration("https://own.example/chat", "own-model", Some("session-key"))))
    val original = ExecutionCommand(LLMCommands.feedbackLlmCommandFactory.name,
      ujson.Obj("llmConfiguration" -> writeJs(LlmPartialConfiguration(endpoint = Some("https://other.example/chat")))))
    client.executeResponse(BasicLogger(), original).failed.flatMap(_ => received.future).map { sent =>
      assertEquals(read[LlmPartialConfiguration](sent.json("llmConfiguration")).apiKey, None)
    }
  }

}
