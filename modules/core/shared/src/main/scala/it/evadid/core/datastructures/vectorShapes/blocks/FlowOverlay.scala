package it.evadid.core.datastructures.vectorShapes.blocks

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.core.datastructures.vectorShapes.blocks.BlockArrangement.Fixed
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder

/** Progress of a control-flow path while the rows of a program are drawn top to bottom. */
enum FlowPathStatus {
  case Paused, Finished, Open, Handled
}

final case class FlowSegment(path: SvgPathBuilder[Double], isActive: Boolean, stroke: FlowStroke)

final case class FlowPath(status: FlowPathStatus, segments: Seq[FlowSegment]) {
  def lastSegment: FlowSegment = segments.last

  def changeLastPath(change: SvgPathBuilder[Double] => SvgPathBuilder[Double]): FlowPath = {
    require(segments.nonEmpty, s"Cannot extend an empty control-flow path: $this")
    FlowPath(FlowPathStatus.Handled, segments.init :+ lastSegment.copy(path = change(lastSegment.path)))
  }

  def appendSegment(segment: FlowSegment): FlowPath = FlowPath(FlowPathStatus.Handled, segments :+ segment)

  def appendSegmentAtEnd(segment: Point[Double] => FlowSegment): FlowPath = appendSegment(segment(lastSegment.path.current))
}

/** Arrows and junction symbols drawn on top of the control-flow lines. */
enum FlowDecorationKind {
  case StartTriangle
  case UpDownArrow(pointsDown: Boolean, isActive: Boolean)
  case LeftRightArrow(pointsLeft: Boolean, isActive: Boolean)
  case DataArrow(pointsLeft: Boolean)
  case SplitSymbol
  case UnionSymbol

  def path(center: Point[Double], segment: Double): SvgPathBuilder[Double] = {
    val paths = FlowDecorationPaths[Double](segment)
    this match {
      case StartTriangle =>
        val side = segment * 2
        val height = Math.sqrt(2) * segment
        SvgPathBuilder(center).moveToRel(Dimension(-side / 2, -height / 2)).horizontalLineWithWidth(side)
          .lineToRel(Dimension(-side / 2, height)).lineToRel(Dimension(-side / 2, -height)).closePath()
      case UpDownArrow(down, _) => if down then paths.controlArrowDown(center) else paths.controlArrowUp(center)
      case LeftRightArrow(left, _) => if left then paths.controlArrowLeft(center) else paths.controlArrowRight(center)
      case DataArrow(left) => if left then paths.dataArrowLeft(center) else paths.dataArrowRight(center)
      case SplitSymbol =>
        SvgPathBuilder(center).moveToRel(Dimension(0.0, -segment)).lineToRel(Dimension(segment, segment))
          .lineToRel(Dimension(-segment, segment)).lineToRel(Dimension(-segment, -segment))
          .lineToRel(Dimension(segment, -segment)).closePath()
      case UnionSymbol => SvgPathBuilder(center).addCenteredCircle(segment).closePath()
    }
  }

  def size(segment: Double): Dimension[Double] = this match {
    case StartTriangle => Dimension(segment * 2, Math.sqrt(2) * segment)
    case UpDownArrow(_, _) | LeftRightArrow(_, _) => Dimension(segment / 5.0 * 6.0, segment / 5.0 * 8.0)
    case DataArrow(_) => Dimension(segment / 5.0 * 8.0, segment / 5.0 * 8.0)
    case SplitSymbol | UnionSymbol => Dimension(segment * 2, segment * 2)
  }

  def paint(paints: BlockPaints): BlockPaint = this match {
    case StartTriangle | DataArrow(_) => paints.activeDecoration
    case UpDownArrow(_, active) => if active then paints.activeDecoration else paints.inactiveDecoration
    case LeftRightArrow(_, active) => if active then paints.activeDecoration else paints.inactiveDecoration
    case SplitSymbol | UnionSymbol => paints.flowSymbol
  }
}

