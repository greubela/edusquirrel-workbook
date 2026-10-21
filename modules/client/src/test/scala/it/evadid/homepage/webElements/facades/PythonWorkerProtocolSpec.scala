package it.evadid.homepage.webElements.facades

import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.*
import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.distribution.formats.WorkerExecutionProtocol.{Request, Response}
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.{AdditionalLoggingOutput, ExecutionClientResponseTimestamps}
import it.evadid.distribution.command.SerializedException
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import java.time.LocalDateTime
import upickle.default.*
import todomove.`export`.workers.PyodideWorkerClient

class PythonWorkerProtocolSpec extends EditorDomSuite {
  private val sent = Var(Option.empty[RunPythonRequest])
  private val failing = Var(false)
  private val terminated = Var(false)

  private class FakeWorker(url: String) extends js.Object {
    private val message = Var(Option.empty[js.Function1[dom.MessageEvent, Unit]])
    private val error = Var(Option.empty[js.Function1[dom.ErrorEvent, Unit]])
    def onmessage: js.UndefOr[js.Function1[dom.MessageEvent, Unit]] = message.now().fold[js.UndefOr[js.Function1[dom.MessageEvent, Unit]]](js.undefined)(identity)
    def onmessage_=(handler: js.Function1[dom.MessageEvent, Unit]): Unit = message.set(Some(handler))
    def onerror: js.UndefOr[js.Function1[dom.ErrorEvent, Unit]] = error.now().fold[js.UndefOr[js.Function1[dom.ErrorEvent, Unit]]](js.undefined)(identity)
    def onerror_=(handler: js.Function1[dom.ErrorEvent, Unit]): Unit = error.set(Some(handler))
    def terminate(): Unit = terminated.set(true)
    def postMessage(encoded: String): Unit = {
      val request = read[Request](encoded)
      assertEquals(request.command.name, "worker.runPython")
      sent.set(Some(read[RunPythonRequest](request.command.json)))
      val now = LocalDateTime.now()
      val timestamps = ExecutionClientResponseTimestamps(now, now, now)
      val response = if failing.now() then ExecutionClientResponse.forError(timestamps, SerializedException("bad Python"),
        request.command, AdditionalLoggingOutput("before error", "traceback"))
      else ExecutionClientResponse.forValue(timestamps, RunPythonResult(ujson.Null, "output", "",
        List(PythonCallback("turtle", "forward", List(ujson.Num(12), ujson.Str("label"))))), request.command)
      setTimeout(0) {
        message.now().get(js.Dynamic.literal(data = write(Response(request.requestId, response))).asInstanceOf[dom.MessageEvent])
      }
    }
  }

  private def workerTest[A](failure: Boolean)(run: PyodideWorkerClient => scala.concurrent.Future[A]): scala.concurrent.Future[A] = {
    val global = js.Dynamic.global.globalThis
    val previous = global.selectDynamic("Worker")
    failing.set(failure); terminated.set(false); sent.set(None)
    val script = dom.document.createElement("script")
    script.setAttribute("data-eva-client", "")
    script.setAttribute("src", "https://example.test/artifacts/newest/client.js")
    dom.document.head.appendChild(script)
    global.updateDynamic("Worker")(js.constructorOf[FakeWorker])
    val client = new PyodideWorkerClient()
    run(client).andThen { case _ =>
      client.terminate()
      assert(terminated.now())
      global.updateDynamic("Worker")(previous)
      script.remove()
    }
  }

  test("Python consumers use typed worker commands and preserve flags, context and callback arguments") {
    workerTest(false) { client =>
      client.addCallbacks("turtle", Seq("forward")).flatMap(_ => client.run("forward(12)",
        PythonRunConfig(context = js.Dictionary("answer" -> 42), resetGlobals = false, captureStderr = false))).map { report =>
        assertEquals(sent.now().get.callbacks, Map("turtle" -> List("forward")))
        assertEquals(sent.now().get.context, Map("answer" -> ujson.Num(42)))
        assert(!sent.now().get.resetGlobals)
        assert(!sent.now().get.captureStderr)
        assertEquals(report.stdout, "output")
        assertEquals(report.callbackOps.head.args.map(_.toString), Vector("12", "label"))
      }
    }
  }
  test("Python failures retain captured output from the worker envelope") {
    workerTest(true) { client => client.run("raise Exception()").failed.map { error =>
      val failure = error.asInstanceOf[PythonWorkerFailure]
      assertEquals(failure.stdout, "before error")
      assertEquals(failure.stderr, "traceback")
      assertEquals(failure.message, "bad Python")
    }}
  }
}
