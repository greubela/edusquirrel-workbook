package it.evadid.core.datastructures.user

import munit.FunSuite
import java.time.LocalDateTime
import upickle.default.*

class UserPackageSpec extends FunSuite {
  test("user names normalize repeated spaces and tabs before email derivation") {
    assertEquals(User.cleanUserName("  ADA   Lovelace\t "), "Ada Lovelace")
    assertEquals(User.deriveHuMail("  ADA   Lovelace  "), Some("ada.lovelace@student.hu-berlin.de"))
    assertEquals(User.deriveHuMail("Ada"), None)
    assertEquals(User.cleanUserName(""), "")
  }
  test("mail-derived names split dots, hyphens and underscores without treating digits as separators") {
    assertEquals(User.deriveNameFromMail("ada.lovelace@example.org"), Some("Ada Lovelace"))
    assertEquals(User.deriveNameFromMail("ada2_lovelace@example.org"), Some("Ada2 Lovelace"))
    assertEquals(User.deriveNameFromMail("ada-lovelace@example.org"), Some("Ada Lovelace"))
    assertEquals(User.deriveNameFromMail("ada@example.org"), None)
    assertEquals(User.deriveNameFromMail("invalid"), None)
  }
  test("initials and new users have stable identity fields") {
    val first = User.createNewUser("ADA", "LOVELACE", "ada@example.org")
    val second = User.createNewUser("ADA LOVELACE", "ada@example.org")
    assertEquals(first.name, "Ada Lovelace")
    assertEquals(first.initials, "AL")
    assertEquals(first.personId, first.id)
    assertEquals(first.abbreviation, Some("AL"))
    assert(first.id != second.id)
    assertEquals(User("", "empty", "").initials, "?")
    assertEquals(User("Ada", "single", "").initials, "A")
  }
  test("email validation handles blank, null, valid and malformed values") {
    List("ada@example.org", "ada+work@example.org", " ada@example.org ").foreach(mail => assert(User.isEmail(mail)))
    List(null, "", " ", "ada", "ada@", "@example.org", "ada@-example.org", "ada example.org").foreach(mail => assert(!User.isEmail(mail)))
  }
  test("user and database records have default codecs") {
    val user = User("Ada", "id", "ada@example.org")
    assertEquals(read[User](write(user)), user)
    val record = User.UserInDatabase(user, "{\"language\":\"English\"}")
    assertEquals(read[User.UserInDatabase](write(record)), record)
  }
  test("secure access tokens are distinct, hexadecimal, expire after 30 minutes and round-trip") {
    val before = LocalDateTime.now()
    val first = User.SingleAccessToken.generateSecureToken()
    val second = User.SingleAccessToken.generateSecureToken()
    val after = LocalDateTime.now()
    assert(first.token.matches("[0-9a-f]{40}"))
    assert(first.token != second.token)
    assert(!first.expires.isBefore(before.plusMinutes(30)))
    assert(!first.expires.isAfter(after.plusMinutes(30)))
    assertEquals(read[User.SingleAccessToken](write(first)), first)
    assertEquals(User.SingleAccessToken("existing").token, "existing")
  }
  test("token information and signed tokens preserve users and timestamps") {
    val user = User("Ada", "id", "ada@example.org")
    val created = LocalDateTime.of(2025, 1, 1, 12, 0)
    val info = UserTokenInfo(user, created, created.plusHours(1))
    assertEquals(read[UserTokenInfo](info.toJson), info)
    val signed = UserTokenInfo.SignedToken(info, "signed-value")
    assertEquals(read[UserTokenInfo.SignedToken](signed.toJson), signed)
    assertEquals(UserTokenInfo.SignedToken.cookieKey, "Authorization")
  }
  test("all-user information creates a new user while retaining its account configuration") {
    val config = UserConfig(Nil, isOnlineAccount = false)
    val info = AllUserInfo.createNewUser("ADA LOVELACE", "ada@example.org", config)
    assertEquals(info.user.name, "Ada Lovelace")
    assertEquals(info.token, None)
    assertEquals(info.config, config)
    assert(info.toString.contains("logged in: false"))
  }

}
