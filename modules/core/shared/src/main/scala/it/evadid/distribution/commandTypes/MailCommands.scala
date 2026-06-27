package it.evadid.distribution.commandTypes

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory

object MailCommands {

  case class SendMailRequest(
                              recipientMail: String,
                              subject: String,
                              content: String
                            )

  case class SendMailResponse(sent: Boolean)

  val sendMailCommand: ExecutionCommandFactory[SendMailRequest, SendMailResponse] = ExecutionCommandFactory(
    "send-mail-request",
    DefaultSerializer.serializerSendMailRequestJson,
    DefaultSerializer.serializerSendMailResponseJson
  )

}
