package it.evadid.core.datastructures.vectorShapes.renderer

import it.evadid.core.datastructures.color.{AppColor, RGBColor}
import it.evadid.core.datastructures.font.AppFont
import it.evadid.core.datastructures.geometry.{AspectRatio, Bounds, Dimension, Point}
import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.core.datastructures.vectorShapes.abstractions.{AppShapeCompositeControl, AppShapeElement, DrawingRoutine}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.*
import it.evadid.core.datastructures.vectorShapes.atomar.{AppShapeDrawingRoutineElement, AppShapeTextElement}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.helper.{AlignmentInParent, RenderingDimension}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.util.logging.Logger
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.*
import it.evadid.vm.code.errors.BeSingleLineComment
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.io.stringPrinter.python.BeExpressionToPythonString
import it.evadid.vm.naming.NamingStyle.SnakeCase

import scala.collection.mutable
import scala.util.control.NonFatal

/** Builds a flowchart without executing the expression or using the legacy block renderer.
  * Function/class declarations and calls are process nodes; their bodies are not inlined.
  * Pass a function body itself to build a separate function flowchart.
  */
object BeExpressionToFlowchart {
  enum NodeKind derives upickle.default.ReadWriter {
    case Terminal, Process, Decision
  }
  case class Node(id: Int, label: String, kind: NodeKind, expression: Option[BeExpression] = None) derives upickle.default.ReadWriter
  case class Edge(from: Int, to: Int, label: Option[String] = None, backEdge: Boolean = false) derives upickle.default.ReadWriter
  case class Flowchart(nodes: List[Node], edges: List[Edge], startId: Int, endId: Int) derives upickle.default.ReadWriter {
    def toShape(config: Config = Config()): AppShapeElement[Double] = render(this, config)
  }

  case class Config(
      font: AppFont = AppFont.defaultFont,
      minimumNodeSize: Dimension[Double] = Dimension(160.0, 64.0),
      gap: Dimension[Double] = Dimension(80.0, 80.0)
  ) derives upickle.default.ReadWriter {
    require(minimumNodeSize.width.isFinite && minimumNodeSize.height.isFinite &&
      minimumNodeSize.width > 0 && minimumNodeSize.height > 0, "Node dimensions must be finite and positive")
    require(gap.width.isFinite && gap.height.isFinite && gap.width >= 40 && gap.height >= 40,
      "Flowchart gaps must be finite and at least 40")
  }

  /** Ready for SvgLaminarRenderer.render(logger, BeExpressionToFlowchart(expression)). */
  def apply(expression: BeExpression, config: Config = Config()): AppShapeElement[Double] =
    build(expression).toShape(config)

  def defaultLabel(expression: BeExpression): String = {
    try {
      val source = BeExpressionToPythonString(English, skipUnparsable = false).forExpression(expression)
      val printed = expression match {
        case _: BeDefineFunction | _: BeDefineClass => source.linesIterator.nextOption().getOrElse(source)
        case _ => source
      }
      printed.linesIterator.map(_.trim).filter(_.nonEmpty).mkString(" ").trim match {
        case "" => expression.getClass.getSimpleName
        case label => label
      }
    } catch {
      case NonFatal(_) => expression.getClass.getSimpleName
    }
  }

