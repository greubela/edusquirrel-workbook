package it.evadid.homepage.webElements.editor.code.codemirror

import it.evadid.homepage.control.startup.ClientRuntimeConfig

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.facades.Workers
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
      WebWorkerExecutionClient.fromWorker(
        Workers.classic(ClientRuntimeConfig.workerUrl).createLoadedInstance(),
        timeoutMilliseconds = 30000)) {
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

    def chartForDoc(source: String): Future[PythonFlowchartGutter.Chart] = {
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
          val completed = requested.transform { outcome =>
            if !closed.now() && current == generation.now() then outcome match {
              case Success(chart) =>
                pending.set(None)
                cache.put(source, chart)
              case Failure(_) => discardWorker()
            }
            outcome
          }
          pending.set(Some(source -> completed))
          completed
        }
      }
      result
    }
  }

  /** Reuse the nested block renderer's overlay, with no expression/content shapes. */
  def render(chart: PythonFlowchartGutter.Chart): it.evadid.facades.CodeMirror.Flowchart = {
    import it.evadid.facades.CodeMirror.{Flowchart, FlowchartRow, FlowchartSlice}
    import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
    import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
    import NestedBlockRenderer.*
    import todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth.*
    import todomove.webElementsOld.webElements.svg.shapes.controlflow.doubleWidth.*
    import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
    import org.scalajs.dom
    import scala.scalajs.js.URIUtils.encodeURIComponent

    val config = BeRenderingConfig.default().copy(pControlSegmentSize = 2)
    val height = 16.0
    val slices = chart.flow.zipWithIndex.flatMap { (plan, group) =>
      val expanded = plan.zipWithIndex.flatMap { (line, index) =>
        val next = plan.lift(index + 1).map(_.line).getOrElse(line.line)
        line :: ((line.line + 1) until next).map(PythonFlowchartGutter.FlowLine(_, "gap")).toList
      }
      val lines: List[NestedBlockLine] = expanded.map { line => line.kind match {
        case "start" => ControlFlowLine(ControlFlowProgramStarter())
        case "stop" => ControlFlowLine(ControlFlowProgramStopper())
        case "if" => ControlFlowIncreaseLine(IfElseSplit(), None, ControlFlowDirected(true, false))
        case "else" => ControlFlowReplaceLine(ControlFlowCross(), None, ControlFlowDirected(true, false))
        case "endif" => ControlFlowDecreaseLine(IfElseUnion(), None)
        case "loop" => ControlFlowIncreaseLine(RepetitionSplit(), None, ControlFlowDownUp(false))
        case "endloop" => ControlFlowDecreaseLine(RepetitionUnion(), None)
        case _ => ControlFlowLine(ControlFlowDirected(true, false))
      }}
      val renderer = NestedBlockRenderer(List(NestedBlockSegment(lines)))
      val shape = renderer.controlFlowOnly(config, List.fill(lines.size)(height))
      val dimensions = shape.displaySize(config)
      val width = dimensions.width + 2
      val totalHeight = config.controlSegmentSize + height * lines.size + 2
      val svg = dom.document.createElementNS("http://www.w3.org/2000/svg", "svg")
      svg.setAttribute("xmlns", "http://www.w3.org/2000/svg")
      svg.setAttribute("width", width.toString)
      svg.setAttribute("height", totalHeight.toString)
      svg.setAttribute("viewBox", s"0 0 $width $totalHeight")
      svg.appendChild(shape.render(config, Bounds(Point(0.0, 0.0), Dimension(width, totalHeight))).renderWithMods.ref)
      val image = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg.outerHTML)
      val depth = chart.nodes.filter(node => plan.exists(_.line == node.line)).map(_.depth).minOption.getOrElse(0)
      val x = if group == 0 then 0.0 else math.max(12.0, depth * 12.0)
      expanded.zipWithIndex.groupBy(_._1.line).toList.map { (line, rows) =>
        val first = rows.map(_._2).min
        val last = rows.map(_._2).max
        val top = if first == 0 then 0.0 else config.controlSegmentSize + first * height
        val bottom = config.controlSegmentSize + (last + 1) * height + (if last == expanded.size - 1 then 2 else 0)
        line -> FlowchartSlice(image, width, totalHeight, top, bottom - top, x)
      }
    }
    val rows = slices.groupMap(_._1)(_._2).map { (line, layers) =>
      val title = chart.nodes.find(_.line == line).map(node => s"${node.kind}: ${node.label}").getOrElse("")
      line -> FlowchartRow(layers, title)
    }
    Flowchart(slices.map((_, slice) => slice.x + slice.width).maxOption.getOrElse(1.0), rows)
  }

}
