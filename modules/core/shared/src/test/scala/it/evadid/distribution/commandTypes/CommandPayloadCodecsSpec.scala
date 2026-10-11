package it.evadid.distribution.commandTypes

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.util.logging.BasicLogger
import munit.FunSuite
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}

class CommandPayloadCodecsSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

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
