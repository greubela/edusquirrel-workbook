package it.evadid.server

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.server.commands.BackendCommandHandler
import it.evadid.server.http.HttpApi
import it.evadid.util.logging.BasicLogger
import munit.FunSuite

import java.net.InetAddress
import java.time.Instant

class BackendServerTest extends FunSuite {
  test("an empty command name fails the returned Future instead of throwing") {
    val commands = BackendCommandHandler(TestServerConfig(), account.SignedTokens("secret"), database.schema.SchemaValidation())
    commands.handleExecution(Instant.parse("2026-01-01T08:00:00Z"), ExecutionCommand("   ", ujson.Obj()), None,
      InetAddress.getLoopbackAddress, BasicLogger()).failed.map { error =>
      assert(error.getMessage.contains("must not be empty"), error.getMessage)
    }(using scala.concurrent.ExecutionContext.global)
  }

  test("server failures use the response envelope understood by execution clients") {
    val time = Instant.parse("2026-01-01T08:00:00Z")
    val logger = BasicLogger()
    val command = ExecutionCommand("invalid", ujson.Obj())
    val response = HttpApi.fail(time, "Could not parse command", None, Some(command), logger)
    val restored = upickle.default.read[it.evadid.distribution.formats.ExecutionClientResponse](upickle.default.write(response))
    assertEquals(restored.receivedExecutionCommand, Some(command))
    assertEquals(restored.timestamps.timestampReceived, time)
    assertEquals(restored.result.left.toOption.map(_.msg), Some("Could not parse command"))
    assert(restored.parse[String](logger, command, time).isInstanceOf[it.evadid.distribution.command.ExecutionInfo.ExecutionFailure[?]])
  }

  test("request diagnostics remain bounded and omit values and keys for large or malformed payloads") {
    val feedback = it.evadid.distribution.commandTypes.LLMCommands.feedbackLlmCommandFactory.name
    val sync = it.evadid.distribution.commandTypes.SQLCommands.StoreToDbCommand.name
    val commands = List(
      ExecutionCommand(feedback, ujson.Obj("prompt" -> "private source", "systemPrompt" -> "private rules", "private key" -> "private value")),
      ExecutionCommand(feedback, ujson.Str("private non-object payload")),
      ExecutionCommand("another-command", ujson.Obj("private key" -> "private value")),
      ExecutionCommand(sync, ujson.Obj("historyJson" -> ("private history" * 100000), "databaseConfig" -> ujson.Obj())))
    commands.foreach { command =>
      val logger = BasicLogger()
      BackendCommandHandler.logReceivedCommand(command, logger)
      assert(logger.getOut().contains(command.name))
      assert(logger.getOut().length < 300)
      assert(!logger.getOut().contains("private"))
      assert(!logger.getErr().contains("private"))
    }
  }

  test("without a database the server starts with a warning instead of failing") {
    BackendServer.checkBackendDatabase(None, database.schema.SchemaValidation())
  }
}
