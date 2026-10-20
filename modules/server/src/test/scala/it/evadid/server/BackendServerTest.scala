package it.evadid.server

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.util.logging.BasicLogger
import munit.FunSuite

import java.net.InetAddress
import java.time.LocalDateTime

class BackendServerTest extends FunSuite {
  test("BackendCommandHandler rejects empty command name") {
    val command = ExecutionCommand("   ", ujson.Obj())

    intercept[IllegalArgumentException] {
      BackendCommandHandler.handleExecution(
        LocalDateTime.parse("2026-01-01T08:00:00"),
        command,
        None,
        InetAddress.getLoopbackAddress,
        BasicLogger()
      )
    }
  }
  test("server failures use the response envelope understood by execution clients") {
    val time = LocalDateTime.parse("2026-01-01T08:00:00")
    val logger = BasicLogger()
    val command = ExecutionCommand("invalid", ujson.Obj())
    val response = BackendServer.fail(time, "Could not parse command", None, Some(command), logger)
    val restored = upickle.default.read[it.evadid.distribution.formats.ExecutionClientResponse](upickle.default.write(response))
    assertEquals(restored.receivedExecutionCommand, Some(command))
    assertEquals(restored.timestamps.timestampReceived, time)
    assertEquals(restored.result.left.toOption.map(_.msg), Some("Could not parse command"))
    assert(restored.parse[String](logger, command, time).isInstanceOf[it.evadid.distribution.command.ExecutionInfo.ExecutionFailure[?]])
  }

  test("received feedback logs omit request values and unknown keys even for malformed payloads") {
    val name = it.evadid.distribution.commandTypes.LLMCommands.feedbackLlmCommandFactory.name
    List(ujson.Obj("prompt" -> "private source", "systemPrompt" -> "private rules", "private key" -> "private value"),
      ujson.Str("private non-object payload")).foreach { payload =>
      val logger = BasicLogger()
      BackendCommandHandler.logReceivedCommand(ExecutionCommand(name, payload), logger)
      assert(logger.getOut().contains(name))
      assert(logger.getOut().contains("\"redacted\":true"))
      assert(!logger.getOut().contains("private"))
      assert(!logger.getErr().contains("private"))
    }
    val command = ExecutionCommand("another-command", ujson.Obj("original-key" -> "original-value"))
    val logger = BasicLogger()
    BackendCommandHandler.logReceivedCommand(command, logger)
    assertEquals(logger.getOut(), s"[server] Received command: ${command.name} with params keys: ${command.json.obj.keys}\n")
  }

  test("malformed feedback requests fail without retaining a payload-bearing cause") {
    val name = it.evadid.distribution.commandTypes.LLMCommands.feedbackLlmCommandFactory.name
    val payloads = List(ujson.Obj(), ujson.Obj("prompt" -> "private source"),
      ujson.Obj("prompt" -> "private source", "systemPrompt" -> false),
      ujson.Obj("prompt" -> ujson.Null, "systemPrompt" -> "private instructions"),
      ujson.Obj("prompt" -> "private source", "systemPrompt" -> ujson.Null),
      ujson.Obj("prompt" -> 42, "systemPrompt" -> "private instructions"),
      ujson.Obj("prompt" -> false, "systemPrompt" -> "private instructions"),
      ujson.Obj("prompt" -> "private source", "systemPrompt" -> ujson.Obj("private" -> "value")),
      ujson.Obj("prompt" -> ujson.Arr("private source"), "systemPrompt" -> "private instructions"),
      ujson.Str("private non-object source"), ujson.Num(42), ujson.Arr("private array"), ujson.Null)
    payloads.foreach { payload =>
      val command = ExecutionCommand(name, payload)
      val original = ujson.write(payload)
      val result = scala.util.Try(BackendCommandHandler.validateFeedbackRequest(command))
      assert(result.failed.toOption.exists(_.isInstanceOf[IllegalArgumentException]),
        clue = s"Malformed payload was not rejected: $original")
      val error = result.failed.get
      assertEquals(error.getMessage, "Invalid feedback request.")
      assertEquals(error.getCause, null)
      assert(!error.toString.contains("private"))
      assertEquals(ujson.write(command.json), original)
      BackendCommandHandler.validateFeedbackRequest(command.copy(name = "another-command"))
    }
  }

  test("valid feedback fields including unknown data are validated without modifying execution input") {
    val name = it.evadid.distribution.commandTypes.LLMCommands.feedbackLlmCommandFactory.name
    List(ujson.Obj("prompt" -> "", "systemPrompt" -> ""),
      ujson.Obj("prompt" -> "private source 学校", "systemPrompt" -> "private instructions",
        "private extra key" -> "private extra value")).foreach { payload =>
      val command = ExecutionCommand(name, payload)
      val original = ujson.write(payload)
      BackendCommandHandler.validateFeedbackRequest(command)
      assertEquals(ujson.write(command.json), original)
    }
  }

}
