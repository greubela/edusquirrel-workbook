package it.evadid.server.database

import it.evadid.util.logging.BasicLogger
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import munit.FunSuite
import scala.concurrent.ExecutionContext

class UserAccountDatabaseSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  private def connection(closes: AtomicInteger): Connection = Proxy.newProxyInstance(
    classOf[Connection].getClassLoader, Array(classOf[Connection]), new InvocationHandler {
      override def invoke(proxy: Object, method: Method, args: Array[Object]): Object = method.getName match {
        case "close" => closes.incrementAndGet(); null
        case other => throw UnsupportedOperationException(s"Unexpected JDBC call: $other")
      }
    }).asInstanceOf[Connection]

  test("each operation opens one connection and closes it before completing") {
    val opens = new AtomicInteger()
    val closes = new AtomicInteger()
    val database = new UserAccountDatabase(() => { opens.incrementAndGet(); connection(closes) })
    database.execute(BasicLogger())(_ => "result").flatMap { result =>
      assertEquals(result, "result")
      assertEquals(opens.get(), 1)
      assertEquals(closes.get(), 1)
      database.execute(BasicLogger())(_ => 42).map { result =>
        assertEquals(result, 42)
        assertEquals(opens.get(), 2)
        assertEquals(closes.get(), 2)
      }
    }
  }

  test("failed operations close their connection and preserve the original failure") {
    val closes = new AtomicInteger()
    val error = IllegalStateException("account operation failed")
    val database = new UserAccountDatabase(() => connection(closes))
    database.execute(BasicLogger())(_ => throw error).failed.map { failure =>
      assert(failure eq error)
      assertEquals(closes.get(), 1)
    }
  }

  test("connection failures fail the Future without running the operation") {
    val error = IllegalStateException("connection failed")
    val operations = new AtomicInteger()
    val database = new UserAccountDatabase(() => throw error)
    database.execute(BasicLogger())(_ => operations.incrementAndGet()).failed.map { failure =>
      assert(failure eq error)
      assertEquals(operations.get(), 0)
    }
  }
}