final case class FlowDecoration(kind: FlowDecorationKind, center: Point[Double])

/** Immutable builder of the control-flow overlay; each row's flow shape transforms the open paths. */
final case class FlowOverlayBuilder(paths: Seq[FlowPath], decorations: Seq[FlowDecoration]) {
  private lazy val open: Seq[(FlowPath, Int)] = paths.zipWithIndex.filter(_._1.status == FlowPathStatus.Open)

  def firstOpenPath: FlowPath = open.head._1
  def secondOpenPath: FlowPath = open(1)._1

  private def replaced(index: Int, path: FlowPath): Seq[FlowPath] = paths.updated(index, path)

  def startNewPathAtOrigin(): FlowOverlayBuilder =
    startNewPath(FlowPath(FlowPathStatus.Open, List(FlowSegment(SvgPathBuilder(Point(0.0, 0.0)), true, FlowStroke.Active))))

  def startNewPath(path: FlowPath): FlowOverlayBuilder = copy(paths = paths :+ path)

  def changeFirstOpenPath(change: FlowPath => FlowPath): FlowOverlayBuilder =
    copy(paths = replaced(open.head._2, change(open.head._1)))

  def unionFirstTwoOpen(combine: (FlowPath, FlowPath) => FlowPath): FlowOverlayBuilder = {
    val (first, firstIndex) = open.head
    val (second, secondIndex) = open(1)
    copy(paths = paths.updated(firstIndex, combine(first, second)).patch(secondIndex, Nil, 1))
  }

  def changeLastPaused(change: FlowPath => FlowPath): FlowOverlayBuilder = {
    val index = paths.lastIndexWhere(_.status == FlowPathStatus.Paused)
    copy(paths = replaced(index, change(paths(index))))
  }

  def resetHandledToOpen(): FlowOverlayBuilder =
    copy(paths = paths.map(path => if path.status == FlowPathStatus.Handled then path.copy(status = FlowPathStatus.Open) else path))

  def addDecoration(kind: FlowDecorationKind, center: Point[Double]): FlowOverlayBuilder =
    copy(decorations = decorations :+ FlowDecoration(kind, center))

  /** The drawing's extent: path boxes measured from the origin as widths, decorations up to their far edges. */
  def displaySize(segment: Double): Dimension[Double] = {
    val ends = paths.flatMap(_.segments).map(_.path.requiresDimension).map(size => Point(size.width, size.height)) ++
      decorations.map(decoration => decoration.center.moveWithDimension(decoration.kind.size(segment).scaled(0.5)))
    Dimension(ends.map(_.x).maxOption.getOrElse(0.0), ends.map(_.y).maxOption.getOrElse(0.0))
  }

  /** Every segment and decoration as an absolute-coordinate path element, lines below decorations. */
  def toShape(rendering: BlockRenderingConfig): AppShapeElement[Double] = {
    val paints = rendering.paints
    def element(path: SvgPathBuilder[Double], paint: BlockPaint) =
      BlockElements.fixedPath(path, BlockElementConfig(paint, rendering.font)) -> path.bounds
    val lines = paths.flatMap(_.segments).map(segment => element(segment.path, paints.flow(segment.stroke)))
    val symbols = decorations.map(decoration =>
      element(decoration.kind.path(decoration.center, rendering.segmentSize), decoration.kind.paint(paints)))
    val all = (lines ++ symbols).toList
    BlockElements.composite(Fixed(all.map(_._2)), all.map(_._1), BlockElementConfig(paints.control, rendering.font))
  }
}

/** The control-flow column of one program row: its background and how it continues the flow paths. */
enum FlowShape {
  case ProgramStarter, ProgramStopper, Empty, FunctionCall
  case Directed(goesDown: Boolean, isActive: Boolean)
  case DownUp(isActive: Boolean)
  case IfElseSplit, IfElseCross, IfElseUnion, RepetitionSplit, RepetitionUnion

