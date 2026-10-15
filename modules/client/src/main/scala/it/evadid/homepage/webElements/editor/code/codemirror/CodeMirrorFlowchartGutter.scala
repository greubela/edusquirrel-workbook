package it.evadid.homepage.webElements.editor.code.codemirror

import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter.Chart
import it.evadid.distribution.clients.{ExecutionClient, WebWorkerExecutionClient}
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

import upickle.default.*

case class CodeMirrorFlowchartGutter(logger: Logger, worker: ExecutionClient) {

  private val gutterCache : AsyncDataCache[String, Chart]= new AsyncDataCache[String, Chart](logger) {
    override protected def executeLoading(source: String)(ec: ExecutionContext): Future[Chart] = {
      WorkerCommands.pythonFlowchart.sendCommandTo(worker, WorkerCommands.PythonFlowchartRequest(source)).map(_.result)
    }

    override protected def formatInputForLogging(in: String): String = write(in)

    override protected def formatOutputForLogging(out: Chart): String = out.toString
  }

  def loadAsJsPromise(input: String): js.Promise[js.Object] = {
    gutterCache.loadAsFuture(input).map(toJs).toJSPromise
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

