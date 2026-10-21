package it.evadid.facades.internal

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter.{Chart, Edge, Node}
import it.evadid.facades.CodeMirror.Api.*
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import upickle.default.*

/** Native gutter integration; semantic graph calculation stays in the worker. */
private[evadid] object CodeMirrorFlowchartRendering {
  private case class Route(edge: Edge, low: Int, high: Int, sourceX: Int, targetX: Int,
                           terminal: Boolean, back: Boolean, lane: Option[Int]) derives ReadWriter
  private case class Layout(nodes: Map[Int, Node], routes: List[Route], entries: Set[Int], width: Int) derives ReadWriter
  private def nodeX(node: Node): Int = 18 + node.depth * 18
  private def layout(chart: Chart): Layout = {
    val nodes = chart.nodes.map(n => n.line -> n).toMap
    val maxX = (18 :: nodes.values.map(nodeX).toList).max
    val edges = chart.edges.filter(e => nodes.contains(e.from) && nodes.contains(e.to))
      .sortBy(e => (math.min(e.from, e.to), -math.max(e.from, e.to), e.from))
    val (lanes, routes) = edges.foldLeft((Vector.empty[Int], List.empty[Route])) { case ((lanes, routes), edge) =>
      val low = math.min(edge.from, edge.to)
      val high = math.max(edge.from, edge.to)
      val terminal = edge.kind.endsWith("-end")
      val back = !terminal && edge.to <= edge.from
      val direct = !back && edge.to == edge.from + 1 && edge.kind != "no"
      val laneIndex = lanes.indexWhere(_ < low) match { case -1 => lanes.size; case index => index }
      val next = if direct then lanes else if laneIndex == lanes.size then lanes :+ high else lanes.updated(laneIndex, high)
      val route = Route(edge, low, high, nodeX(nodes(edge.from)), nodeX(nodes(edge.to)), terminal, back,
        Option.unless(direct)(maxX + 20 + laneIndex * 10))
      (next, routes :+ route)
    }
    Layout(nodes, routes, chart.entries.toSet, maxX + 34 + lanes.size * 10)
  }
  private def element(tag: String, attributes: (String, String)*): dom.Element = {
    val node = dom.document.createElementNS("http://www.w3.org/2000/svg", tag)
    attributes.foreach { case (key, value) => node.setAttribute(key, value) }
    node
  }
  private def row(layout: Layout, line: Int): dom.Element = {
    val svg = element("svg", "viewBox" -> s"0 0 ${layout.width} 20", "preserveAspectRatio" -> "none", "aria-hidden" -> "true")
    def path(d: String, color: String, dashed: Boolean = false): Unit = {
      val node = element("path", "d" -> d, "fill" -> "none", "stroke" -> color, "stroke-width" -> "1.2")
      if dashed then node.setAttribute("stroke-dasharray", "2 1.5")
      svg.appendChild(node)
    }
    layout.routes.filter(e => line >= e.low && line <= e.high).foreach { route =>
      val edge = route.edge
      val branch = edge.kind.stripSuffix("-end")
      val color = branch match { case "yes" => "#176a39"; case "no" => "#a12e23"; case _ => "#382b18" }
      val source = route.sourceX; val target = route.targetX
      if route.terminal then {
        val lane = route.lane.get
        path(s"M ${source + 6} 10 H $lane V 16", color)
        svg.appendChild(element("circle", "cx" -> lane.toString, "cy" -> "18", "r" -> "1.4", "fill" -> color))
      } else route.lane match {
        case None =>
          if line == edge.from then path(s"M $source 15 V 18 L $target 20", color)
          if line == edge.to then path(s"M $target 0 V 5", color)
        case Some(lane) =>
          if edge.from == edge.to then path(s"M ${source + 5} 10 H $lane V 2 H $target V 5", color, true)
          else if line == edge.from then path(s"M $source 15 V 18 H $lane V ${if route.back then 0 else 20}", color, route.back)
          else if line == edge.to then path(s"M $lane ${if route.back then 20 else 0} V 2 H $target V 5", color, route.back)
          else path(s"M $lane 0 V 20", color, route.back)
      }
      if line == edge.to && !route.terminal then path(s"M ${target - 2} 2 L $target 5 L ${target + 2} 2", color)
      if line == edge.from && Set("yes", "no")(branch) then {
        val text = element("text", "x" -> (source + (if branch == "yes" then -10 else 7)).toString,
          "y" -> "19", "fill" -> color, "font-size" -> "6", "font-family" -> "sans-serif")
        text.textContent = if branch == "yes" then "T" else "F"
        svg.appendChild(text)
      }
    }
    layout.nodes.get(line).foreach { node =>
      val x = nodeX(node)
      val shape = if Set("decision", "loop")(node.kind) then
        element("path", "d" -> s"M $x 5 L ${x + 6} 10 L $x 15 L ${x - 6} 10 Z")
      else element("rect", "x" -> (x - 5).toString, "y" -> "6", "width" -> "10", "height" -> "8", "rx" -> (if node.kind == "return" then "4" else "1"))
      shape.setAttribute("fill", "#dbb573"); shape.setAttribute("stroke", "#382b18"); shape.setAttribute("stroke-width", "1.1")
      svg.appendChild(shape)
      if layout.entries(line) then svg.appendChild(element("path", "d" -> s"M ${x - 2} 0 H ${x + 2} L $x 4 Z", "fill" -> "#382b18"))
      if !layout.routes.exists(_.edge.from == line) then {
        path(s"M $x 15 V 17", "#382b18")
        svg.appendChild(element("circle", "cx" -> x.toString, "cy" -> "18", "r" -> "1.4", "fill" -> "#382b18"))
      }
    }
    svg
  }
  private class Marker(val layout: Layout, val line: Int) extends GutterMarker {
    override def eq(other: GutterMarker): Boolean = other match {
      case marker: Marker => (layout eq marker.layout) && line == marker.line
      case _ => false
    }
    override def toDOM(view: View): dom.Element = {
      val container = dom.document.createElement("div").asInstanceOf[dom.html.Div]
      container.className = "cm-flowchart-row"
      container.style.width = s"${layout.width}px"
      layout.nodes.get(line).foreach(node => container.title = s"${node.kind}: ${node.label}")
      container.appendChild(row(layout, line))
      container
    }
  }
  def extension(chartForDoc: js.Function1[String, js.Promise[js.Object]]): Extension = {
    val ready = Libraries.StateEffect.define[Layout]()
    val empty = layout(Chart(Nil, Nil, Nil))
    val field = Libraries.StateField.define[Layout](js.Dynamic.literal(
      create = (() => empty): js.Function0[Layout],
      update = ((current: Layout, transaction: Transaction) =>
        if transaction.docChanged then empty
        else transaction.effects.find(_.is(ready)).map(_.value.asInstanceOf[Layout]).getOrElse(current)
      ): js.Function2[Layout, Transaction, Layout]))
    val analysis = Libraries.ViewPlugin.define((view: View) => {
      val timer = Var(Option.empty[SetTimeoutHandle])
      val generation = Var(0)
      val destroyed = Var(false)
      def schedule(delay: Int): Unit = {
        timer.now().foreach(clearTimeout)
        generation.update(_ + 1)
        val current = generation.now()
        timer.set(Some(setTimeout(delay) {
          val source = view.state.doc.toString()
          js.Promise.resolve[js.Object](chartForDoc(source).asInstanceOf[js.Object]).toFuture.foreach { chart =>
            if !destroyed.now() && generation.now() == current && view.state.doc.toString() == source then
              view.dispatch(js.Dynamic.literal(effects = ready.of(layout(read[Chart](js.JSON.stringify(chart))))))
          }
        }))
      }
      schedule(0)
      new js.Object {
        def update(event: ViewUpdate): Unit = if event.docChanged then schedule(150)
        def destroy(): Unit = {
          destroyed.set(true); generation.update(_ + 1); timer.now().foreach(clearTimeout)
        }
      }
    }, js.Dynamic.literal())
    js.Array[Extension](field, analysis, Libraries.gutter(js.Dynamic.literal(
      `class` = "cm-flowchart-gutter", renderEmptyElements = true,
      lineMarker = ((view: View, line: js.Dynamic) => {
        val current = view.state.field(field)
        if current.nodes.isEmpty then null else new Marker(current, view.state.doc.lineAt(line.from.asInstanceOf[Int]).number)
      }): js.Function2[View, js.Dynamic, GutterMarker],
      initialSpacer = ((view: View) => new Marker(view.state.field(field), 0)): js.Function1[View, GutterMarker],
      updateSpacer = ((_: GutterMarker, event: ViewUpdate) => new Marker(event.state.field(field), 0)): js.Function2[GutterMarker, ViewUpdate, GutterMarker],
      lineMarkerChange = ((event: ViewUpdate) => event.docChanged || event.transactions.exists(_.effects.exists(_.is(ready)))): js.Function1[ViewUpdate, Boolean]
    )))
  }
}
