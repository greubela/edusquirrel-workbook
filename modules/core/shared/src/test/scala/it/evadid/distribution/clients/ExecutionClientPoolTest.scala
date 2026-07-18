package it.evadid.distribution.clients

import it.evadid.distribution.command.{ExecutionCommand, SerializedException}
import it.evadid.util.logging.{BasicLogger, Logger}
import munit.FunSuite

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

class ExecutionClientPoolTest extends FunSuite {

  private def failingClient(commandName: String): ExecutionClient = new LocalExecutionClient {
    override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = executionCommand.name == commandName

    override protected def executeCommand(executionCommand: ExecutionCommand, logger: Logger): Future[Map[String, String]] =
      Future.failed(new Exception("database connection failed"))
  }

  private def failingClient(commandName: String, failure: Throwable): ExecutionClient = new LocalExecutionClient {
    override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = executionCommand.name == commandName

    override protected def executeCommand(executionCommand: ExecutionCommand, logger: Logger): Future[Map[String, String]] =
      Future.failed(failure)
  }

  test("reports no matching handler separately from matching handler failures") {
    val pool = ExecutionClientPool(List(failingClient("sync-to-db-request")))

    val error = intercept[SerializedException] {
      Await.result(pool.handleExecution(ExecutionCommand("missing-command", Map.empty), BasicLogger()), 5.seconds)
    }

    assertEquals(error.getMessage, "ExecutionClientPool: no handler for command missing-command registered!")
  }

  test("reports matching handler failures without claiming the handler is missing") {
    val pool = ExecutionClientPool(List(failingClient("sync-to-db-request")))

    val error = intercept[SerializedException] {
      Await.result(pool.handleExecution(ExecutionCommand("sync-to-db-request", Map.empty), BasicLogger()), 5.seconds)
    }

    assert(error.getMessage.contains("ExecutionClientPool: all handlers for command sync-to-db-request failed"))
  }

  test("preserves the original handler failure cause chain") {
    val rootCause = new Exception("Connection refused")
    val handlerFailure = new Exception("Could not connect to database", rootCause)
    val pool = ExecutionClientPool(List(failingClient("sync-to-db-request", handlerFailure)))

    val error = intercept[SerializedException] {
      Await.result(pool.handleExecution(ExecutionCommand("sync-to-db-request", Map.empty), BasicLogger()), 5.seconds)
    }

    assert(error.getMessage.contains("ExecutionClientPool: all handlers for command sync-to-db-request failed"))
  }
}
