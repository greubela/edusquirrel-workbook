package it.evadid.distribution.commandTypes

import it.evadid.distribution.clients.LocalExecutionClient
import it.evadid.distribution.command.{ExecutionCommand, ExecutionCommandFactory, SerializedException}
import it.evadid.util.logging.{BasicLogger, Logger}
import munit.FunSuite
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}
import java.io.{ByteArrayOutputStream, PrintStream}

class CommandPayloadCodecsSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  test("feedback logging copies retain only bounded metadata without changing execution payloads") {
    val name = LLMCommands.feedbackLlmCommandFactory.name
    val valid = ujson.Obj("prompt" -> "private source Grüße 🐢", "systemPrompt" -> "private instructions",
      "private unknown key" -> ujson.Obj("nested" -> "private extra value"))
    val malformed = List(ujson.Str("private raw body"), ujson.Arr("private array"), ujson.Null,
      ujson.Obj("redacted" -> true, "promptLength" -> "private count", "systemPromptLength" -> -1),
      ujson.Obj("prompt" -> ujson.Obj("private key" -> "private value"), "systemPrompt" -> false))
    (valid :: malformed).foreach { payload =>
      val command = ExecutionCommand(name, payload)
      val original = write(command)
      val safe = command.stripPrivacySensitiveInformation()
      assertEquals(safe.name, name)
      assertEquals(safe.json.obj.keySet.toSet, Set("redacted", "promptLength", "systemPromptLength"))
      assertEquals(safe.json("redacted"), ujson.Bool(true))
      assert(!safe.toJson.contains("private"))
      assert(!(safe.json eq command.json))
      assertEquals(safe.stripPrivacySensitiveInformation(), safe)
      assertEquals(write(command), original)
      assertEquals(command.toJson, original)
      assertEquals(ujson.write(command.json), ujson.write(payload))
      assertEquals(read[ExecutionCommand](write(command)), command)
    }
    val safe = ExecutionCommand(name, valid).stripPrivacySensitiveInformation()
    assertEquals(safe.json("promptLength").num.toInt, valid("prompt").str.length)
    assertEquals(safe.json("systemPromptLength").num.toInt, valid("systemPrompt").str.length)
    val ordinary = ExecutionCommand("diagnostic-command", ujson.Obj("count" -> 12, "option" -> "full details"))
    assert(ordinary.stripPrivacySensitiveInformation() eq ordinary)
    assertEquals(ordinary.stripPrivacySensitiveInformation().toJson, ordinary.toJson)
  }

  test("feedback factory logs omit prompts and client credentials while execution keeps the exact request") {
    val request = LLMCommands.FeedbackLlmRequest("private learner source\r\n学校", "private system rules")
    val logger = BasicLogger()
    val client = new LocalExecutionClient[LLMCommands.FeedbackLlmRequest, String] {
      override def toString: String = "private client token"
      override def canExecuteCommand(command: ExecutionCommand): Boolean = command.name == LLMCommands.feedbackLlmCommandFactory.name
      override protected def rawExecution(log: Logger, input: LLMCommands.FeedbackLlmRequest): Future[String] = {
        assertEquals(input, request)
        Future.successful("accepted")
      }
    }
    LLMCommands.feedbackLlmCommandFactory.sendCommandTo(client, request, Some(logger)).map { response =>
      assertEquals(response.result, "accepted")
      assertEquals(response.command.json, writeJs(request))
      assert(logger.getOut().contains("promptLength"))
      assert(!logger.getOut().contains("private"))
      assert(!logger.getErr().contains("private"))
    }
  }

  test("Python command logs omit source, tests, context and callback data without changing requests") {
    val factory = WorkerCommands.runPython
    val request = WorkerCommands.RunPythonRequest("private source", Map("private tests" -> ujson.Str("private assertions")),
      Map("private module" -> List("private callback")), List("private package"))
    val logger = BasicLogger()
    val response = WorkerCommands.RunPythonResult(ujson.Null, "", "", Nil)
    val client = factory.toLocalExecutionClient((input, _) => {
      assertEquals(input, request)
      Future.successful(response)
    })
    val original = ExecutionCommand(factory.name, writeJs(request))
    val safe = original.stripPrivacySensitiveInformation()
    assertEquals(safe.json.obj.keySet, Set("redacted", "codeLength"))
    assertEquals(safe.json("codeLength"), ujson.Num(request.code.length))
    assertEquals(safe.stripPrivacySensitiveInformation(), safe)
    assertEquals(original.json, writeJs(request))
    List(ujson.Null, ujson.Str("private body"), ujson.Obj("redacted" -> true, "codeLength" -> -1)).foreach { value =>
      val malformed = ExecutionCommand(factory.name, value).stripPrivacySensitiveInformation()
      assertEquals(malformed.json("codeLength"), ujson.Null)
      assert(!malformed.toJson.contains("private"))
    }
    factory.sendCommandTo(client, request, Some(logger)).map { reply =>
      assertEquals(reply.result, response)
      assertEquals(reply.command.json, writeJs(request))
      assert(logger.getOut().contains("codeLength"))
      assert(!logger.getOut().contains("private"))
      assert(!logger.getErr().contains("private"))
    }
  }

  test("feedback parse and permission errors do not echo their input") {
    val factory = LLMCommands.feedbackLlmCommandFactory
    val client = factory.toLocalExecutionClient((_, _) => Future.successful("unused"))
    val malformed = ExecutionCommand(factory.name, ujson.Obj("prompt" -> "private source"))
    val logger = BasicLogger()
    Future.unit.flatMap(_ => client.executeResponse(logger, malformed)).failed.flatMap { failure =>
      failure match {
        case error: SerializedException =>
          assert(error.msg.contains("promptLength"))
          assert(!error.toJson.contains("private"))
          assertEquals(error.cause, None)
        case other => fail(s"Unexpected parse failure: ${other.getClass.getName}")
      }
      val denied = factory.toLocalExecutionClient((_, _) => Future.successful("unused"), (_, _) => false)
      factory.sendCommandTo(denied, LLMCommands.FeedbackLlmRequest("private source", "private instructions"), Some(logger))
        .failed.map { deniedFailure =>
          deniedFailure match {
            case error: SerializedException =>
              assert(error.msg.contains("promptLength"))
              assert(!error.toJson.contains("private"))
              assertEquals(error.cause, None)
            case other => fail(s"Unexpected permission failure: ${other.getClass.getName}")
          }
          assert(!logger.getOut().contains("private"))
          assert(!logger.getErr().contains("private"))
        }
    }
  }

  test("feedback default-client logging does not print the raw command or credential-bearing client label") {
    val command = ExecutionCommand(LLMCommands.feedbackLlmCommandFactory.name,
      writeJs(LLMCommands.FeedbackLlmRequest("private source", "private rules")))
    val client = new LocalExecutionClient[LLMCommands.FeedbackLlmRequest, String] {
      override def toString: String = "private client token"
      override def canExecuteCommand(command: ExecutionCommand): Boolean = true
      override protected def rawExecution(logger: Logger, input: LLMCommands.FeedbackLlmRequest): Future[String] = {
        logger.logWarn("diagnostic marker")
        Future.successful(input.prompt)
      }
    }
    val output = new ByteArrayOutputStream()
    val result = Console.withOut(new PrintStream(output))(client.handleCommand[String](command))
    result.map { response =>
      assertEquals(response.successOrThrow().result, "private source")
      assert(output.toString.contains("diagnostic marker"))
      assert(output.toString.contains("promptLength"))
      assert(!output.toString.contains("private"))
    }
  }

  test("ordinary factory logs retain diagnostic parameter values") {
    val factory = ExecutionCommandFactory[String, String]("diagnostic-command")
    val logger = BasicLogger()
    val client = factory.toLocalExecutionClient((input, _) => Future.successful(input))
    factory.sendCommandTo(client, "full diagnostic value", Some(logger)).map { response =>
      assertEquals(response.result, "full diagnostic value")
      assert(logger.getOut().contains("full diagnostic value"))
    }
  }

  test("a mail response codec initializes independently of its command factory") {
    val response = MailCommands.SendMailResponse(None, sent = true)
    assertEquals(read[MailCommands.SendMailResponse](write(response)), response)
    assertEquals(UserCommands.authMailCommand.name, "auth-mail-request")
    assertEquals(MailCommands.sendMailCommand.name, "send-mail-request")
  }

  test("a typed mail command preserves the response through the serialized transport") {
    val response = MailCommands.SendMailResponse(None, sent = true)
    val client = MailCommands.sendMailCommand.toLocalExecutionClient((_, _) => Future.successful(response))
    val request = MailCommands.SendMailRequest("student@example.org", "Subject", "Grüße")
    val command = ExecutionCommand(MailCommands.sendMailCommand.name, writeJs(request))
    client.executeResponse(BasicLogger(), command).map { transported =>
      val restored = read[it.evadid.distribution.formats.ExecutionClientResponse](write(transported))
      assertEquals(restored.result.toOption.get.getAs[MailCommands.SendMailResponse], response)
    }
  }
}