  /** The graph is available separately for inspection, custom labels and other renderers. */
  def build(expression: BeExpression, label: BeExpression => String = defaultLabel): Flowchart = {
    val nodes = mutable.ListBuffer.empty[Node]
    val edges = mutable.ListBuffer.empty[Edge]
    val activeLoops = mutable.Set.empty[Int]
    def node(text: String, kind: NodeKind, expression: Option[BeExpression] = None): Int = {
      val id = nodes.size
      nodes += Node(id, text, kind, expression)
      id
    }
    def edge(from: Int, to: Int, text: Option[String] = None): Unit =
      edges += Edge(from, to, text, activeLoops.contains(to))

    val end = node("End", NodeKind.Terminal)
    def process(text: String, next: Int, expression: Option[BeExpression] = None): Int = {
      val id = node(text, NodeKind.Process, expression)
      edge(id, next)
      id
    }
    def loop(test: Int, body: BeSequence, next: Int, update: Option[String]): Unit = {
      activeLoops += test
      val bodyEnd = update.map(process(_, test)).getOrElse(test)
      val entry = append(body, bodyEnd)
      edge(test, entry, Some("Yes"))
      edge(test, next, Some("No"))
      activeLoops -= test
    }
    def append(current: BeExpression, next: Int): Int = current match {
      case BeStartProgram(body) => body.map(append(_, next)).getOrElse(next)
      case sequence: BeSequence => sequence.body.foldRight(next)((child, continuation) => append(child, continuation))
      case conditional: BeIfElse =>
        val id = node(label(conditional.condition), NodeKind.Decision, Some(conditional))
        val yes = append(conditional.thenBody, next)
        val no = append(conditional.elseBody, next)
        edge(id, yes, Some("Yes"))
        edge(id, no, Some("No"))
        id
      case repetition: BeWhile =>
        val test = node(label(repetition.condition), NodeKind.Decision, Some(repetition))
        loop(test, repetition.body, next, None)
        test
      case repetition: BeRepeatNr =>
        val counter = "remaining_" + nodes.size
        val test = node(counter + " > 0", NodeKind.Decision, Some(repetition))
        loop(test, repetition.body, next, Some(counter + " -= 1"))
        process(counter + " = " + repetition.amount, test)
      case repetition: BeFor =>
        val name = repetition.variable.name.getNameIn(English, SnakeCase)
        val test = node(name + " <= " + label(repetition.end), NodeKind.Decision, Some(repetition))
        loop(test, repetition.body, next, Some(name + " += 1"))
        process(name + " = " + label(repetition.start), test)
      case returned: BeReturn => process(label(returned), end, Some(returned))
      case _: BeSingleLineComment => next
      case other => process(label(other), next, Some(other))
    }

    val entry = append(expression, end)
    val start = node("Start", NodeKind.Terminal)
    edge(start, entry)
    // A return must not introduce a fall-through path or unreachable statements.
    val outgoing = edges.toList.groupBy(_.from)
    val reachable = mutable.Set(start)
    val pending = mutable.Queue(start)
    while (pending.nonEmpty) {
      val from = pending.dequeue()
      outgoing.getOrElse(from, Nil).foreach { connection =>
        if (reachable.add(connection.to)) pending.enqueue(connection.to)
      }
    }
    Flowchart(nodes.filter(n => reachable(n.id)).toList, edges.filter(e => reachable(e.from)).toList, start, end)
  }

  private def style(config: Config, fill: Boolean): AppShapeElementConfig[Double] = new AppShapeElementConfig[Double] {
    def useCustomPadding: Option[Dimension[Double]] = Some(Dimension(0.0, 0.0))
    def font: AppFont = config.font
    def colorStroke: AppColor = RGBColor.black
    def colorFill: AppColor = RGBColor.white
    def colorFont: AppColor = RGBColor.black
    override def fillEnabled: Boolean = fill
    override def strokeWidth: Double = if (fill) 1.5 else 0.0
    def onMouseClicked(leftButton: Boolean): Unit = ()
  }

