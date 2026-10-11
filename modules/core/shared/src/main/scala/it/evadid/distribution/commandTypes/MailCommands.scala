package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.user.User
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory

import upickle.default.*

object MailCommands {

  case class SendMailRequest(
                              recipientMail: String,
                              subject: String,
                              content: String
                            ) derives ReadWriter

  case class SendMailResponse(associatedUser: Option[User], sent: Boolean) derives ReadWriter

  lazy val sendMailCommand: ExecutionCommandFactory[SendMailRequest, SendMailResponse] = ExecutionCommandFactory(
    "send-mail-request"
  )


}
