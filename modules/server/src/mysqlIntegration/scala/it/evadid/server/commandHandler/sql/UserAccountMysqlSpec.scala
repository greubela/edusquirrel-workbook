package it.evadid.server.commandHandler.sql

import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.datastructures.user.User.{SingleAccessToken, UserInDatabase}
import it.evadid.distribution.commandTypes.UserCommands.*
import it.evadid.server.AuthHandling
import it.evadid.sql.DatabaseConfig
import it.evadid.util.logging.BasicLogger
import munit.FunSuite

import java.sql.Connection
import java.net.InetAddress
import it.evadid.distribution.command.{ExecutionCommand, ExecutionCommandFactory}
import it.evadid.distribution.commandTypes.{SQLCommands, LLMCommands}
import it.evadid.sql.DatabaseTable
import java.time.LocalDateTime
import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.io.Source
import scala.util.Using

/** Explicitly invoked against a disposable local MySQL database; never sends mail. */
class UserAccountMysqlSpec extends FunSuite {
  private val logger = BasicLogger()
  private val config = UserConfig.onlineDefaults
  private val alice = User("Änne O'Connor 🐿", "alice", "Alice@Example.Invalid")
  private val bob = User("Bob", "bob", "bob@example.invalid")
  private def normalized(user: User): User = user.copy(mail = user.mail.toLowerCase)

  private def withDatabase[A](run: Connection => A): A =
    Using.resource(DatabaseConfig.readFromEnv().newConnection())(run)

  private def queries[A](run: SqlUserCommands => A): A =
    withDatabase(connection => run(SqlUserCommands(connection, logger)))

  private def register(user: User = alice): CreateAccountResponse =
    Await.result(SqlUserCommands.handleCreateAccountCommand(
      CreateAccountRequest(AllUserInfo(user, None, config)), logger), 10.seconds)

  private def login(mail: String, token: SingleAccessToken): LoginResponse =
    Await.result(SqlUserCommands.handleLoginCommand(LoginRequest(mail, Left(token)), logger), 10.seconds)

  override def beforeAll(): Unit = {
    // The handlers use SQL_* too. Refuse to create/clear tables outside this fixture.
    require(sys.env.get("SQL_DATABASE").contains("edusquirrel_account_test"),
      "MySQL integration tests require SQL_DATABASE=edusquirrel_account_test")
    require(sys.env.get("SQL_HOST").exists(Set("127.0.0.1", "localhost")),
      "MySQL integration tests require a disposable database on localhost")
    require(sys.env.get("SERVER_SECRET").exists(_.nonEmpty),
      "Set a disposable SERVER_SECRET for signed-token integration tests")
    withDatabase(it.evadid.server.database.MysqlTestDatabase.reset)
  }

  override def beforeEach(context: BeforeEach): Unit = withDatabase { connection =>
    Using.resource(connection.createStatement()) { statement =>
      statement.executeUpdate("DELETE FROM `syncedLog`")
      statement.executeUpdate("DELETE FROM `commandLog`")
      statement.executeUpdate("DELETE FROM `loginTokens`")
      statement.executeUpdate("DELETE FROM `user`")
    }
    SqlUserCommands(connection, logger).upsertUser(User.Anonymous, UserConfig(Nil, isOnlineAccount = false))
  }

  test("registration persists Unicode identity, normalized email and current account settings") {
    val response = register()
    assert(response.accountCreated)
    assert(response.token.exists(AuthHandling.isTokenValid(logger, _)))
    queries { control =>
      val expected = UserInDatabase(normalized(alice), config)
      assertEquals(control.readUserInfoFromDb(alice.id), Some(expected))
      assertEquals(control.findUserWithMail("ALICE@EXAMPLE.INVALID"), List(expected))
      assertEquals(control.readUserInfoFromDb("missing"), None)
      assertEquals(control.findUserWithMail("missing@example.invalid"), Nil)
    }
  }

  test("duplicate registration rejects an existing email or ID without replacing the account") {
    assert(register().accountCreated)
    val duplicateMail = register(bob.copy(mail = "ALICE@EXAMPLE.INVALID"))
    val duplicateId = register(bob.copy(id = alice.id))
    assertEquals(duplicateMail, CreateAccountResponse(false, None))
    assertEquals(duplicateId, CreateAccountResponse(false, None))
    queries { control =>
      assertEquals(control.readUserInfoFromDb(alice.id), Some(UserInDatabase(normalized(alice), config)))
      assertEquals(control.readUserInfoFromDb(bob.id), None)
    }
  }

