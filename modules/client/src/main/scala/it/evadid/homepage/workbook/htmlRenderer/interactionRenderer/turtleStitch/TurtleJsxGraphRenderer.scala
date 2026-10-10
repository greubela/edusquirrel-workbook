package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleGraphic, TurtleGradingLogic}
import TurtleGradingLogic.{GradingLine, TurtleGraphicComparison, TurtleLineStatus}
import org.scalajs.dom
import upickle.default.*

import scala.scalajs.js
import scala.util.control.NonFatal


/** Renders and compares a turtle trace on a JSXGraph board.
 *
 * JSXGraph must be loaded by the host page (`JXG` must be available globally).
 * Scene coordinates follow TurtlePathBuilder's SVG convention (positive y points downwards).
 * They are converted to JSXGraph's upward-positive coordinates when displayed.
 */
object TurtleJsxGraphRenderer:

  def render[T: Fractional](program: Signal[List[TurtleCommand[T]]], graphic: TurtleGraphic): DomElementCollection = {
    program.signal.map(curList => {
      render(curList, graphic)
    })
  }

  /** Stitch reproduction accepts any pen-up route between the drawn segments. */
  def renderStrokes[T: Fractional](program: Signal[List[TurtleCommand[T]]], expected: TurtleGraphic): DomElementCollection =
    program.map(commands => render(buildScene(commands, expected, 1e-7, gradeJumps = false), "Turtle drawing"))

  def render[T: Fractional](program: List[TurtleCommand[T]], expected: TurtleGraphic): Element =
    render(buildScene(program, expected), "Turtle drawing")

  def render[T: Fractional](program: List[TurtleCommand[T]], expected: List[LineToRender[T]]): Element =
    render(buildScene(program, expected), "Turtle drawing")

  def render(scene: Scene, title: String): Element = {
    var dispose: () => Unit = () => ()
    div(
      cls := "turtle-gradig-panel",
      onMountCallback(event => {
        val container = event.thisNode.ref
        container.classList.remove("turtle-gradig-panel--unavailable")
        try {
          val library = jsxGraph()
          val board = render(container, scene, title)
          dispose = () => library.JSXGraph.freeBoard(board)
        } catch {
          case NonFatal(error) =>
            container.classList.add("turtle-gradig-panel--unavailable")
            container.textContent = error match {
              case _: IllegalArgumentException => "This drawing is outside the supported display range."
              case _ => "The drawing preview is unavailable. Try reloading the page."
            }
        }
      }),
      onUnmountCallback(_ => {
        val release = dispose
        dispose = () => ()
        release()
      })
    )
  }

  /* Factories */



  /* details */

  /** A line segment used as the expected result of a turtle exercise. */
  final case class LineToRender[T: Fractional](start: Point[T], end: Point[T], jump: Boolean = false)


  enum LineResult derives ReadWriter:
    case Correct, Unexpected, Missing, Neutral

  final case class RenderedLine(start: Point[Double], end: Point[Double], result: LineResult, jump: Boolean) derives ReadWriter

  final case class RenderedAngle(vertex: Point[Double], fromHeading: Double, degrees: Double, lineBefore: Int, lineAfter: Int) derives ReadWriter

  final case class Scene(lines: List[RenderedLine], angles: List[RenderedAngle]) derives ReadWriter

  private[turtleStitch] final case class AngleSector(first: Point[Double], last: Point[Double], degrees: Double)

  /** Rays point away from the vertex along both adjacent segments. Coordinates
    * are converted to JSXGraph here, and ordered to select the smaller sector.
    */
  private[turtleStitch] def angleSector(scene: Scene, angle: RenderedAngle, radius: Double): AngleSector =
    val vertex = Point(angle.vertex.x, -angle.vertex.y)
    def ray(endpoint: Point[Double]): Point[Double] =
      val dx = endpoint.x - angle.vertex.x
      val dy = angle.vertex.y - endpoint.y
      val length = math.hypot(dx, dy)
      Point(vertex.x + dx / length * radius, vertex.y + dy / length * radius)

    // Matching accepts reversed segments, so select the endpoint away from the
    // vertex rather than assuming the matched line's traversal direction.
    def otherEndpoint(line: RenderedLine): Point[Double] =
      def distance(point: Point[Double]): Double = math.hypot(point.x - angle.vertex.x, point.y - angle.vertex.y)
      if distance(line.start) >= distance(line.end) then line.start else line.end

    val incoming = ray(otherEndpoint(scene.lines(angle.lineBefore)))
    val outgoing = ray(otherEndpoint(scene.lines(angle.lineAfter)))
    val from = math.atan2(incoming.y - vertex.y, incoming.x - vertex.x)
    val to = math.atan2(outgoing.y - vertex.y, outgoing.x - vertex.x)
    val fullTurn = 2.0 * math.Pi
    val sweep = ((to - from) % fullTurn + fullTurn) % fullTurn
    if sweep <= math.Pi then AngleSector(incoming, outgoing, math.toDegrees(sweep))
    else AngleSector(outgoing, incoming, math.toDegrees(fullTurn - sweep))

  private def graphic[T: Fractional](program: List[TurtleCommand[T]]): TurtleGraphic = {
    val numeric = summon[Fractional[T]]
    TurtleGraphic.TurtleGraphicProgram(program.map(command =>
      TurtleCommand(command.name, command.args.map(numeric.toDouble), command.stringArgs)))
  }

  private def renderedLines(lines: List[GradingLine]): List[RenderedLine] = lines.map { graded =>
    val result = graded.status match
      case TurtleLineStatus.CORRECT => LineResult.Correct
      case TurtleLineStatus.EXPECTED_BUT_MISSING => LineResult.Missing
      case TurtleLineStatus.EXISTING_BUT_UNEXPECTED => LineResult.Unexpected
    RenderedLine(graded.line.start, graded.line.end, result, graded.jump)
  }

  private def renderedAngles(angles: Seq[TurtleGraphic.Angle]): List[RenderedAngle] = angles.map { angle =>
    RenderedAngle(angle.vertex, angle.fromHeading, angle.degrees, angle.lineBefore, angle.lineAfter)
  }.toList

  /** JSXGraph projects the shared-core geometry and grading result into display objects. */
  def buildScene[T: Fractional](program: List[TurtleCommand[T]], expected: List[LineToRender[T]], tolerance: Double = 1e-7, gradeJumps: Boolean = true): Scene = {
    val actual = graphic(program)
    val targets = expected.map(line => TurtleGraphic.Movement(line.start.toDouble, line.end.toDouble, line.jump))
    Scene(renderedLines(TurtleGradingLogic.compareLines(actual.renderMovements, targets, tolerance, gradeJumps)),
      renderedAngles(actual.renderAngles))
  }

  def buildScene[T: Fractional](program: List[TurtleCommand[T]], expected: TurtleGraphic): Scene =
    buildScene(program, expected, 1e-7)

  def buildScene[T: Fractional](program: List[TurtleCommand[T]], expected: TurtleGraphic, tolerance: Double): Scene =
    buildScene(program, expected, tolerance, gradeJumps = true)

  def buildScene[T: Fractional](program: List[TurtleCommand[T]], expected: TurtleGraphic, tolerance: Double, gradeJumps: Boolean): Scene =
    buildScene(graphic(program), expected, tolerance, gradeJumps)

  private[turtleStitch] def buildScene(actual: TurtleGraphic, expected: TurtleGraphic, tolerance: Double, gradeJumps: Boolean): Scene = {
    val comparison = TurtleGraphicComparison(actual, expected, tolerance, gradeJumps)
    Scene(renderedLines(comparison.difference), renderedAngles(comparison.angles))
  }

  /** Creates the JSXGraph board inside `container` and returns the board object. */
  def render[T: Fractional](container: dom.html.Div, program: List[TurtleCommand[T]], expected: List[LineToRender[T]]): js.Dynamic =
    render(container, buildScene(program, expected), "Turtle drawing")

  /** Creates the JSXGraph board using the expected graphic for both comparison and angle overlays. */
  def render[T: Fractional](container: dom.html.Div, program: List[TurtleCommand[T]], expected: TurtleGraphic): js.Dynamic =
    render(container, buildScene(program, expected), "Turtle drawing")

  private def jsxGraph(): js.Dynamic =
    val library = js.Dynamic.global.globalThis.selectDynamic("JXG")
    if js.isUndefined(library) || library == null then
      throw new IllegalStateException("JSXGraph is not loaded; expected global JXG")
    library

  def render(container: dom.html.Div, scene: Scene, title: String): js.Dynamic =
    container.classList.add("turtle-gradig-panel")
    val points = scene.lines.flatMap(line => List(line.start, line.end))
    if points.exists(point => !point.x.isFinite || !point.y.isFinite) then
      throw IllegalArgumentException("The drawing contains non-finite coordinates.")
    val bounds = boundingBox(points.map(p => Point(p.x, -p.y)))
    if bounds.exists(!_.isFinite) || !((bounds(2) - bounds(0)).isFinite && (bounds(1) - bounds(3)).isFinite) ||
      bounds(2) <= bounds(0) || bounds(1) <= bounds(3) then
      throw IllegalArgumentException("The drawing exceeds the supported display range.")
    val jxg = jsxGraph()
    // SVG tick labels avoid a forced HTML layout for each label on every board update.
    val axisOptions = js.Dynamic.literal(ticks = js.Dynamic.literal(label = js.Dynamic.literal(display = "internal")))
    val board: js.Dynamic = jxg.JSXGraph.initBoard(container, js.Dynamic.literal(
      title = Option(title).map(_.trim).filter(_.nonEmpty).getOrElse("Turtle drawing"),
      renderer = "svg", boundingbox = js.Array(bounds(0), bounds(1), bounds(2), bounds(3)), axis = true,
      defaultAxes = js.Dynamic.literal(x = axisOptions, y = axisOptions),
      keepaspectratio = true, showCopyright = false
    ))

    // Dense embroidery targets otherwise redraw the entire board after each object.
    val canBatch = js.typeOf(board.selectDynamic("suspendUpdate")) == "function" &&
      js.typeOf(board.selectDynamic("unsuspendUpdate")) == "function"

    val pointObjects = scala.collection.mutable.Map.empty[Point[Double], js.Dynamic]
    val angleObjects = scala.collection.mutable.Map.empty[Int, js.Dynamic]

    def pointObject(point: Point[Double]): js.Dynamic = pointObjects.getOrElseUpdate(point, createPoint(point))


    def createPoint(point: Point[Double]): js.Dynamic = {
      board.create("point", js.Array(point.x, -point.y), js.Dynamic.literal(
        name = "", fixed = true, showInfobox = false, label = js.Dynamic.literal(display = "internal"),
        cssClass = "turtle-point", highlightCssClass = "turtle-point is-emphasized"
      ))
    }

    def createLine(line: RenderedLine, index: Int): Unit = {
      val a = pointObject(line.start);
      val b = pointObject(line.end)
      val resultClass = line.result match
        case LineResult.Correct => "turtle-line--correct"
        case LineResult.Unexpected => "turtle-line--unexpected"
        case LineResult.Missing => "turtle-line--missing"
        case LineResult.Neutral => "turtle-line--neutral"
      val classes = s"turtle-line $resultClass" + (if line.jump then " turtle-line--jump" else "")
      val segment: js.Dynamic = board.create("segment", js.Array(a, b), js.Dynamic.literal(
        cssClass = classes, highlightCssClass = classes, fixed = true
      ))
      segment.on("over", (_: js.Any) => hoverRelated(scene, index, pointObjects, angleObjects, board, active = true))
      segment.on("out", (_: js.Any) => hoverRelated(scene, index, pointObjects, angleObjects, board, active = false))
    }

    def createAngle(angle: RenderedAngle, index: Int): Unit = {
      def length(line: RenderedLine): Double = math.hypot(line.end.x - line.start.x, line.end.y - line.start.y)
      val radius = math.min((bounds(2) - bounds(0)) / 18.0,
        math.min(length(scene.lines(angle.lineBefore)), length(scene.lines(angle.lineAfter))) / 4.0)
      val sector = angleSector(scene, angle, radius)

      def helper(point: Point[Double]): js.Dynamic = board.create("point", js.Array(point.x, point.y), js.Dynamic.literal(visible = false, fixed = true))

      val arc = board.create("angle", js.Array(helper(sector.first), pointObject(angle.vertex), helper(sector.last)), js.Dynamic.literal(
        name = "", radius = radius, `type` = "sector", orthoType = "sector", selection = "auto", cssClass = "turtle-angle", highlightCssClass = "turtle-angle is-emphasized", fixed = true
      ))
      arc.on("over", (_: js.Any) => setAngleHover(arc, sector.degrees, active = true, board))
      arc.on("out", (_: js.Any) => setAngleHover(arc, sector.degrees, active = false, board))
      angleObjects(index) = arc
    }

    try
      if canBatch then board.suspendUpdate()
      scene.lines.zipWithIndex.foreach { case (line, index) => createLine(line, index) }
      // Hundreds of repeated angle sectors obscure dense stitch patterns and are
      // expensive JSXGraph objects. Keep one representative of each turn there.
      val angleEntries = scene.angles.zipWithIndex
      val visibleAngles = if angleEntries.size <= 128 then angleEntries
        else angleEntries.groupBy { case (angle, _) => math.round(angle.degrees * 1e6) }
          .values.map(_.head).toList.sortBy(_._2)
      visibleAngles.foreach { case (angle, index) => createAngle(angle, index) }
      if canBatch then board.unsuspendUpdate() else board.update()
      board
    catch
      case NonFatal(error) =>
        jxg.JSXGraph.freeBoard(board)
        throw error

  private def hoverRelated(scene: Scene, lineIndex: Int, points: collection.mutable.Map[Point[Double], js.Dynamic], angles: collection.mutable.Map[Int, js.Dynamic], board: js.Dynamic, active: Boolean): Unit =
    val line = scene.lines(lineIndex)
    List(line.start, line.end).foreach { p =>
      points.get(p).foreach(_.setAttribute(js.Dynamic.literal(
        name = (if active then s"(${format(p.x)}, ${format(p.y)})" else ""),
        cssClass = (if active then "turtle-point is-emphasized" else "turtle-point")
      )))
    }
    scene.angles.zipWithIndex.filter { case (a, _) => a.lineBefore == lineIndex || a.lineAfter == lineIndex }.foreach { case (a, i) => angles.get(i).foreach(setAngleHover(_, angleSector(scene, a, 1.0).degrees, active, board)) }
    board.update()

  private def setAngleHover(obj: js.Dynamic, degrees: Double, active: Boolean, board: js.Dynamic): Unit =
    obj.setAttribute(js.Dynamic.literal(name = (if active then s"${format(degrees)}°" else ""),
      cssClass = (if active then "turtle-angle is-emphasized" else "turtle-angle")))
    board.update()

  private def format(value: Double): String =
    val rounded = math.rint(value * 1000.0) / 1000.0
    if rounded == rounded.toLong then rounded.toLong.toString else rounded.toString

  private def boundingBox(points: List[Point[Double]]): Array[Double] =
    if points.isEmpty then Array(-10.0, 10.0, 10.0, -10.0)
    else
      val xs = points.map(_.x);
      val ys = points.map(_.y)
      val span = math.max(math.max(xs.max - xs.min, ys.max - ys.min), 1.0)
      val padding = span * 0.12
      Array(xs.min - padding, ys.max + padding, xs.max + padding, ys.min - padding)
