package it.evadid.server.account

import it.evadid.util.logging.BasicLogger
import munit.FunSuite

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.{Connection, PreparedStatement, ResultSet}
import java.time.Instant

class AccountRowsSpec extends FunSuite {

  List("2026-10-13T01:40:00.000000000Z", "2026-10-13T01:40:00.123456789Z").foreach { value =>
    test(s"token expiry accepts $value") {
      assertEquals(AccountRows.parseDatabaseTimestamp(value), Instant.parse(value))
    }
  }

  test("unsupported expiry formats are rejected rather than interpreted as legacy data") {
    List("not a timestamp", "2026-10-13T01:40:00", "2026-10-13T01:40:00Z", "2026-10-13 01:40:00.123456", "2026-10-13T01:40:00.123456Z").foreach(value =>
      intercept[RuntimeException](AccountRows.parseDatabaseTimestamp(value)))
  }

  private def stub[T](kind: Class[T])(handle: (String, Array[AnyRef]) => AnyRef): T =
    Proxy.newProxyInstance(kind.getClassLoader, Array(kind), new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
        handle(method.getName, Option(args).getOrElse(Array.empty[AnyRef]))
    }).asInstanceOf[T]

  /** A connection whose prepared statements, described by `query`, answer every query with `result`. */
  private def answering(result: ResultSet, query: String): Connection = {
    val statement = stub(classOf[PreparedStatement]) { (method, _) =>
      if (method == "executeQuery") result else if (method == "toString") query else null
    }
    stub(classOf[Connection]) { (method, _) =>
      if (method == "prepareStatement") statement else null
    }
  }

  for ((code, expiry) <- List("secret" -> "2026-10-13T01:40:00.123456000Z", "" -> "2026-10-13T01:40:00.123456000Z",
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
    val token = AccountRows(answering(result, "test token query"), BasicLogger()).getTokenFromDb("user-1")
    if code.nonEmpty && expiry == "2026-10-13T01:40:00.123456000Z" then {
      assertEquals(token.map(_.token), Some("secret"))
      assertEquals(token.map(_.expires), Some(Instant.parse("2026-10-13T01:40:00.123456Z")))
    } else assertEquals(token, None)
  }
  }
  /** A result set answering `getString` by column name for `rows`, failing reads before the first row. */
  private def accounts(rows: List[Map[String, String]]): (ResultSet, () => Int) = {
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
    result -> (() => prematureReads)
  }

  test("invalid account preferences cannot prevent identity lookup and JDBC reads begin on the first row") {
    import it.evadid.core.datastructures.user.UserConfig
    val configured = UserConfig(Nil, isOnlineAccount = true)
    List(upickle.default.write(configured) -> configured, "invalid settings" -> UserConfig.defaults(isLocalAccount = false))
      .foreach { (stored, expected) =>
        val (result, prematureReads) = accounts(List(Map("id" -> "one", "name" -> "First", "mail" -> "first@example.org", "config" -> stored)))
        val account = AccountRows(answering(result, "user query"), BasicLogger()).findUserWithMail("First@Example.org ")
        assertEquals(account.map(_.user.id), Some("one"))
        assertEquals(account.map(_.config), Some(expected))
        assertEquals(prematureReads(), 0)
      }
  }

  test("mail addresses are stored and looked up trimmed and in lower case") {
    assertEquals(AccountRows.normalizedMail(" Alice@Example.Invalid "), "alice@example.invalid")
  }
}
