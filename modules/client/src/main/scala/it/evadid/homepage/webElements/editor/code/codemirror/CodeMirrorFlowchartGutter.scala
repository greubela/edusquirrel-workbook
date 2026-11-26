package it.evadid.homepage.webElements.editor.code.codemirror


import it.evadid.core.datastructures.vectorShapes.renderer.FlowchartGutter

object CodeMirrorFlowchartGutter {
  private val gutterLogger = it.evadid.util.logging.Logger.withNameAndPrefixes(Some("CodeMirrorFlowchartGutter"))
  /** The block editor's control-flow overlay, with no expression/content shapes. */
  def render(chart: FlowchartGutter.Chart): it.evadid.facades.CodeMirror.Flowchart = {
    import it.evadid.facades.CodeMirror.{Flowchart, FlowchartRow, FlowchartSlice}
    import it.evadid.core.datastructures.vectorShapes.blocks.{BlockElements, BlockRenderingConfig, FlowLayout, FlowLine, FlowShape}
    import it.evadid.core.datastructures.vectorShapes.renderer.SvgLaminarRenderer
    import org.scalajs.dom
    import scala.scalajs.js.URIUtils.encodeURIComponent

    val config = BlockRenderingConfig(segmentSize = 2)
    val height = 16.0
    def depthOf(plan: List[FlowchartGutter.FlowLine]) =
      chart.nodes.filter(node => plan.exists(_.line == node.line)).map(_.depth).minOption.getOrElse(0)
    // Graphs are offset by their depth below the shallowest one, so a nested function's graph runs beside its outer one.
    val shallowest = chart.flow.map(depthOf).minOption.getOrElse(0)
    val slices = chart.flow.flatMap { plan =>
      val expanded = plan.zipWithIndex.flatMap { (line, index) =>
        val next = plan.lift(index + 1).map(_.line).getOrElse(line.line)
        line :: ((line.line + 1) until next).map(FlowchartGutter.FlowLine(_, "gap")).toList
      }
      val lines: List[FlowLine] = expanded.map { line => line.kind match {
        case "start" => FlowLine.Plain(FlowShape.ProgramStarter)
        case "stop" => FlowLine.Plain(FlowShape.ProgramStopper)
        case "if" => FlowLine.Increase(FlowShape.IfElseSplit, FlowShape.Directed(true, false))
        case "else" => FlowLine.Replace(FlowShape.IfElseCross, FlowShape.Directed(true, false))
        case "endif" => FlowLine.Decrease(FlowShape.IfElseUnion)
        case "loop" => FlowLine.Increase(FlowShape.RepetitionSplit, FlowShape.DownUp(false))
        case "endloop" => FlowLine.Decrease(FlowShape.RepetitionUnion)
        case _ => FlowLine.Plain(FlowShape.Directed(true, false))
      }}
      val overlay = FlowLayout(lines).overlay(config, List.fill(lines.size)(height))
      val width = overlay.displaySize(config.segmentSize).width + 2
      val totalHeight = config.segmentSize + height * lines.size + 2
      val svg = dom.document.createElementNS("http://www.w3.org/2000/svg", "svg")
      svg.setAttribute("xmlns", "http://www.w3.org/2000/svg")
      svg.setAttribute("width", width.toString)
      svg.setAttribute("height", totalHeight.toString)
      svg.setAttribute("viewBox", s"0 0 $width $totalHeight")
      svg.appendChild(SvgLaminarRenderer.renderElementAsSvg(gutterLogger, BlockElements.layout(overlay.toShape(config))).ref)
      val image = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg.outerHTML)
      val x = (depthOf(plan) - shallowest) * 12.0
      expanded.zipWithIndex.groupBy(_._1.line).toList.map { (line, rows) =>
        val first = rows.map(_._2).min
        val last = rows.map(_._2).max
        val top = if first == 0 then 0.0 else config.segmentSize + first * height
        val bottom = config.segmentSize + (last + 1) * height + (if last == expanded.size - 1 then 2 else 0)
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
