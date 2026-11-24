package it.evadid.homepage.webElements.editor.code.codemirror

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python}
import it.evadid.core.datastructures.vectorShapes.renderer.FlowchartGutter
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.homepage.webElements.editor.code.worker.{SharedWorker, SharedWorkerCache}

import scala.concurrent.Future

/** A language's flowchart gutter: charts of the editor's text, computed asynchronously. A language without a
  * source shows no gutter rather than an approximation.
  */
trait CodeMirrorFlowchartSource {
  def chart(source: String): Future[FlowchartGutter.Chart]
}

object CodeMirrorFlowchartSource {
  /** Sources of `language` charted by the page's shared analysis worker. */
  private def charted(language: ProgrammingLanguage): CodeMirrorFlowchartSource = new CodeMirrorFlowchartSource {
    private val charts = SharedWorkerCache(WorkerCommands.flowchart, WorkerCommands.FlowchartRequest(language, _: String),
      SharedWorker.analysis, capacity = 8)
    override def chart(source: String): Future[FlowchartGutter.Chart] = charts(source)
  }

  lazy val python: CodeMirrorFlowchartSource = charted(Python)
  /** Java programs, classes or single methods (the Function Mode editor). */
  lazy val java: CodeMirrorFlowchartSource = charted(Java)

  def forLanguage(language: ProgrammingLanguage): Option[CodeMirrorFlowchartSource] = language match {
    case Python => Some(python)
    case Java => Some(java)
    case _ => None
  }
}