  def widthInIndentations: Int = this match {
    case IfElseSplit | IfElseCross | IfElseUnion | RepetitionSplit | RepetitionUnion => 2
    case _ => 1
  }

  def minHeightInSegments: Int = this match {
    case ProgramStarter | ProgramStopper => 3
    case Empty => 1
    case FunctionCall | Directed(_, _) | DownUp(_) => 2
    case IfElseSplit | RepetitionSplit => 8
    case IfElseCross | RepetitionUnion => 5
    case IfElseUnion => 7
  }

  def naturalSize(segment: Double): Dimension[Double] = Dimension(segment * widthInIndentations * 6, segment * minHeightInSegments)

  /** The connector background, stretched to the row. */
  def background(bounds: Bounds[Double], segment: Double): SvgPathBuilder[Double] = {
    val paths = FlowDecorationPaths[Double](segment)
    this match {
      case ProgramStarter => paths.buildControlFlowStart(bounds)
      case ProgramStopper => paths.buildControlFlowStop(bounds)
      case IfElseSplit | RepetitionSplit => paths.buildControlFlowBackgroundMultipleSize(bounds, List((true, true), (false, true)), false)
      case IfElseCross => paths.buildControlFlowBackgroundMultipleSize(bounds, List((true, true), (true, true)), false)
      case IfElseUnion | RepetitionUnion => paths.buildControlFlowBackgroundMultipleSize(bounds, List((true, true), (true, false)), false)
      case _ => paths.buildControlFlowBackgroundMultipleSize(bounds, List((true, true)), true)
    }
  }

