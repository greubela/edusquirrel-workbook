package it.evadid.homepage.webElements.editor.code.codemirror

import it.evadid.core.datastructures.language.AppLanguage.{ProgrammingLanguage, Python}
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.homepage.webElements.editor.code.worker.{LatestResult, LatestWorkerRequest}

import scala.concurrent.Future

/** A language's flowchart gutter. Each mounted editor opens its own analysis and closes it on unmount; a
  * language without a source shows no gutter rather than an approximation.
  */
trait CodeMirrorFlowchartSource {
  def open(): CodeMirrorFlowchartSource.Analysis
}

object CodeMirrorFlowchartSource {
  /** Charts of the editor's current text, computed off the page thread. */
  trait Analysis {
    def chart(source: String): Future[PythonFlowchartGutter.Chart]
    def close(): Unit
  }

  /** Python sources are charted by the workbook worker. */
  val python: CodeMirrorFlowchartSource = () => new Analysis {
    private val request = LatestWorkerRequest(WorkerCommands.pythonFlowchart, WorkerCommands.PythonFlowchartRequest(_: String),
      cache = Some(LatestResult[String, PythonFlowchartGutter.Chart]()))
    override def chart(source: String): Future[PythonFlowchartGutter.Chart] = request(source)
    override def close(): Unit = request.close()
  }

  def forLanguage(language: ProgrammingLanguage): Option[CodeMirrorFlowchartSource] = language match {
    case Python => Some(python)
    case _ => None
  }
}
