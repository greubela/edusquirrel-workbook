package it.evadid.distribution.clients

import it.evadid.distribution.*
import it.evadid.distribution.executor.Executor
import it.evadid.distribution.*
import munit.FunSuite
import org.scalajs.dom
import upickle.default.{read, write}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js

class WorkerExecutionTest extends FunSuite {

  private class FakeWorker extends WorkerLike {
    var onmessage: dom.MessageEvent => Unit = _
    var onerror: dom.ErrorEvent => Any = _

    override def postMessage(message: String): Unit = {
      val payload = read[Map[String, String]](message)
      val requestId = payload("requestId")
      val command = read[ExecutionCommand](payload("command"))
      val response = write(Map(
        "requestId" -> requestId,
        "executionInfo" -> write(
          ExecutionInfo(
            command = command,
            result = scala.util.Success(ExecutionResult(command.params, "ok", "")),
            meta = None
          )
        )(using ExecutionCommand.given_ReadWriter_ExecutionInfo)
      ))

      val event = js.Dynamic.literal(data = response).asInstanceOf[dom.MessageEvent]
      onmessage(event)
    }
  }

  test("worker execution resolves command result from worker response") {
    val fakeWorker = new FakeWorker
    val client = new ExecuteOnWebWorker(fakeWorker)

    val resultF: Future[ExecutionInfo] = client.executeCommand(ExecutionCommand("build", Map("a" -> "b")))

    resultF.map { result =>
      assertEquals(result.command.name, "build")
      assertEquals(result.result.get.data.get("a"), Some("b"))
      assertEquals(result.result.get.stdOut, "ok")
    }
  }
}
