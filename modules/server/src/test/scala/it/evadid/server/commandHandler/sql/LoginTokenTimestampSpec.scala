package it.evadid.server.commandHandler.sql

import it.evadid.util.logging.BasicLogger
import munit.FunSuite

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.{Connection, PreparedStatement, ResultSet}
import java.time.LocalDateTime

class LoginTokenTimestampSpec extends FunSuite {
  private val parser = GenericSqlFunctionality(null, BasicLogger())

  List("2026-10-13 01:40:00", "2026-10-13 01:40:00.0",
    "2026-10-13 01:40:00.123456", "2026-10-13 01:40:00.123456789").foreach { value =>
    test(s"token expiry accepts $value") {
      assertEquals(parser.parseDatabaseTimestamp(value), LocalDateTime.parse(value.replace(' ', 'T')))
    }
  }

  test("unsupported expiry formats are rejected rather than interpreted as legacy data") {
    List("not a timestamp", "2026-10-13T01:40:00").foreach(value =>
      intercept[RuntimeException](parser.parseDatabaseTimestamp(value)))
  }

  private def stub[T](kind: Class[T])(handle: (String, Array[AnyRef]) => AnyRef): T =
    Proxy.newProxyInstance(kind.getClassLoader, Array(kind), new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
        handle(method.getName, Option(args).getOrElse(Array.empty[AnyRef]))
    }).asInstanceOf[T]

  for ((code, expiry) <- List("secret" -> "2026-10-13 01:40:00.123456", "" -> "2026-10-13 01:40:00.123456",
      "secret" -> "invalid expiry", "secret" -> "2026-10-13T01:40:00", "secret" -> null)) {
  test(s"stored token with blank code=${code.isEmpty} and expiry=$expiry is read or safely discarded") {
    var row = -1
    val fields = Map("userId" -> "user-1", "token" -> code, "tokenExpires" -> expiry)
    val result = stub(classOf[ResultSet]) { (method, args) => method match {
      case "next" => row += 1; java.lang.Boolean.valueOf(row == 0)
      case "getString" =>
        if (row != 0) throw new java.sql.SQLException("Cursor is not on a row")
        fields(args(0).toString)
      case _ => null
    }}
    val statement = stub(classOf[PreparedStatement]) { (method, _) =>
      if (method == "executeQuery") result else if (method == "toString") "test token query" else null
    }
    val connection = stub(classOf[Connection]) { (method, _) =>
      if (method == "prepareStatement") statement else null
    }
    val token = SqlUserCommands(connection, BasicLogger()).findAccessTokens("user-1")
    if code.nonEmpty && expiry == "2026-10-13 01:40:00.123456" then {
      assertEquals(token.map(_.token), Some("secret"))
      assertEquals(token.map(_.expires), Some(LocalDateTime.parse("2026-10-13T01:40:00.123456")))
    } else assertEquals(token, None)
  }
  }
  test("invalid account preferences cannot prevent identity lookup and JDBC reads begin on the first row") {
    import it.evadid.core.datastructures.user.UserConfig
    val configured = UserConfig(Nil, isOnlineAccount = true)
    val rows = List(
      Map("id" -> "one", "name" -> "First", "mail" -> "first@example.org", "config" -> upickle.default.write(configured)),
      Map("id" -> "two", "name" -> "Second", "mail" -> "second@example.org", "config" -> "invalid settings"))
    var row = -1
    var prematureReads = 0
    val result = stub(classOf[ResultSet]) { (method, args) => method match {
      case "next" => row += 1; java.lang.Boolean.valueOf(row < rows.size)
      case "getString" =>
        if (row < 0 || row >= rows.size) {
          prematureReads += 1
          throw new java.sql.SQLException("Cursor is not on a row")
        }
        rows(row)(args(0).toString)
      case _ => null
    }}
    val statement = stub(classOf[PreparedStatement]) { (method, _) =>
      if (method == "executeQuery") result else if (method == "toString") "user query" else null
    }
    val connection = stub(classOf[Connection]) { (method, _) =>
      if (method == "prepareStatement") statement else null
    }
    val accounts = SqlUserCommands(connection, BasicLogger()).findUserWithMail("fixture@example.org")
    assertEquals(accounts.map(_.user.id), List("one", "two"))
    assertEquals(accounts.head.config, configured)
    assertEquals(accounts.last.config, UserConfig.onlineDefaults)
    assertEquals(upickle.default.read[UserConfig](upickle.default.write(accounts.last.config)), accounts.last.config)
    assertEquals(prematureReads, 0)
  }

}