  test("account updates persist changed identity and settings without modifying another account") {
    assert(register().accountCreated)
    assert(register(bob).accountCreated)
    val changed = alice.copy(name = "Updated ' name", mail = "Changed@Example.Invalid")
    val settings = UserConfig(Nil, isOnlineAccount = true)
    val response = Await.result(SqlUserCommands.handleUpdateAccountCommand(
      UpdateAccountRequest(AllUserInfo(changed, None, settings)), logger), 10.seconds)
    assert(response.accountChanged)
    assertEquals(response.token.info.user, changed)
    queries { control =>
      assertEquals(control.findUserWithMail(alice.mail), Nil)
      assertEquals(control.readUserInfoFromDb(alice.id), Some(UserInDatabase(normalized(changed), settings)))
      assertEquals(control.readUserInfoFromDb(bob.id), Some(UserInDatabase(bob, config)))
    }
  }

  test("mail and ID lookups treat SQL syntax as literal input") {
    assert(register().accountCreated)
    queries { control =>
      assertEquals(control.findUserWithMail("' OR 1=1 -- "), Nil)
      assertEquals(control.readUserInfoFromDb("' OR 1=1 -- "), None)
      assertEquals(control.findAccessTokens("' OR 1=1 -- "), None)
    }
  }

  test("token provisioning persists and reuses a live token, replaces expired tokens, and rejects unknown users") {
    assert(register().accountCreated)
    queries { control =>
      assertEquals(control.ensureAndGetUserTokenFromDb("missing", logger), None)
      val before = LocalDateTime.now().minusSeconds(1)
      val first = control.ensureAndGetUserTokenFromDb(alice.id, logger).get
      assert(first.expires.isAfter(before))
      val stored = control.findAccessTokens(alice.id).get
      assertEquals(stored.token, first.token)
      // MySQL rounds nanoseconds to the column's microsecond precision.
      assert(math.abs(java.time.Duration.between(first.expires, stored.expires).toNanos) <= 1000)
      assertEquals(control.ensureAndGetUserTokenFromDb(alice.id, logger), Some(stored))

      val expired = SingleAccessToken("expired-fixture", LocalDateTime.now().minusDays(1).withNano(123456000))
      control.upsertToken(alice.id, expired)
      assertEquals(control.findAccessTokens(alice.id), Some(expired))
      val renewed = control.ensureAndGetUserTokenFromDb(alice.id, logger).get
      assert(renewed.token != expired.token)
      assert(renewed.expires.isAfter(LocalDateTime.now()))
      assertEquals(control.findAccessTokens(alice.id).map(_.token), Some(renewed.token))
    }
  }

  test("login validates the stored code, account and database expiry rather than the submitted expiry") {
    assert(register().accountCreated)
    assert(register(bob).accountCreated)
    val token = SingleAccessToken("alice-fixture", LocalDateTime.now().plusHours(1).withNano(123456000))
    queries(_.upsertToken(alice.id, token))
    assert(!login(alice.mail, token.copy(token = "wrong")).loginSucceeded)
    assert(!login(bob.mail, token).loginSucceeded)
    val unknown = login("missing@example.invalid", token)
    assert(!unknown.userKnown)
    assert(!unknown.loginSucceeded)
    val response = login("ALICE@EXAMPLE.INVALID", token.copy(expires = LocalDateTime.now().minusYears(1)))
    assert(response.loginSucceeded)
    assertEquals(response.allUserInfo.map(_.user), Some(normalized(alice)))
    assertEquals(response.allUserInfo.map(_.config), Some(config))
    assert(response.allUserInfo.flatMap(_.token).exists(AuthHandling.isTokenValid(logger, _)))

    queries(_.upsertToken(alice.id, token.copy(expires = LocalDateTime.now().minusDays(1))))
    assert(!login(alice.mail, token).loginSucceeded)
  }

  test("signed-token login is bound to the stored account and safely rejects missing accounts") {
    val token = register().token.get
    assert(register(bob).accountCreated)
    def signedLogin(mail: String): LoginResponse = Await.result(SqlUserCommands.handleLoginCommand(
      LoginRequest(mail, Right(token)), logger), 10.seconds)
    assert(signedLogin(alice.mail).loginSucceeded)
    assert(!signedLogin(bob.mail).loginSucceeded)
    assert(!signedLogin("missing@example.invalid").loginSucceeded)
  }

