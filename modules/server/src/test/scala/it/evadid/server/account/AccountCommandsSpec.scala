package it.evadid.server.account

import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.util.logging.BasicLogger
import munit.FunSuite
import java.time.LocalDateTime

class AccountCommandsSpec extends FunSuite {
  private val stored = SingleAccessToken("0123abcd", LocalDateTime.now().plusHours(1))

  test("a login code is accepted only when it equals the stored, unexpired code; its submitted expiry is ignored") {
    val logger = BasicLogger()
    assert(AccountCommands.acceptsLoginCode(Some(stored), stored.copy(expires = LocalDateTime.now().minusYears(1)), logger))
    assert(!AccountCommands.acceptsLoginCode(None, stored, logger))
    assert(!AccountCommands.acceptsLoginCode(Some(stored), stored.copy(token = "0123abce"), logger))
    assert(!AccountCommands.acceptsLoginCode(Some(stored), stored.copy(token = ""), logger))
    assert(!AccountCommands.acceptsLoginCode(Some(stored.copy(expires = LocalDateTime.now().minusSeconds(1))), stored, logger))
    assert(!logger.getOut().contains(stored.token))
  }

  test("the login mail carries the code in its body only") {
    val mail = LoginMail("alice@example.invalid", stored)
    assertEquals(mail.recipientMail, "alice@example.invalid")
    assert(!mail.subject.contains(stored.token))
    assert(mail.content.contains(stored.token))
    assert(mail.attachments.isEmpty)
  }
}
