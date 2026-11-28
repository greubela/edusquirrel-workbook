package it.evadid.server.account

import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.distribution.commandTypes.MailCommands.SendMailRequest
import java.time.format.DateTimeFormatter

/** The bilingual mail carrying a login code; the code appears only in the body, never in the subject. */
object LoginMail {
  private val expiryFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

  def apply(recipient: String, token: SingleAccessToken): SendMailRequest = {
    val expires = token.expires.format(expiryFormat)
    SendMailRequest(recipient, "EduSquirrel: Ihr Login-Code / your login code",
      s"""Guten Tag,
         |
         |Ihr Login-Code lautet:
         |
         |${token.token}
         |
         |Der Code ist gültig bis $expires. Bitte geben Sie ihn nicht weiter!
         |
         |Your login code is shown above. It is valid until $expires; please do not share it.
         |""".stripMargin, Nil)
  }
}