  test("malformed account settings do not block identity lookup or login and leave other settings intact") {
    assert(register().accountCreated)
    assert(register(bob).accountCreated)
    val otherSettings = UserConfig(Nil, isOnlineAccount = true)
    queries(_.upsertUser(bob, otherSettings))
    withDatabase { connection =>
      Using.resource(connection.prepareStatement("UPDATE `user` SET `config` = ? WHERE `id` = ?")) { statement =>
        statement.setString(1, "{invalid settings")
        statement.setString(2, alice.id)
        assertEquals(statement.executeUpdate(), 1)
      }
    }
    val token = queries { control =>
      assertEquals(control.readUserInfoFromDb(alice.id), Some(UserInDatabase(normalized(alice), UserConfig.onlineDefaults)))
      assertEquals(control.readUserInfoFromDb(bob.id).map(_.config), Some(otherSettings))
      control.ensureAndGetUserTokenFromDb(alice.id, logger).get
    }
    assert(login(alice.mail, token).loginSucceeded)
  }

  private def logRows(table: String): List[List[String]] = withDatabase { connection =>
    Using.resource(connection.createStatement()) { statement =>
      Using.resource(statement.executeQuery(s"SELECT `type`, `userid`, `ipAddress`, `payload` FROM `$table` ORDER BY `id`")) { rows =>
        Iterator.continually(rows.next()).takeWhile(identity).map { _ =>
          (1 to 4).map(rows.getString).toList
        }.toList
      }
    }
  }

  test("ordinary and sync commands log in separate tables with account IDs and IPv4/IPv6 addresses") {
    val token = register().token.get
    val address = InetAddress.getByName("2001:db8::42")
    val ordinary = ExecutionCommand("login-command", ujson.Obj("event" -> "fixture"))
    SqlLogCommands.handleLog(LocalDateTime.now(), ordinary, Some(token), address, logger)
    val sync = ExecutionCommand(SQLCommands.StoreToDbCommand.name, ujson.Obj("event" -> "sync fixture"))
    SqlLogCommands.handleLog(LocalDateTime.now(), sync, None, InetAddress.getByName("192.0.2.12"), logger)
    assertEquals(logRows("commandLog").map(_.take(3)), List(List(ordinary.name, alice.id, address.getHostAddress)))
    assertEquals(logRows("syncedLog").map(_.take(3)), List(List(sync.name, User.Anonymous.id, "192.0.2.12")))
  }

  test("logging preserves privacy filtering and disabled destinations perform no SQL") {
    val command = ExecutionCommand(LLMCommands.feedbackLlmCommandFactory.name,
      ujson.Obj("prompt" -> "private learner answer", "systemPrompt" -> "private instructions"))
    SqlLogCommands.handleLog(LocalDateTime.now(), command, None, InetAddress.getLoopbackAddress, logger)
    val payload = logRows("commandLog").head(3)
    assert(ujson.read(payload)("json")("redacted").bool)
    assert(!payload.contains("private"))
    val disabled = ExecutionCommandFactory[String, String]("disabled", storeLogIntoTable = None)
    val result = SqlLogCommands(null, logger).logCommand(LocalDateTime.now(), ExecutionCommand(disabled.name, ujson.Obj()),
      None, InetAddress.getLoopbackAddress, disabled.storeLogIntoTable)
    assertEquals(result.elementsAdded, 0)
    assertEquals(logRows("commandLog").size, 1)
    assertEquals(logRows("syncedLog"), Nil)
  }

  test("a configured custom log table receives the same structured event") {
    val definition = ExecutionCommandFactory[String, String]("custom-audit",
      storeLogIntoTable = Some(DatabaseTable("customCommandLog")))
    withDatabase { connection =>
      Using.resource(connection.createStatement())(_.executeUpdate("CREATE TABLE `customCommandLog` LIKE `commandLog`"))
      try {
        val command = ExecutionCommand(definition.name, ujson.Obj("event" -> "custom"))
        val result = SqlLogCommands(connection, logger).logCommand(LocalDateTime.now(), command,
          None, InetAddress.getByName("192.0.2.1"), definition.storeLogIntoTable)
        assertEquals(result.elementsAdded, 1)
        assertEquals(logRows("customCommandLog").map(_.take(3)),
          List(List(definition.name, User.Anonymous.id, "192.0.2.1")))
        assertEquals(logRows("commandLog"), Nil)
      } finally Using.resource(connection.createStatement())(_.executeUpdate("DROP TABLE `customCommandLog`"))
    }
  }
}