  def continueFlow(flow: FlowOverlayBuilder, segment: Double, center: Point[Double], lineHeight: Double): FlowOverlayBuilder = {
    val extra = math.max(0.0, lineHeight - segment * minHeightInSegments)
    this match {
      case ProgramStarter =>
        val height = lineHeight + segment
        flow.startNewPathAtOrigin()
          .changeFirstOpenPath(_.changeLastPath(_.moveToRel(Dimension(3 * segment, height / 2)).lineToRel(Dimension(0.0, height / 2))))
          .addDecoration(FlowDecorationKind.StartTriangle, Point(center.x, center.y - segment / 2))
      case ProgramStopper =>
        val height = lineHeight - segment / 2
        flow.changeFirstOpenPath(_.changeLastPath(_.lineToRel(Dimension(0.0, height / 2)).moveToRel(Dimension(-segment, 0.0))
          .horizontalLineWithWidth(2 * segment)).copy(status = FlowPathStatus.Finished))
      case Empty => flow.changeFirstOpenPath(_.changeLastPath(_.moveToRel(Dimension(0.0, lineHeight))))
      case FunctionCall => Directed(true, true).continueFlow(flow, segment, center, lineHeight)
      case Directed(down, active) =>
        val (path, arrows) = FlowShape.directed(flow.firstOpenPath, segment, center, lineHeight)
        arrows.foldLeft(flow.changeFirstOpenPath(_ => path))((current, arrow) =>
          current.addDecoration(FlowDecorationKind.UpDownArrow(down, active), arrow))
      case DownUp(_) =>
        flow.changeFirstOpenPath(_.changeLastPath(_.verticalLineWithHeight(lineHeight)))
          .changeFirstOpenPath(_.changeLastPath(_.verticalLineWithHeight(lineHeight)))
      case IfElseSplit =>
        val parent = flow.firstOpenPath.changeLastPath(_.verticalLineWithHeight(extra / 2).lineToRel(Dimension(0.0, segment))
          .lineToRel(Dimension(3 * segment, 0.0)).lineToRel(Dimension(0.0, 3 * segment)))
        val end = parent.lastSegment.path.current
        val falsePath = SvgPathBuilder(end).lineToRel(Dimension(-3 * segment, 3 * segment)).lineToRel(Dimension(0.0, segment))
          .verticalLineWithHeight(extra / 2)
        val truePath = SvgPathBuilder(end).lineToRel(Dimension(3 * segment, 3 * segment)).lineToRel(Dimension(0.0, segment))
          .verticalLineWithHeight(extra / 2)
        flow.changeFirstOpenPath(_ => parent.copy(status = FlowPathStatus.Paused))
          .startNewPath(FlowPath(FlowPathStatus.Handled, List(FlowSegment(falsePath, false, FlowStroke.ActiveFalse),
            FlowSegment(SvgPathBuilder(falsePath.current), false, FlowStroke.InactiveFalse))))
          .startNewPath(FlowPath(FlowPathStatus.Open, List(FlowSegment(truePath, false, FlowStroke.ActiveTrue))))
          .addDecoration(FlowDecorationKind.SplitSymbol, center)
          .addDecoration(FlowDecorationKind.DataArrow(true), center.moveWithDimension(Dimension(3.0 * segment, 0.0)))
      case IfElseCross =>
        val leftToRight = FlowShape.cross(flow.firstOpenPath, lineHeight, segment, true, FlowStroke.ActiveFalse, minHeightInSegments)
        val rightToLeft = FlowShape.cross(flow.secondOpenPath, lineHeight, segment, false, FlowStroke.InactiveTrue, minHeightInSegments)
        flow.changeFirstOpenPath(_ => rightToLeft).changeFirstOpenPath(_ => leftToRight)
      case IfElseUnion =>
        def finish(path: FlowPath, toTheRight: Boolean) = {
          val finished = path.changeLastPath(_.lineToRel(Dimension(if toTheRight then 3 * segment else -3 * segment, 3 * segment)))
          finished.copy(status = FlowPathStatus.Finished)
        }
        val first = finish(flow.firstOpenPath, true)
        val afterFirst = flow.changeFirstOpenPath(_ => first)
        val second = finish(afterFirst.firstOpenPath, false)
        val joined = first.lastSegment.path.current
        afterFirst.changeFirstOpenPath(_ => second)
          .changeLastPaused(_.changeLastPath(_.moveToAbs(joined).lineToRel(Dimension(0.0, 2 * segment)).horizontalLineWithWidth(-3 * segment)
            .verticalLineWithHeight(2 * segment).verticalLineWithHeight(math.max(0.0, lineHeight - segment * 7))).copy(status = FlowPathStatus.Handled))
          .addDecoration(FlowDecorationKind.UnionSymbol, center.moveWithDimension(Dimension(0.0, -segment)))
          .addDecoration(FlowDecorationKind.UpDownArrow(true, true), center.moveWithDimension(Dimension(0.0, -segment)))
      case RepetitionSplit =>
        val toUnion = flow.firstOpenPath.lastSegment.path.verticalLineWithHeight(extra / 2.0).verticalLineWithHeight(segment)
          .horizontalLineWithWidth(3 * segment)
        val union = toUnion.current.moveWithDimension(Dimension(0.0, segment))
        val parent = flow.firstOpenPath.changeLastPath(_ => toUnion.verticalLineWithHeight(3 * segment)).copy(status = FlowPathStatus.Paused)
        val end = parent.lastSegment.path.current
        val stop = SvgPathBuilder(end).lineToRel(Dimension(-3.5 * segment, 3.5 * segment)).verticalLineWithHeight(0.5 * segment)
        val loopback = SvgPathBuilder(union).horizontalLineWithWidth(-2.5 * segment).verticalLineWithHeight(extra / 2.0)
          .verticalLineWithHeight(6.0 * segment)
        val accept = SvgPathBuilder(end).lineToRel(Dimension(3 * segment, 3 * segment)).lineToRel(Dimension(0.0, segment))
          .verticalLineWithHeight(extra / 2)
        flow.changeFirstOpenPath(_ => parent)
          .startNewPath(FlowPath(FlowPathStatus.Handled, List(FlowSegment(stop, false, FlowStroke.ActiveFalse),
            FlowSegment(SvgPathBuilder(stop.current).verticalLineWithHeight(extra / 2), false, FlowStroke.InactiveFalse))))
          .startNewPath(FlowPath(FlowPathStatus.Handled, List(FlowSegment(loopback, false, FlowStroke.Inactive))))
          .startNewPath(FlowPath(FlowPathStatus.Handled, List(FlowSegment(accept, false, FlowStroke.ActiveTrue))))
          .addDecoration(FlowDecorationKind.SplitSymbol, center.moveWithDimension(Dimension(0.0, segment / 2.0)))
          .addDecoration(FlowDecorationKind.DataArrow(true), center.moveWithDimension(Dimension(3.0 * segment, 0.0)))
          .addDecoration(FlowDecorationKind.UnionSymbol, union.moveWithDimension(Dimension(0.0, -segment / 2.0)))
          .addDecoration(FlowDecorationKind.UpDownArrow(true, true), union.moveWithDimension(Dimension(0.0, -segment / 2.0)))
      case RepetitionUnion =>
        val stopped = flow.firstOpenPath.appendSegmentAtEnd(position => FlowSegment(SvgPathBuilder(position).verticalLineWithHeight(segment)
          .lineToRel(Dimension(segment / 2, segment / 2)).verticalLineWithHeight(segment * 1.5), false, FlowStroke.ActiveFalse))
          .copy(status = FlowPathStatus.Finished)
        val end = stopped.lastSegment.path.current
        flow.changeFirstOpenPath(_ => stopped)
          .unionFirstTwoOpen { (first, second) =>
            val shortened = second.changeLastPath(_.lineToRel(Dimension(-segment, segment)))
            val combining = SvgPathBuilder(shortened.lastSegment.path.current).horizontalLineWithWidth(-2 * segment)
              .horizontalLineWithWidth(-1.5 * segment).lineToRel(Dimension(-segment, -segment))
            FlowPath(FlowPathStatus.Finished, shortened.segments ++ List(FlowSegment(combining, false, FlowStroke.Inactive)) ++ first.segments)
          }
          .changeLastPaused(_.changeLastPath(_.moveToAbs(end).lineToRel(Dimension(0.0, 2 * segment)).verticalLineWithHeight(extra))
            .copy(status = FlowPathStatus.Handled))
          .addDecoration(FlowDecorationKind.UnionSymbol, end)
    }
  }
}

