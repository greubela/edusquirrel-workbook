package it.evadid.server.commandHandler.sql

import it.evadid.util.logging.BasicLogger
import munit.FunSuite

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.{Connection, PreparedStatement, ResultSet}
import java.time.LocalDateTime

class LoginTokenTimestampSpec extends FunSuite {
  private val parser = GenericSqlFunctionality(null, BasicLogger())

  List("2026-10-13 01:40:00", "2026-10-13 01:40:00.0",
    "2026-10-13 01:40:00.123456", "2026-10-13 01:40:00.123456789",
    "2026-10-13T01:40:00").foreach { value =>
    test(s"token expiry accepts $value") {
      assertEquals(parser.parseDatabaseTimestamp(value), LocalDateTime.parse(value.replace(' ', 'T')))
    }
  }

  test("invalid expiry fails rather than turning into a valid token") {
    intercept[RuntimeException](parser.parseDatabaseTimestamp("not a timestamp"))
  }

  private def stub[T](kind: Class[T])(handle: (String, Array[AnyRef]) => AnyRef): T =
    Proxy.newProxyInstance(kind.getClassLoader, Array(kind), new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
        handle(method.getName, Option(args).getOrElse(Array.empty[AnyRef]))
    }).asInstanceOf[T]

  test("login reads a token and its fractional expiry through JDBC without a database dependency") {
    var row = -1
    val fields = Map("userId" -> "user-1", "token" -> "secret", "tokenExpires" -> "2026-10-13 01:40:00.123456")
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
    val token = SqlUserCommands(connection, BasicLogger()).findAccessTokens("user-1").get
    assertEquals(token.token, "secret")
    assertEquals(token.expires, LocalDateTime.parse("2026-10-13T01:40:00.123456"))
  }
}
