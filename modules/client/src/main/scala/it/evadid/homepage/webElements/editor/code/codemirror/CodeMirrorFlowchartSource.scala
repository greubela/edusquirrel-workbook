package it.evadid.homepage.webElements.editor.code.codemirror

import it.evadid.core.datastructures.language.AppLanguage.{ProgrammingLanguage, Python}
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.homepage.webElements.editor.code.worker.{SharedWorker, SharedWorkerCache}

import scala.concurrent.Future

/** A language's flowchart gutter: charts of the editor's text, computed asynchronously. A language without a
  * source shows no gutter rather than an approximation.
  */
trait CodeMirrorFlowchartSource {
  def chart(source: String): Future[PythonFlowchartGutter.Chart]
}

object CodeMirrorFlowchartSource {
  /** Python sources are charted by the page's shared analysis worker. */
  val python: CodeMirrorFlowchartSource = new CodeMirrorFlowchartSource {
    private val charts = SharedWorkerCache(WorkerCommands.pythonFlowchart, WorkerCommands.PythonFlowchartRequest(_: String),
      SharedWorker.analysis, capacity = 8)
    override def chart(source: String): Future[PythonFlowchartGutter.Chart] = charts(source)
  }

  def forLanguage(language: ProgrammingLanguage): Option[CodeMirrorFlowchartSource] = language match {
    case Python => Some(python)
    case _ => None
  }
}