object FlowShape {
  /** A straight row with start and end marks and an arrow per four segments of height. */
  private def directed(path: FlowPath, segment: Double, center: Point[Double], lineHeight: Double): (FlowPath, List[Point[Double]]) = {
    val arrowStep = (segment * 4).toInt
    val arrowHeight = segment / 5.0 * 9.0
    val gap = segment / 5.0
    val lineLength = arrowStep - arrowHeight - gap
    val top = center.y - lineHeight / 2
    def mark(builder: SvgPathBuilder[Double]) =
      builder.moveToRel(Dimension(-5.0, 0.0)).horizontalLineWithWidth(10.0).moveToRel(Dimension(-5.0, 0.0))
    val arrows = (0 until lineHeight.toInt / arrowStep).toList
    val withArrows = arrows.foldLeft(path.changeLastPath(mark)) { (current, _) =>
      current.changeLastPath(_.verticalLineWithHeight(lineLength / 2).moveToRel(Dimension(0.0, arrowHeight + 2 * gap))
        .verticalLineWithHeight(lineLength / 2))
    }
    val step = lineLength + arrowHeight + 2 * gap
    val centers = arrows.map(index => Point(center.x, top + step * index + lineLength / 2 + gap + arrowHeight / 2))
    (withArrows.changeLastPath(_.verticalLineWithHeight(lineHeight - step * arrows.size)).changeLastPath(mark), centers)
  }

