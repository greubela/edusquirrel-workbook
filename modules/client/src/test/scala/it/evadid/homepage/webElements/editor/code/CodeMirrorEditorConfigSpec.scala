package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.distribution.command.SerializedException
import it.evadid.distribution.formats.{ExecutionClientResponse, WorkerExecutionProtocol}
import it.evadid.distribution.formats.ExecutionClientResponse.ExecutionClientResponseTimestamps
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorConfig
import munit.FunSuite
import org.scalajs.dom
import upickle.default.*
import java.time.LocalDateTime
import scala.collection.mutable.ListBuffer
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class CodeMirrorEditorConfigSpec extends FunSuite {
  private def chart(label: String) = PythonFlowchartGutter.Chart(
    List(PythonFlowchartGutter.Node(1, "process", 0, label)), Nil, List(1))

  private class FakeWorker {
    val sent = ListBuffer.empty[String]
    var terminated = false
    private val raw = js.Dynamic.literal(
      postMessage = ((message: String) => { sent += message; () }): js.Function1[String, Unit],
      terminate = (() => { terminated = true }): js.Function0[Unit])
    val client = WebWorkerExecutionClient.fromWorker(raw.asInstanceOf[dom.Worker], timeoutMilliseconds = 1000)
    private def receive(response: ExecutionClientResponse): Unit = {
      val request = read[WorkerExecutionProtocol.Request](sent.head)
      raw.selectDynamic("onmessage").asInstanceOf[js.Function1[dom.MessageEvent, Unit]](
        js.Dynamic.literal(data = write(WorkerExecutionProtocol.Response(request.requestId, response))).asInstanceOf[dom.MessageEvent])
    }
    def reply(result: PythonFlowchartGutter.Chart): Unit = {
      val request = read[WorkerExecutionProtocol.Request](sent.head)
      val now = LocalDateTime.now()
      receive(ExecutionClientResponse.forValue(ExecutionClientResponseTimestamps(now, now, now), result, request.command))
    }
    def fail(): Unit = {
      val request = read[WorkerExecutionProtocol.Request](sent.head)
      val now = LocalDateTime.now()
      receive(ExecutionClientResponse.forError(ExecutionClientResponseTimestamps(now, now, now),
        SerializedException("chart failed"), request.command))
    }
  }

  private class Workers {
    val created = ListBuffer.empty[FakeWorker]
    def create(): WebWorkerExecutionClient = {
      val worker = new FakeWorker
      created += worker
      worker.client
    }
  }

  test("the regular editor accepts both gutter settings and exposes the passed configuration") {
    for (enabled <- List(false, true)) {
      val settings = CodeMirrorEditorConfig(showFlowchartGutter = enabled)
      val editor = CodeMirrorEditor(Var("print(1)"), editorConfig = settings)
      assertEquals(editor.config.now(), settings)
      assertEquals(read[CodeMirrorEditorConfig](write(settings)), settings)
      assertEquals(readBinary[CodeMirrorEditorConfig](writeBinary(settings)), settings)
    }
  }

  test("default and compatibility editors permit disabling the gutter") {
    assertEquals(CodeMirrorEditor(Var("")).config.now().showFlowchartGutter, false)
    assertEquals(CodeMirrorEditorFlowchart(Var("")).config.now().showFlowchartGutter, true)
    assertEquals(CodeMirrorEditorFlowchart(Var(""), editorConfig = CodeMirrorEditorConfig.Default)
      .config.now().showFlowchartGutter, false)
    assertEquals(read[CodeMirrorEditorConfig]("{}"), CodeMirrorEditorConfig.Default)
  }

  test("Eva propagates its editor settings and old serialized configurations retain defaults") {
    val configured = EvaEditorConfig(codeMirrorConfig = CodeMirrorEditorConfig.Default)
    assertEquals(read[EvaEditorConfig](write(configured)), configured)
    assertEquals(read[EvaEditorConfig]("{}").codeMirrorConfig, CodeMirrorEditorConfig.Flowchart)
  }

  test("the flowchart cache retains only the latest exact source") {
    val cache = new CodeMirrorEditorFlowchart.Cache
    val first = "print(1)\r\n"
    cache.put(first, chart("first"))
    assertEquals(cache.get(first), Some(chart("first")))
    assertEquals(cache.get("print(1)\n"), None)
    assertEquals(cache.get("print(1)\r\n "), None)
    cache.put("print(2)", chart("second"))
    assertEquals(cache.get(first), None)
    assertEquals(cache.get("print(2)"), Some(chart("second")))
  }

  test("closed flowchart analysis rejects even a cached source") {
    val cache = new CodeMirrorEditorFlowchart.Cache
    val workers = new Workers
    cache.put("print(1)", chart("cached"))
    val analysis = new CodeMirrorEditorFlowchart.Analysis(cache, workers.create)
    analysis.close()
    analysis.chartForDoc("print(1)").toFuture.failed.map { error =>
      assert(error.getMessage.contains("closed"))
      assertEquals(workers.created.size, 0)
    }
  }

  test("concurrent analysis of the same source shares one worker request") {
    val cache = new CodeMirrorEditorFlowchart.Cache
    val workers = new Workers
    val analysis = new CodeMirrorEditorFlowchart.Analysis(cache, workers.create)
    val first = analysis.chartForDoc("print(1)").toFuture
    val second = analysis.chartForDoc("print(1)").toFuture
    assertEquals(workers.created.size, 1)
    assertEquals(workers.created.head.sent.size, 1)
    workers.created.head.reply(chart("shared"))
    first.zip(second).map { (a, b) =>
      assertEquals(js.JSON.stringify(a), js.JSON.stringify(b))
      assertEquals(cache.get("print(1)"), Some(chart("shared")))
    }.andThen { case _ => analysis.close() }
  }

  test("a different source cancels its previous worker and ignores late replies") {
    val cache = new CodeMirrorEditorFlowchart.Cache
    val workers = new Workers
    val analysis = new CodeMirrorEditorFlowchart.Analysis(cache, workers.create)
    val cancelled = analysis.chartForDoc("print(1)").toFuture.failed
    val next = analysis.chartForDoc("print(2)").toFuture
    assert(workers.created.head.terminated)
    assertEquals(workers.created.size, 2)
    workers.created.head.reply(chart("old"))
    workers.created.last.reply(chart("new"))
    cancelled.zip(next).map { (error, _) =>
      assert(error.getMessage.contains("closed"))
      assertEquals(cache.get("print(1)"), None)
      assertEquals(cache.get("print(2)"), Some(chart("new")))
    }.andThen { case _ => analysis.close() }
  }

  test("failed analysis retries with a new worker") {
    val cache = new CodeMirrorEditorFlowchart.Cache
    val workers = new Workers
    val analysis = new CodeMirrorEditorFlowchart.Analysis(cache, workers.create)
    val failed = analysis.chartForDoc("print(1)").toFuture.failed
    workers.created.head.fail()
    failed.flatMap { error =>
      assert(error.getMessage.contains("chart failed"))
      assert(workers.created.head.terminated)
      assertEquals(cache.get("print(1)"), None)
      val retry = analysis.chartForDoc("print(1)").toFuture
      assertEquals(workers.created.size, 2)
      workers.created.last.reply(chart("retried"))
      retry
    }.map { _ => assertEquals(cache.get("print(1)"), Some(chart("retried"))) }
      .andThen { case _ => analysis.close() }
  }

  test("a completed but superseded request cannot replace the latest cache") {
    val cache = new CodeMirrorEditorFlowchart.Cache
    val workers = new Workers
    val analysis = new CodeMirrorEditorFlowchart.Analysis(cache, workers.create)
    val first = analysis.chartForDoc("print(1)").toFuture
    workers.created.head.reply(chart("superseded"))
    val next = analysis.chartForDoc("print(2)").toFuture
    assert(workers.created.head.terminated)
    workers.created.last.reply(chart("current"))
    first.zip(next).map { (_, _) =>
      assertEquals(cache.get("print(1)"), None)
      assertEquals(cache.get("print(2)"), Some(chart("current")))
    }.andThen { case _ => analysis.close() }
  }
}
