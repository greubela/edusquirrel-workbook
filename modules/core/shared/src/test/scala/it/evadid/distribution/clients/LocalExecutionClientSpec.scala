package it.evadid.distribution.clients

import it.evadid.distribution.command.{ExecutionCommand, SerializedException}
import it.evadid.util.logging.{BasicLogger, Logger}
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import munit.FunSuite

class LocalExecutionClientSpec extends FunSuite {
  private def client(run: Int => Future[Int]): ExecutionClient = new LocalExecutionClient[Int, Int] {
    override def canExecuteCommand(command: ExecutionCommand): Boolean = command.name == "calculate"
    override protected def rawExecution(logger: Logger, input: Int): Future[Int] = run(input)
  }
  private val command = ExecutionCommand("calculate", ujson.Num(3))

  test("decoding failures return a failed Future without invoking the handler") {
    val called = Promise[Unit]()
    client { _ => called.success(()); Future.successful(1) }
      .executeResponse(BasicLogger(), command.copy(json = ujson.Str("not an integer"))).failed.map { error =>
        assert(error.getMessage.contains("Unsupported request format"))
        assert(!called.isCompleted)
      }
  }

  test("synchronous and asynchronous failures use the same response envelope and preserve causes") {
    val error = SerializedException("calculation failed", IllegalStateException("root cause"))
    val clients = List(client(_ => throw error), client(_ => Future.failed(error)))
    Future.traverse(clients)(_.executeResponse(BasicLogger(), command)).map { responses =>
      responses.foreach { response =>
        assert(response.result.left.toOption.get eq error)
        assertEquals(response.result.left.toOption.get.cause.map(_.msg), Some("root cause"))
      }
    }
  }

  test("completion waits for the handler and decodes its successful result") {
    val entered = Promise[Int]()
    val completed = Promise[Int]()
    val response = client { value => entered.success(value); completed.future }.executeResponse(BasicLogger(), command)
    entered.future.flatMap { value =>
      assertEquals(value, 3)
      assert(!response.isCompleted)
      completed.success(9)
      response.map(result => assertEquals(result.successOrThrow[Int](BasicLogger(), command, result.timestamps.timestampReceived).result, 9))
    }
  }
}
