package it.evadid.homepage.webElements.editor.code

import it.evadid.core.datastructures.language.AppLanguage.Python
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.vectorShapes.renderer.FlowchartGutter
import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.distribution.command.SerializedException
import it.evadid.distribution.formats.{ExecutionClientResponse, WorkerExecutionProtocol}
import it.evadid.distribution.formats.ExecutionClientResponse.ExecutionClientResponseTimestamps
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorConfig
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.worker.{SharedWorker, SharedWorkerCache}
import it.evadid.distribution.commandTypes.WorkerCommands
import munit.FunSuite
import org.scalajs.dom
import upickle.default.*

import java.time.LocalDateTime
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class CodeMirrorEditorConfigSpec extends FunSuite {
  private val logger = it.evadid.util.logging.Logger.withNameAndPrefixes(Some("test"), it.evadid.util.logging.derived.PrintToStdLogger.printNothing)
  private def chart(label: String) = FlowchartGutter.Chart(
    List(FlowchartGutter.Node(1, "process", 0, label)), Nil, List(1))

  private class FakeWorker {
    val sent = ListBuffer.empty[String]
    var terminated = false
    private val raw = js.Dynamic.literal(
      postMessage = ((message: String) => { sent += message; () }): js.Function1[String, Unit],
      terminate = (() => { terminated = true }): js.Function0[Unit])
    val client = WebWorkerExecutionClient.fromWorker(raw.asInstanceOf[dom.Worker], timeoutMilliseconds = 1000)
    private def receive(response: ExecutionClientResponse, index: Int): Unit = {
      val request = read[WorkerExecutionProtocol.Request](sent(index))
      raw.selectDynamic("onmessage").asInstanceOf[js.Function1[dom.MessageEvent, Unit]](
        js.Dynamic.literal(data = write(WorkerExecutionProtocol.Response(request.requestId, response))).asInstanceOf[dom.MessageEvent])
    }
    def reply(result: FlowchartGutter.Chart, index: Int = 0): Unit = {
      val request = read[WorkerExecutionProtocol.Request](sent(index))
      val now = LocalDateTime.now()
      receive(ExecutionClientResponse.forValue(ExecutionClientResponseTimestamps(now, now, now), result, request.command), index)
    }
    def fail(index: Int = 0): Unit = {
      val request = read[WorkerExecutionProtocol.Request](sent(index))
      val now = LocalDateTime.now()
      receive(ExecutionClientResponse.forError(ExecutionClientResponseTimestamps(now, now, now),
        SerializedException("chart failed"), request.command), index)
    }
  }

  private def charts(workers: Workers, capacity: Int = 32) =
    SharedWorkerCache(WorkerCommands.flowchart, WorkerCommands.FlowchartRequest(Python, _: String),
      SharedWorker(() => workers.create()), capacity)

  private class Workers {
    val created = ListBuffer.empty[FakeWorker]
    def create(): WebWorkerExecutionClient = {
      val worker = new FakeWorker
      created += worker
      worker.client
    }
  }

  test("only languages with a flowchart source get a gutter") {
    import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SQL}
    import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorFlowchartSource
    assertEquals(CodeMirrorFlowchartSource.forLanguage(Python), Some(CodeMirrorFlowchartSource.python))
    assertEquals(CodeMirrorFlowchartSource.forLanguage(Java), Some(CodeMirrorFlowchartSource.java))
    assertEquals(CodeMirrorFlowchartSource.forLanguage(SQL), None)
  }

  test("the regular editor accepts both gutter settings and exposes the passed configuration") {
    for (enabled <- List(false, true)) {
      val settings = CodeMirrorEditorConfig(showFlowchartGutter = enabled)
      val editor = CodeMirrorEditor(logger, Var("print(1)"), language = it.evadid.core.datastructures.language.AppLanguage.Python, config = Var(settings),
        loadingLabel = Val("loading"))
      assertEquals(editor.config.now(), settings)
      assertEquals(read[CodeMirrorEditorConfig](write(settings)), settings)
      assertEquals(readBinary[CodeMirrorEditorConfig](writeBinary(settings)), settings)
    }
  }

  test("default and explicitly configured editors retain their gutter setting") {
    assertEquals(read[CodeMirrorEditorConfig]("{}"), CodeMirrorEditorConfig.Default)
    assertEquals(CodeMirrorEditorConfig.Flowchart.showFlowchartGutter, true)
  }

  test("Eva propagates its editor settings and omitted fields use current defaults") {
    val configured = EvaEditorConfig(codeMirrorConfig = CodeMirrorEditorConfig.Flowchart)
    assertEquals(read[EvaEditorConfig](write(configured)), configured)
    assertEquals(read[EvaEditorConfig]("{}").codeMirrorConfig, CodeMirrorEditorConfig.Default)
  }

  test("identical requests share one worker request and are answered from the cache afterwards") {
    val workers = new Workers
    val cache = charts(workers)
    val first = cache("print(1)")
    val second = cache("print(1)")
    assertEquals(workers.created.size, 1)
    assertEquals(workers.created.head.sent.size, 1)
    workers.created.head.reply(chart("shared"))
    first.zip(second).map { (a, b) =>
      assertEquals(a, b)
      assertEquals(cache("print(1)").value.flatMap(_.toOption), Some(chart("shared")))
      assertEquals(workers.created.head.sent.size, 1, "a remembered result needs no request")
    }
  }

  test("all requests of the page share one worker and all complete") {
    val workers = new Workers
    val cache = charts(workers)
    val first = cache("print(1)")
    val second = cache("print(2)")
    assertEquals(workers.created.size, 1)
    assertEquals(workers.created.head.sent.size, 2)
    assert(!workers.created.head.terminated, "a newer request never cancels another consumer's request")
    workers.created.head.reply(chart("second"), 1)
    workers.created.head.reply(chart("first"), 0)
    first.zip(second).map((a, b) => assertEquals((a, b), (chart("first"), chart("second"))))
  }

  test("a failed request is forgotten and retried on the same worker") {
    val workers = new Workers
    val cache = charts(workers)
    val failed = cache("print(1)").failed
    workers.created.head.fail()
    failed.flatMap { error =>
      assert(error.getMessage.contains("chart failed"))
      val retry = cache("print(1)")
      assertEquals(workers.created.size, 1)
      assertEquals(workers.created.head.sent.size, 2)
      workers.created.head.reply(chart("retried"), 1)
      retry
    }.map(result => assertEquals(result, chart("retried")))
  }

  test("a stopped worker is replaced by the next request") {
    val workers = new Workers
    val worker = SharedWorker(() => workers.create())
    val stopped = worker.send(WorkerCommands.flowchart, WorkerCommands.FlowchartRequest(Python, "print(1)")).failed
    workers.created.head.client.close()
    stopped.flatMap { _ =>
      val next = worker.send(WorkerCommands.flowchart, WorkerCommands.FlowchartRequest(Python, "print(2)"))
      assertEquals(workers.created.size, 2)
      workers.created.last.reply(chart("fresh"))
      next
    }.map(result => assertEquals(result, chart("fresh")))
  }

  test("only the latest keys up to the capacity are remembered") {
    val workers = new Workers
    val cache = charts(workers, capacity = 1)
    val first = cache("print(1)")
    workers.created.head.reply(chart("first"))
    first.flatMap { _ =>
      cache("print(2)")
      cache("print(1)")
      assertEquals(workers.created.head.sent.size, 3, "the evicted key is requested again")
      Future.unit
    }
  }
}
