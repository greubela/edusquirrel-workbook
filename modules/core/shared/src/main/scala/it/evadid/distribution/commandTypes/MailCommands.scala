package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.file.LoadedFile
import it.evadid.core.datastructures.user.User
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory

import upickle.default.*

object MailCommands {

  case class SendMailRequest(
                              recipientMail: String,
                              subject: String,
                              content: String,
                              attachments: List[LoadedFile]
                            ) derives ReadWriter {
    attachments.zipWithIndex.foreach((file, index) => SendMailRequest.requireValidName(file, index + 1))
  }

  object SendMailRequest {
    /** Attachments are sent under their file name, so a nameless file (e.g. one located by a `data:` URL) or a name
     * with control characters cannot be requested; callers name such files explicitly. */
    private def requireValidName(file: LoadedFile, position: Int): Unit = {
      val name = file.description.filenameWithExtension
      if name.trim.isEmpty then throw IllegalArgumentException(s"Invalid mail request: attachment $position has no file name.")
      if name.exists(_.isControl) then
        throw IllegalArgumentException(s"Invalid mail request: the file name of attachment $position contains control characters.")
    }
  }

  case class SendMailResponse(associatedUser: Option[User], sent: Boolean) derives ReadWriter

  lazy val sendMailCommand: ExecutionCommandFactory[SendMailRequest, SendMailResponse] = ExecutionCommandFactory(
    "send-mail-request", requiresLogin = true
  )


}
