package it.evadid.server.commandHandler.sql.sync

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.commandTypes.SQLCommands.StoreToDbRequest
import it.evadid.sql.{DatabaseConfig, DatabaseHistoryFormat}
import it.evadid.util.logging.BasicLogger
import it.evadid.workbook.interaction.sync.{UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.{Connection, PreparedStatement, Timestamp, SQLException}
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import munit.FunSuite
import upickle.default.*

class UpsertSnapshotSpec extends FunSuite {
  private val context = UsageContext("program", "scenario", "student")
  private val sync = context.toSyncContext("answer")
  private val time = LocalDateTime.parse("2026-11-12T12:00:00.123456789")
  private val config = DatabaseConfig("mysql", "unused", "3306", "unused")
  private val history = InteractionVariableHistory.empty[String, Unit, Unit]

  private def jdbc[A](api: Class[A])(call: (String, Array[Object]) => Object): A =
    Proxy.newProxyInstance(api.getClassLoader, Array(api), new InvocationHandler {
      override def invoke(proxy: Object, method: Method, args: Array[Object]): Object =
        if method.getName == "toString" then "snapshot statement" else call(method.getName, args)
    }).asInstanceOf[A]

  test("relational events format binds the timestamp and close their statement after a successful write") {
    val closes = new AtomicInteger()
    val bindings = new AtomicInteger()
    val statement = jdbc(classOf[PreparedStatement]) { (name, args) => name match {
      case "setString" => bindings.incrementAndGet(); null
      case "setTimestamp" =>
        assertEquals(args(0), Int.box(4))
        assertEquals(args(1), Timestamp.valueOf(time))
        bindings.incrementAndGet(); null
      case "executeUpdate" => Int.box(2)
      case "clearParameters" => null
      case "close" => closes.incrementAndGet(); null
      case other => throw AssertionError(other)
    }}
    val connection = jdbc(classOf[Connection]) { (name, args) =>
      assertEquals(name, "prepareStatement")
      val sql = args(0).asInstanceOf[String]
      assert(sql.contains("answer_event"))
      statement
    }
    val control = UpsertToDatabase(connection, context, BasicLogger())
    val request = StoreToDbRequest(sync, write(history), config, DatabaseHistoryFormat.RelationalEvents)
    val result = control.upsertIntoRelationalTables(request)
    assertEquals((result.elementsAdded, result.elementsChanged), (0, 0))
    assertEquals(bindings.get(), 0)
    assertEquals(closes.get(), 1)
  }

  test("relational events write failures close the statement and report SQL diagnostics") {
    val closes = new AtomicInteger()
    val error = SQLException("write failed", "23000", 17)
    val statement = jdbc(classOf[PreparedStatement]) { (name, _) => name match {
      case "setString" | "setTimestamp" => null
      case "executeUpdate" => throw error
      case "close" => closes.incrementAndGet(); null
      case other => throw AssertionError(other)
    }}
    val connection = jdbc(classOf[Connection]) { (name, _) =>
      assertEquals(name, "prepareStatement")
      statement
    }
    val request = StoreToDbRequest(sync, write(history), config, DatabaseHistoryFormat.RelationalEvents)
    val reported = intercept[SQLException](UpsertToDatabase(connection, context, BasicLogger()).upsertIntoRelationalTables(request))
    assert(reported.getMessage.contains("executing database update"))
    assertEquals(reported.getSQLState, error.getSQLState)
    assertEquals(reported.getErrorCode, error.getErrorCode)
    assert(closes.get() > 0)
  }

  test("empty snapshots do not prepare a statement") {
    val connection = jdbc(classOf[Connection])((name, _) => throw AssertionError("Unexpected database access: " + name))
    val control = UpsertToDatabase(connection, context, BasicLogger())
    val request = StoreToDbRequest(sync, write(InteractionVariableHistory.empty[String, Unit, Unit]), config, DatabaseHistoryFormat.RelationalEvents)
    val result = control.upsertIntoRelationalTables(request)
    assertEquals(result.elementsChanged, 0)
  }
}