  private def cross(path: FlowPath, lineHeight: Double, segment: Double, toTheRight: Boolean, stroke: FlowStroke,
                    minHeightInSegments: Int): FlowPath = {
    val extra = math.max(0.0, lineHeight - segment * minHeightInSegments)
    def x(distance: Double) = if toTheRight then distance else -distance
    def middle(builder: SvgPathBuilder[Double]) =
      builder.horizontalLineWithWidth(x(2 * segment)).lineToRel(Dimension(x(segment), segment)).horizontalLineWithWidth(x(2 * segment))
    val down = path.lastSegment.path.verticalLineWithHeight(extra / 2).verticalLineWithHeight(segment)
      .lineToRel(Dimension(x(segment / 2.0), segment / 2.0))
    val continued = if toTheRight then middle(down) else down
    val start = SvgPathBuilder(continued.current)
    val second = (if toTheRight then start else middle(start)).lineToRel(Dimension(x(segment / 2.0), segment / 2.0))
      .verticalLineWithHeight(2 * segment).verticalLineWithHeight(extra / 2)
    path.changeLastPath(_ => continued).appendSegment(FlowSegment(second, true, stroke))
  }
}

/** One row of a control-flow column; nesting pushes the flow shape continuing in later rows. */
enum FlowLine {
  case Plain(shape: FlowShape)
  case Increase(shape: FlowShape, push: FlowShape)
  case Replace(shape: FlowShape, push: FlowShape)
  case Decrease(shape: FlowShape)

  def shape: FlowShape

  def popBefore: Boolean = this match {
    case Replace(_, _) | Decrease(_) => true
    case _ => false
  }

  def pushAfter: Option[FlowShape] = this match {
    case Increase(_, push) => Some(push)
    case Replace(_, push) => Some(push)
    case _ => None
  }
}

/** The rows of a program's control flow with the enclosing flow shapes of every row. */
final case class FlowLayout(lines: Seq[FlowLine]) {
  /** Each line with the stack of enclosing flow shapes, outermost first. */
  lazy val stacked: Seq[(FlowLine, List[FlowShape])] =
    lines.foldLeft((List.empty[FlowShape], Vector.empty[(FlowLine, List[FlowShape])])) { case ((stack, result), line) =>
      val current = if line.popBefore then stack.tail else stack
      val next = line.pushAfter.fold(current)(_ :: current)
      (next, result :+ (line -> current.reverse))
    }._2

  def columnWidths(segment: Double): Seq[Double] = {
    val depth = stacked.map(_._2.size).maxOption.getOrElse(0)
    (0 until depth).map(column => stacked.flatMap(_._2.lift(column)).map(_.naturalSize(segment).width).max)
  }

  /** The minimum row heights from the flow shapes alone. */
  def flowHeights(segment: Double): Seq[Double] =
    stacked.map((line, stack) => (line.shape :: stack).map(_.naturalSize(segment).height).max)

  /** Draws the overlay for rows of the given heights; the first row starts one segment below the origin. */
  def overlay(rendering: BlockRenderingConfig, heights: Seq[Double]): FlowOverlayBuilder = {
    require(heights.size == lines.size, "Every flow row needs a height")
    val segment = rendering.segmentSize
    val widths = columnWidths(segment)
    val centers = widths.scanLeft(0.0)(_ + _).zip(widths).map((offset, width) => offset + width / 2)
    stacked.zip(heights).foldLeft((FlowOverlayBuilder(Nil, Nil), segment)) {
      case ((builder, top), ((line, stack), height)) =>
        val centerY = top + height / 2
        val nested = stack.reverse.zip(centers).foldLeft(builder) {
          case (current, (shape, centerX)) => shape.continueFlow(current, segment, Point(centerX, centerY), height)
        }
        val ownX = widths.take(stack.size).sum + line.shape.naturalSize(segment).width / 2
        (line.shape.continueFlow(nested, segment, Point(ownX, centerY), height).resetHandledToOpen(), top + height)
    }._1
  }
}
