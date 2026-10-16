package it.evadid.homepage.webElements.editor.code.codemirror

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object CodeMirrorFlowchartGutter {
  private[code] final class Cache {
    private val latest = Var(Option.empty[(String, PythonFlowchartGutter.Chart)])
    def get(source: String): Option[PythonFlowchartGutter.Chart] = latest.now().filter(_._1 == source).map(_._2)
    def put(source: String, chart: PythonFlowchartGutter.Chart): Unit = latest.set(Some(source -> chart))
  }

  private[code] final class Analysis(cache: Cache, createWorker: () => WebWorkerExecutionClient = () =>
      WebWorkerExecutionClient(CodeMirrorEditorLoading.workerUrl, timeoutMilliseconds = 30000)) {
    private val worker = Var(Option.empty[WebWorkerExecutionClient])
    private val pending = Var(Option.empty[(String, Future[PythonFlowchartGutter.Chart])])
    private val generation = Var(0)
    private val closed = Var(false)

    private def discardWorker(): Unit = {
      generation.update(_ + 1)
      worker.now().foreach(_.close())
      worker.set(None)
      pending.set(None)
    }

    def close(): Unit = {
      closed.set(true)
      discardWorker()
    }

    def chartForDoc(source: String): js.Promise[js.Object] = {
      val result = if closed.now() then Future.failed(IllegalStateException("Flowchart editor is closed"))
      else cache.get(source) match {
        case Some(chart) =>
          if pending.now().nonEmpty then discardWorker()
          Future.successful(chart)
        case None => pending.now().filter(_._1 == source).map(_._2).getOrElse {
          if pending.now().nonEmpty then discardWorker()
          val current = generation.now()
          val requested = Try {
            val executor = worker.now().getOrElse {
              val created = createWorker()
              worker.set(Some(created))
              created
            }
            WorkerCommands.pythonFlowchart.sendCommandTo(executor, WorkerCommands.PythonFlowchartRequest(source)).map(_.result)
          }.fold(Future.failed, identity)
          pending.set(Some(source -> requested))
          requested.onComplete { outcome =>
            if !closed.now() && current == generation.now() then outcome match {
              case Success(chart) =>
                pending.set(None)
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