  private def render(chart: Flowchart, config: Config): AppShapeElement[Double] = {
    val forward = chart.edges.filterNot(_.backEdge)
    val outgoing = forward.groupBy(_.from)
    val counts = forward.groupMapReduce(_.to)(_ => 1)(_ + _)
    val incoming = mutable.Map.from(chart.nodes.map(n => n.id -> counts.getOrElse(n.id, 0)))
    val ranks = mutable.Map.from(chart.nodes.map(n => n.id -> 0))
    val queue = mutable.Queue.from(chart.nodes.filter(n => incoming(n.id) == 0).map(_.id))
    var visited = 0
    while (queue.nonEmpty) {
      val id = queue.dequeue()
      visited += 1
      outgoing.getOrElse(id, Nil).foreach { edge =>
        ranks(edge.to) = math.max(ranks(edge.to), ranks(id) + 1)
        incoming(edge.to) -= 1
        if (incoming(edge.to) == 0) queue.enqueue(edge.to)
      }
    }
    require(visited == chart.nodes.size, "Flowchart contains a cycle without a marked back edge")
    val sizes = chart.nodes.map(n => config.font.measureText(n.label))
    val width = math.max(config.minimumNodeSize.width, sizes.map(_.width * 2 + 32).maxOption.getOrElse(0.0))
    val height = math.max(config.minimumNodeSize.height, sizes.map(_.height * 2 + 32).maxOption.getOrElse(0.0))
    val rows = chart.nodes.groupBy(n => ranks(n.id))
    val columns = rows.values.map(_.size).maxOption.getOrElse(1)
    val margin = 24.0
    val contentWidth = columns * width + (columns - 1) * config.gap.width
    val bounds = rows.toList.flatMap { (row, nodes) =>
      val rowWidth = nodes.size * width + (nodes.size - 1) * config.gap.width
      nodes.sortBy(_.id).zipWithIndex.map { (node, column) =>
        node.id -> Bounds(Point(margin + (contentWidth - rowWidth) / 2 + column * (width + config.gap.width),
          margin + row * (height + config.gap.height)), Dimension(width, height))
      }
    }.toMap
    val routes = chart.edges.zipWithIndex.map { (edge, index) =>
      val from = bounds(edge.from)
      val to = bounds(edge.to)
      val begin = Point(from.centerX, from.endY)
      val finish = Point(to.centerX, to.startY)
      val points =
        if (!edge.backEdge && ranks(edge.to) == ranks(edge.from) + 1) {
          val midY = (begin.y + finish.y) / 2
          List(begin, Point(begin.x, midY), Point(finish.x, midY), finish)
        } else {
          val lane = margin + contentWidth + 24 + index * 20
          List(begin, Point(begin.x, begin.y + 16), Point(lane, begin.y + 16),
            Point(lane, finish.y - 16), Point(finish.x, finish.y - 16), finish)
        }
      (edge, points)
    }
    val fullWidth = math.max(margin * 2 + contentWidth,
      routes.flatMap(_._2.map(_.x)).maxOption.getOrElse(0.0) + margin)
    val fullHeight = margin * 2 + (ranks.values.maxOption.getOrElse(0) + 1) * height +
      ranks.values.maxOption.getOrElse(0) * config.gap.height
    val fullSize = Dimension(fullWidth, fullHeight)
    val shapeStyle = style(config, fill = true)
    val textStyle = style(config, fill = false)
    val lineStyle = AppShapeElementConfig.turtleSegment[Double](RGBColor.black, 1.5)
    val connectorStyle = new AppShapeElementConfig[Double] {
      def useCustomPadding: Option[Dimension[Double]] = Some(Dimension(0.0, 0.0))
      def font: AppFont = config.font
      def colorStroke: AppColor = lineStyle.colorStroke
      def colorFill: AppColor = lineStyle.colorFill
      def colorFont: AppColor = lineStyle.colorFont
      override def strokeWidth: Double = lineStyle.strokeWidth
      override def fillEnabled: Boolean = false
      def onMouseClicked(leftButton: Boolean): Unit = ()
    }
    val elements = mutable.ListBuffer.empty[(AppShapeElement[Double], Bounds[Double])]
    elements += ((AppShapeDrawingRoutineElement(Connections(routes.map(_._2)), connectorStyle, Some(fullSize)),
      Bounds(Point(0.0, 0.0), fullSize)))
    def text(value: String, area: Bounds[Double]): Unit = {
      val size = config.font.measureText(value)
      val origin = Point(area.centerX - size.width / 2, area.centerY - size.height / 2)
      elements += ((AppShapeTextElement[Double](value, textStyle), Bounds(origin, size)))
    }
    chart.nodes.foreach { node =>
      val area = bounds(node.id)
      elements += ((AppShapeDrawingRoutineElement(NodeOutline(node.kind), shapeStyle, Some(area.dimension)), area))
      text(node.label, area)
    }
    routes.foreach { (edge, points) =>
      edge.label.foreach { label =>
        val first = points.head
        val second = points(1)
        val target = bounds(edge.to)
        val onLeft = target.centerX < first.x || (target.centerX == first.x && label == "Yes")
        val x = if (onLeft) first.x - 25 else first.x + 25
        text(label, Bounds(Point(x - 15, (first.y + second.y) / 2 - 8), Dimension(30.0, 16.0)))
      }
    }
    AppShapeComposition(FixedLayout(fullSize, elements.map(_._2).toList), textStyle, elements.map(_._1).toList)
  }

