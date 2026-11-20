package it.evadid.distribution.clients

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.command.{ExecutionCommand, SerializedException}
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.distribution.commandTypes.WorkerCommands.{RunPythonRequest, RunPythonResult}
import it.evadid.distribution.formats.{ExecutionClientResponse, WorkerExecutionProtocol}
import it.evadid.distribution.formats.ExecutionClientResponse.{AdditionalLoggingOutput, ExecutionClientResponseTimestamps}
import it.evadid.facades.FakeWorker
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import munit.FunSuite
import upickle.default.*
import java.time.LocalDateTime
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Browser-worker clients over the DOM worker transport, with the worker modelled at its message boundary. */
class PythonExecutionClientSpec extends FunSuite {
  private val logger = Logger.withNameAndPrefixes(Some("python-test"), PrintToStdLogger.printNothing)
  private val now = LocalDateTime.parse("2026-11-20T10:00:00")
  private val times = ExecutionClientResponseTimestamps(now, now, now)

  private def request(worker: FakeWorker, index: Int = 0): WorkerExecutionProtocol.Request =
    read[WorkerExecutionProtocol.Request](worker.posted(index).asInstanceOf[String])

  private def respond(worker: FakeWorker, index: Int)(response: ExecutionCommand => ExecutionClientResponse): Unit = {
    val sent = request(worker, index)
    worker.deliver(write(WorkerExecutionProtocol.Response(sent.requestId, response(sent.command))))
  }

  test("Python runs are posted as typed worker commands and decode the interpreter result") {
    FakeWorker.installed {
      val client = PythonExecutionClient.worker("http://localhost/artifacts/newest/worker.js")
      val worker = FakeWorker.created.head
      assertEquals(worker.url, "http://localhost/artifacts/newest/worker.js")
      val result = client.run(RunPythonRequest("print('hi')", packages = Seq("numpy")))
      val sent = request(worker)
      assertEquals(sent.command.name, WorkerCommands.runPython.name)
      assertEquals(read[RunPythonRequest](sent.command.json), RunPythonRequest("print('hi')", packages = Seq("numpy")))
      val expected = RunPythonResult(ujson.Num(1), "hi\n", "", Nil)
      respond(worker, 0)(command => ExecutionClientResponse.forValue(times, expected, command))
      result.map(value => assertEquals(value, expected))
    }
  }

  test("interpreter errors keep the captured output and readiness probes an empty program") {
    FakeWorker.installed {
      val client = PythonExecutionClient.worker("http://localhost/worker.js")
      val worker = FakeWorker.created.head
      val failed = client.run(RunPythonRequest("1/0"))
      respond(worker, 0)(command => ExecutionClientResponse.forError(times, SerializedException("ZeroDivisionError"),
        command, AdditionalLoggingOutput("partial", "Traceback")))
      val ready = client.ready()
      assertEquals(read[RunPythonRequest](request(worker, 1).command.json).code, "")
      respond(worker, 1)(command => ExecutionClientResponse.forValue(times, RunPythonResult(ujson.Null, "", "", Nil), command))
      failed.failed.zip(ready).map { (error, _) =>
        assertEquals(error, PythonExecutionClient.Failure("ZeroDivisionError", "partial", "Traceback"))
      }
    }
  }

  test("closing a Python client terminates its worker and rejects later runs") {
    FakeWorker.installed {
      val client = PythonExecutionClient.worker("http://localhost/worker.js")
      val pending = client.run(RunPythonRequest("while True: pass"))
      client.close()
      assert(FakeWorker.created.head.terminated)
      pending.failed.zip(client.run(RunPythonRequest("1")).failed).map { (inFlight, later) =>
        assertEquals(inFlight.getMessage, "Worker execution client closed")
        assertEquals(later.getMessage, "Worker execution client closed")
      }
    }
  }

  test("script errors and undecodable messages from the worker fail the pending requests") {
    FakeWorker.installed {
      val crashing = WebWorkerExecutionClient("http://localhost/crash.js")
      val undecodable = WebWorkerExecutionClient("http://localhost/clone.js", timeoutMilliseconds = 5000)
      val List(crashWorker, cloneWorker) = FakeWorker.created.toList: @unchecked
      val command = ExecutionCommand(WorkerCommands.health.name, ujson.Obj())
      val first = crashing.executeResponse(logger, command)
      val second = undecodable.executeResponse(logger, command)
      crashWorker.crash("ReferenceError: pyodide is not defined", "worker.js", 12)
      cloneWorker.undecodable()
      assert(crashWorker.terminated && cloneWorker.terminated)
      assert(!crashing.canExecuteCommand(command))
      first.failed.zip(second.failed).map { (crash, clone) =>
        assertEquals(crash.getMessage, "Worker error: ReferenceError: pyodide is not defined at worker.js:12")
        assertEquals(clone.getMessage, "Worker message could not be decoded")
      }
    }
  }

  test("worker responses must be JSON strings") {
    FakeWorker.installed {
      val client = WebWorkerExecutionClient.fromWorker(new org.scalajs.dom.Worker("http://localhost/w.js"))
      val reply = client.executeResponse(logger, ExecutionCommand(WorkerCommands.health.name, ujson.Obj()))
      FakeWorker.created.head.deliver(scala.scalajs.js.Dynamic.literal(requestId = "1"))
      reply.failed.map { error =>
        assertEquals(error.getMessage, "Malformed worker response")
        assertEquals(error.asInstanceOf[SerializedException].cause.map(_.msg), Some("Worker response must be a JSON string"))
      }
    }
  }

  test("remote execution configurations create HTTPS clients for the configured backend") {
    val token = None: Option[SignedToken]
    val executor = RemoteExecutionConfig("backend.example.org", 8443).executor(token)
    assertEquals(executor, JsRemoteExecutionClient("backend.example.org", 8443, None))
    assertEquals(read[RemoteExecutionConfig](write(RemoteExecutionConfig("b", 1))), RemoteExecutionConfig("b", 1))
    Future.unit
  }
}
