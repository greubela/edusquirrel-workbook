package it.evadid.distribution.clients

import it.evadid.distribution.command.{ExecutionCommand, ExecutionInfo, SerializedException}
import it.evadid.util.logging.{BasicLogger, Logger}
import munit.FunSuite

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ExecutionClientPoolTest extends FunSuite {
  private type Result = Map[String, String]

  /** A client for the commands `accepts` admits whose execution is `run`. */
  private def client(accepts: ExecutionCommand => Boolean)(run: => Future[Result]): ExecutionClient =
    new LocalExecutionClient[ujson.Value, Result] {
      override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = accepts(executionCommand)
      override protected def rawExecution(logger: Logger, input: ujson.Value): Future[Result] = run
    }

  private def failingClient(commandName: String, failure: Throwable = Exception("database connection failed")): ExecutionClient =
    client(_.name == commandName)(Future.failed(failure))

  private def handle(clients: List[ExecutionClient], commandName: String): Future[ExecutionInfo[Result]] =
    ExecutionClientPool(clients).handleCommand[Result](BasicLogger(), ExecutionCommand(commandName, ujson.Obj()))

  test("reports no matching handler separately from matching handler failures") {
    handle(List(failingClient("sync-to-db-request")), "missing-command").failed.map { error =>
      assertEquals(error.getMessage, "ExecutionClientPool: no handler for command missing-command registered!")
    }
  }

  test("reports matching handler failures without claiming the handler is missing") {
    handle(List(failingClient("sync-to-db-request")), "sync-to-db-request").failed.map { error =>
      assert(error.isInstanceOf[SerializedException])
      assert(error.getMessage.contains("ExecutionClientPool: all handlers for command sync-to-db-request failed"))
      assert(error.getMessage.contains("database connection failed"))
    }
  }

  test("reports number of attempted handlers and retains the last failure as cause") {
    handle(List(failingClient("sync", Exception("first failure")),
      failingClient("sync", Exception("last failure", Exception("underlying reason")))), "sync").failed.map { error =>
      val serialized = error.asInstanceOf[SerializedException]
      assert(serialized.msg.contains("2 handlers tried"))
      assert(serialized.msg.contains("last handler failed because: last failure"))
      assertEquals(serialized.cause.map(_.msg), Some("last failure"))
      assertEquals(serialized.cause.flatMap(_.cause).map(_.msg), Some("underlying reason"))
    }
  }

  test("handler summaries retain SQL diagnostics after a long operation description") {
    val detail = "SQL error while " + ("query context " * 20) + "column limits (SQLState=22001, errorCode=1406)"
    handle(List(failingClient("sync", Exception(detail))), "sync").failed.map { error =>
      assert(error.getMessage.contains("SQLState=22001"))
      assert(error.getMessage.contains("errorCode=1406"))
    }
  }

  test("a later handler can still succeed after an earlier handler fails") {
    val successful = client(_.name == "sync-to-db-request")(Future.successful(Map("status" -> "stored")))
    handle(List(failingClient("sync-to-db-request"), successful), "sync-to-db-request").map { result =>
      assertEquals(result.resultOp, Some(Map("status" -> "stored")))
    }
  }

  test("synchronous handler failures fall back just like failed Futures") {
    val synchronous = client(_ => true)(throw IllegalStateException("synchronous failure"))
    val fallback = client(_ => true)(Future.successful(Map("status" -> "fallback")))
    handle(List(synchronous, fallback), "test").map { result =>
      assertEquals(result.resultOp, Some(Map("status" -> "fallback")))
    }
  }
}