  /** Absolute child slots keep connectors and text aligned through the normal layout pipeline. */
  private case class FixedLayout(size: Dimension[Double], slots: List[Bounds[Double]]) extends AppShapeCompositeControl[Double] derives upickle.default.ReadWriter {
    def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)] = None
    def calculateMyMinimumDimension(children: List[AppElementMeasured[Double]], element: AppShapeElementConfig[Double], config: AppShapeRenderingConfig[Double]): RenderingDimension[Double] =
      RenderingDimension.fromRawDimensionAndConfig(size, element, config)
    def calculateChildrenDimensions(children: List[AppElementMeasured[Double]], size: RenderingDimension[Double], element: AppShapeElementConfig[Double], config: AppShapeRenderingConfig[Double]): List[AppElementDimensioned[Double]] =
      children.zip(slots).map { (child, slot) => child.withTargetDimension(RenderingDimension.fromRawDimensionAndConfig(slot.dimension, child.baseElement.elementConfig, config)) }
    def calculateChildrenPositions(children: List[AppElementDimensioned[Double]], size: RenderingDimension[Double], element: AppShapeElementConfig[Double], config: AppShapeRenderingConfig[Double]): List[AppElementPositioned[Double]] =
      children.zip(slots).map { (child, slot) => child.withOffset(slot.startPoint) }
  }

  private case class NodeOutline(kind: NodeKind) extends DrawingRoutine[Double] derives upickle.default.ReadWriter {
    def hasDesiredAspectRatio: Option[AspectRatio] = None
    def appendPathToBuilder(logger: Logger, builder: SvgPathBuilder[Double], size: Dimension[Double]): SvgPathBuilder[Double] = {
      val origin = builder.current
      val w = size.width
      val h = size.height
      def p(x: Double, y: Double) = Point(origin.x + x, origin.y + y)
      kind match {
        case NodeKind.Decision =>
          builder.moveToAbs(p(w / 2, 0)).lineToAbs(p(w, h / 2)).lineToAbs(p(w / 2, h)).lineToAbs(p(0, h / 2)).closePath()
        case NodeKind.Terminal =>
          val r = math.min(w, h) / 2
          builder.moveToAbs(p(r, 0)).lineToAbs(p(w - r, 0))
            .quadraticBezierToAbs(p(w, 0), p(w, h / 2)).quadraticBezierToAbs(p(w, h), p(w - r, h))
            .lineToAbs(p(r, h)).quadraticBezierToAbs(p(0, h), p(0, h / 2)).quadraticBezierToAbs(p(0, 0), p(r, 0)).closePath()
        case NodeKind.Process =>
          builder.lineToAbs(p(w, 0)).lineToAbs(p(w, h)).lineToAbs(p(0, h)).closePath()
      }
    }
  }

  private case class Connections(routes: List[List[Point[Double]]]) extends DrawingRoutine[Double] derives upickle.default.ReadWriter {
    def hasDesiredAspectRatio: Option[AspectRatio] = None
    def appendPathToBuilder(logger: Logger, builder: SvgPathBuilder[Double], size: Dimension[Double]): SvgPathBuilder[Double] = {
      val origin = builder.current
      def absolute(point: Point[Double]) = Point(origin.x + point.x, origin.y + point.y)
      routes.foldLeft(builder) { (path, points) =>
        val routed = points.tail.foldLeft(path.moveToAbs(absolute(points.head)))((p, point) => p.lineToAbs(absolute(point)))
        val tip = absolute(points.last)
        routed.moveToAbs(Point(tip.x - 5, tip.y - 8)).lineToAbs(tip).lineToAbs(Point(tip.x + 5, tip.y - 8))
      }
    }
  }
}
