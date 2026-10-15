package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.font.AppFont
import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/** The regular Python editor with a line-aligned control-flow gutter before
  * the line numbers. Unsupported/incomplete programs remain editable.
  */
case class CodeMirrorEditorFlowchart(
    content: Var[String],
    onUserInput: String => Unit = _ => (),
    editorFont: Signal[AppFont] = Val(AppFont("JetBrains Mono", 14)),
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Flowchart
) extends SimpleWebEditor[String, CodeMirrorEditorConfig] {
  private val editor = CodeMirrorEditor(content, onUserInput, editorFont, Python,
    editorConfig = editorConfig)

  def focus(): Unit = editor.focus()
  def currentDoc: Option[String] = editor.currentDoc
  def setDiagnostics(diagnostics: Seq[CodeMirrorEditor.Diagnostic]): Unit = editor.setDiagnostics(diagnostics)
  def clearDiagnostics(): Unit = editor.clearDiagnostics()
  override def getDomElement(): Element = editor.getDomElement()
  override def underlyingVar: Var[String] = content
  override def config: Val[CodeMirrorEditorConfig] = editor.config
}

object CodeMirrorEditorFlowchart {
  private[code] final class Cache {
    private var latest = Option.empty[(String, PythonFlowchartGutter.Chart)]
    def get(source: String): Option[PythonFlowchartGutter.Chart] = latest.filter(_._1 == source).map(_._2)
    def put(source: String, chart: PythonFlowchartGutter.Chart): Unit = latest = Some(source -> chart)
  }

  private[code] final class Analysis(cache: Cache, createWorker: () => WebWorkerExecutionClient = () =>
      WebWorkerExecutionClient(CodeMirrorEditorLoading.workerUrl, timeoutMilliseconds = 30000)) {
    private var worker = Option.empty[WebWorkerExecutionClient]
    private var pending = Option.empty[(String, Future[PythonFlowchartGutter.Chart])]
    private var generation = 0
    private var closed = false

    private def discardWorker(): Unit = {
      generation += 1
      worker.foreach(_.close())
      worker = None
      pending = None
    }

    def close(): Unit = {
      closed = true
      discardWorker()
    }

    def chartForDoc(source: String): js.Promise[js.Object] = {
      val result = if closed then Future.failed(IllegalStateException("Flowchart editor is closed"))
      else cache.get(source) match {
        case Some(chart) =>
          if pending.nonEmpty then discardWorker()
          Future.successful(chart)
        case None => pending.filter(_._1 == source).map(_._2).getOrElse {
          if pending.nonEmpty then discardWorker()
          val current = generation
          val requested = Try {
            val executor = worker.getOrElse {
              val created = createWorker()
              worker = Some(created)
              created
            }
            WorkerCommands.pythonFlowchart.sendCommandTo(executor, WorkerCommands.PythonFlowchartRequest(source)).map(_.result)
          }.fold(Future.failed, identity)
          pending = Some(source -> requested)
          requested.onComplete { outcome =>
            if !closed && current == generation then outcome match {
              case Success(chart) =>
                pending = None
                cache.put(source, chart)
              case Failure(_) => discardWorker()
            }
          }
          requested
        }
      }
      result.map(toJs).toJSPromise
    }
  }

  private def toJs(chart: PythonFlowchartGutter.Chart): js.Object = {
    js.Dynamic.literal(
      nodes = js.Array(chart.nodes.map(node => js.Dynamic.literal(
        line = node.line, kind = node.kind, depth = node.depth, label = node.label
      )) *),
      edges = js.Array(chart.edges.map(edge => js.Dynamic.literal(
        from = edge.from, to = edge.to, kind = edge.kind
      )) *),
      entries = js.Array(chart.entries *)
    )
  }
}
