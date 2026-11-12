package it.evadid.server.commandHandler.sql

import it.evadid.util.logging.BasicLogger
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.{PreparedStatement, ResultSet, SQLException}
import java.util.concurrent.atomic.AtomicInteger
import munit.FunSuite

class GenericSqlFunctionalitySpec extends FunSuite {
  private def jdbc[A](api: Class[A])(call: (String, Array[Object]) => Object): A =
    Proxy.newProxyInstance(api.getClassLoader, Array(api), new InvocationHandler {
      override def invoke(proxy: Object, method: Method, args: Array[Object]): Object =
        if method.getName == "toString" then "test JDBC resource" else call(method.getName, args)
    }).asInstanceOf[A]

  private def control(logger: BasicLogger) = GenericSqlFunctionality(logger)

  test("queries discard unreadable rows, close their result set and leave caller-owned statements open") {
    val index = new AtomicInteger(-1)
    val resultCloses = new AtomicInteger()
    val statementCloses = new AtomicInteger()
    val rows = jdbc(classOf[ResultSet]) { (name, _) => name match {
      case "next" => Boolean.box(index.incrementAndGet() < 3)
      case "getString" => if index.get() == 1 then throw SQLException("unreadable row") else s"row-${index.get()}"
      case "close" => resultCloses.incrementAndGet(); null
      case other => throw AssertionError(other)
    }}
    val statement = jdbc(classOf[PreparedStatement]) { (name, _) => name match {
      case "executeQuery" => rows
      case "close" => statementCloses.incrementAndGet(); null
      case other => throw AssertionError(other)
    }}
    val logger = BasicLogger()
    assertEquals(control(logger).executeQuery(statement, List("value")), List(List("row-0"), List("row-2")))
    assertEquals(resultCloses.get(), 1)
    assertEquals(statementCloses.get(), 0)
    assert(logger.getOut().contains("unreadable row") || logger.getErr().contains("unreadable row"))
  }

  test("failed queries preserve the database exception even when statement cleanup fails") {
    val original = SQLException("database offline")
    val cleanup = SQLException("close failed")
    val statement = jdbc(classOf[PreparedStatement]) { (name, _) => name match {
      case "executeQuery" => throw original
      case "close" => throw cleanup
      case other => throw AssertionError(other)
    }}
    val caught = intercept[SQLException](control(BasicLogger()).executeQuery(statement, List("value")))
    assert(caught eq original)
    assertEquals(caught.getSuppressed.toList, List(cleanup))
  }

  test("updates still clear parameters without closing reusable statements") {
    val clears = new AtomicInteger()
    val statement = jdbc(classOf[PreparedStatement]) { (name, _) => name match {
      case "executeUpdate" => Int.box(2)
      case "clearParameters" => clears.incrementAndGet(); null
      case other => throw AssertionError(other)
    }}
    assertEquals(control(BasicLogger()).executeUpdate(statement), 2)
    assertEquals(clears.get(), 1)
  }
}
