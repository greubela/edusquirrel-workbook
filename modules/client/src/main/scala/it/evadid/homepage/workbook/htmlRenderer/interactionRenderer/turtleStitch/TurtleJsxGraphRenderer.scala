package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.core.util.io.{ConstructorLikeParserWithJsonElements, Serializer}
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.workbook.elements.interactionElements.programming.TurtleGraphic
import org.scalajs.dom
import upickle.default.*

import scala.collection.mutable.ListBuffer
import scala.scalajs.js


/** Renders and compares a turtle trace on a JSXGraph board.
 *
 * JSXGraph must be loaded by the host page (`JXG` must be available globally).
 * Coordinates use turtle/JSXGraph coordinates (positive y points upwards).
 */
object TurtleJsxGraphRenderer:

  def render[T: Fractional](program: Signal[List[TurtleCommand[T]]], graphic: TurtleGraphic): DomElementCollection = {
    program.signal.map(curList => {
      render(curList, graphic)
    })
  }

  def render[T: Fractional](program: List[TurtleCommand[T]], expected: TurtleGraphic): Element = {
    val container = div(
      cls := "turtle-gradig-panel",
      "TurtleJsxGraphRenderer: Not supporting TurtleGraphic yet!"
    )
    container
  }

  def render[T: Fractional](program: List[TurtleCommand[T]], expected: List[LineToRender[T]]): Element = {
    val container = div(
      cls := "turtle-gradig-panel",
      onMountCallback(event => {
        render(event.thisNode.ref, program, expected)
      })
    )
    container
  }

  /* Factories */

  private val pointBackgroundColor = "red"


  /* details */

  private sealed trait ObjectsToRender {

  }

  /** A line segment used as the expected result of a turtle exercise. */
  final case class LineToRender[T: Fractional](start: Point[T], end: Point[T])


  enum LineResult derives ReadWriter:
    case Correct, Unexpected, Missing

  private given prw: ReadWriter[Point[Double]] = new Serializer[Point[Double]]() {

    override def serialize(obj: Point[Double]): String = s"Point(Double)(${obj.x.toString})(${obj.y.toString})"

    override def deserialize(str: String): Point[Double] = {
      val res = ConstructorLikeParserWithJsonElements.parseString(str)
      println("TurtleJsxRendering::deserialize not implemented correctly")
      Point(res.get.jsonPayloads(1).toDouble, res.get.jsonPayloads(2).toDouble)
    }
  }.uPickleReadWrite

  final case class RenderedLine(start: Point[Double], end: Point[Double], result: LineResult, jump: Boolean) derives ReadWriter

  final case class RenderedAngle(vertex: Point[Double], fromHeading: Double, degrees: Double, lineBefore: Int, lineAfter: Int) derives ReadWriter

  final case class Scene(lines: List[RenderedLine], angles: List[RenderedAngle]) derives ReadWriter

  private final case class Movement(start: Point[Double], end: Point[Double], jump: Boolean) derives ReadWriter

  private final case class PendingAngle(vertex: Point[Double], fromHeading: Double, degrees: Double, lineBefore: Int) derives ReadWriter

  /** Builds a testable rendering model and performs a one-to-one, direction-independent
   * comparison of actual and expected segments.
   */
  def buildScene[T: Fractional](program: List[TurtleCommand[T]], expected: List[LineToRender[T]], tolerance: Double = 1e-7): Scene =
    val numeric = summon[Fractional[T]]
    var position = Point(0.0, 0.0)
    var heading = 0.0
    var penDown = true
    val movements = ListBuffer.empty[Movement]
    val angles = ListBuffer.empty[RenderedAngle]
    var lastForwardLine: Option[Int] = None
    var pendingAngle: Option[PendingAngle] = None

    def normal(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

    def move(end: Point[Double], jump: Boolean, isForward: Boolean): Unit =
      val index = movements.size
      movements += Movement(position, end, jump)
      if isForward then
        pendingAngle.filter(p => close(p.vertex, position, tolerance)).foreach { p =>
          angles += RenderedAngle(position, p.fromHeading, p.degrees, p.lineBefore, index)
        }
        pendingAngle = None
        lastForwardLine = Some(index)
      else
        pendingAngle = None
        lastForwardLine = None
      position = end

    def forward(distance: Double): Unit =
      val radians = Math.toRadians(heading)
      move(Point(position.x + Math.cos(radians) * distance, position.y + Math.sin(radians) * distance), !penDown, true)

    program.foreach { command =>
      val name = command.name.trim.toLowerCase.replace('-', '_')
      val args = command.args.map(numeric.toDouble)
      name match
        case "forward" | "fd" => args.headOption.foreach(forward)
        case "backward" | "back" | "bk" => args.headOption.foreach(d => forward(-d))
        case "left" | "lt" | "turn_left" | "turnleft" => args.headOption.foreach { degrees =>
          lastForwardLine.foreach(i => pendingAngle = Some(PendingAngle(position, heading, degrees, i)))
          heading = normal(heading + degrees)
        }
        case "right" | "rt" | "turn" => args.headOption.foreach { degrees =>
          lastForwardLine.foreach(i => pendingAngle = Some(PendingAngle(position, heading, -degrees, i)))
          heading = normal(heading - degrees)
        }
        case "goto" | "setpos" | "setposition" | "goto_x_y" | "gotoxy" if args.size >= 2 =>
          move(Point(args(0), args(1)), jump = true, isForward = false)
        case "setx" | "set_x" | "setxposition" => args.headOption.foreach(x => move(Point(x, position.y), jump = true, isForward = false))
        case "sety" | "set_y" | "setyposition" => args.headOption.foreach(y => move(Point(position.x, y), jump = true, isForward = false))
        case "setheading" | "seth" | "set_heading" => args.headOption.foreach(h => heading = normal(h))
        case "penup" | "pu" | "up" | "pen_up" => penDown = false
        case "pendown" | "pd" | "down" | "pen_down" => penDown = true
        case "home" => move(Point(0.0, 0.0), jump = true, isForward = false); heading = 0.0
        case "clear" | "clearscreen" => movements.clear(); angles.clear(); lastForwardLine = None; pendingAngle = None
        case "reset" => movements.clear(); angles.clear(); position = Point(0.0, 0.0); heading = 0.0; penDown = true; lastForwardLine = None; pendingAngle = None
        case _ => ()
    }

    val unmatchedExpected = ListBuffer.from(expected.map(line => LineToRender(line.start.toDouble, line.end.toDouble)))
    val rendered = movements.map { movement =>
      val matchIndex = unmatchedExpected.indexWhere(line => sameLine(movement.start, movement.end, line.start, line.end, tolerance))
      val result = if matchIndex >= 0 then {
        unmatchedExpected.remove(matchIndex);
        LineResult.Correct
      } else LineResult.Unexpected
      RenderedLine(movement.start, movement.end, result, movement.jump)
    }
    val missing = unmatchedExpected.map(line => RenderedLine(line.start, line.end, LineResult.Missing, jump = false))
    Scene((rendered ++ missing).toList, angles.toList)


  /** Creates the JSXGraph board inside `container` and returns the board object. */
  def render[T: Fractional](container: dom.html.Div, program: List[TurtleCommand[T]], expected: List[LineToRender[T]]): js.Dynamic =
    val scene = buildScene(program, expected)
    val points = scene.lines.flatMap(line => List(line.start, line.end))
    val bounds = boundingBox(points)
    val jxg = js.Dynamic.global.selectDynamic("JXG")
    if js.isUndefined(jxg) then throw new IllegalStateException("JSXGraph is not loaded; expected global JXG")
    if container.id.isEmpty then container.id = s"turtle-jsxgraph-${Math.abs(js.Date.now().toLong)}"
    val board: js.Dynamic = jxg.JSXGraph.initBoard(container.id, js.Dynamic.literal(
      boundingbox = js.Array(bounds(0), bounds(1), bounds(2), bounds(3)), axis = true, keepaspectratio = true, showCopyright = false
    ))

    val pointObjects = scala.collection.mutable.Map.empty[Point[Double], js.Dynamic]
    val angleObjects = scala.collection.mutable.Map.empty[Int, js.Dynamic]

    def pointObject(point: Point[Double]): js.Dynamic = pointObjects.getOrElseUpdate(point, createPoint(point))


    def createPoint(point: Point[Double]): js.Dynamic = {
      val label = s"(${format(point.x)}, ${format(point.y)})"
      val obj = board.create("point", js.Array(point.x, point.y), js.Dynamic.literal(
        name = "", size = 3, fixed = true, strokeColor = pointBackgroundColor, fillColor = pointBackgroundColor, fillOpacity = 0.1, strokeOpacity = 0.1,
        highlightFillOpacity = 1.0, highlightStrokeOpacity = 1.0, showInfobox = false
      ))
      /*obj.on("over", (_: js.Any) => {
        obj.setAttribute(js.Dynamic.literal(name = label, fillOpacity = 1.0, strokeOpacity = 1.0));
        board.update()
      })
      obj.on("out", (_: js.Any) => {
        obj.setAttribute(js.Dynamic.literal(name = "", fillOpacity = 0.1, strokeOpacity = 0.1));
        board.update()
      })*/
      obj
    }

    def createLine(line: RenderedLine, index: Int): Unit = {
      val a = pointObject(line.start);
      val b = pointObject(line.end)
      val color = line.result match
        case LineResult.Correct => "#159447"
        case LineResult.Unexpected => "#d12f2f"
        case LineResult.Missing => "#111111"
      val segment: js.Dynamic = board.create("segment", js.Array(a, b), js.Dynamic.literal(
        strokeColor = color, strokeWidth = 3, dash = (if line.jump then 2 else 0), fixed = true
      ))
      segment.on("over", (_: js.Any) => hoverRelated(scene, index, pointObjects, angleObjects, board, active = true))
      segment.on("out", (_: js.Any) => hoverRelated(scene, index, pointObjects, angleObjects, board, active = false))
    }

    def createAngle(angle: RenderedAngle, index: Int): Unit = {
      val radius = math.max((bounds(2) - bounds(0)) / 18.0, 50)
      val start = Point(angle.vertex.x + Math.cos(Math.toRadians(angle.fromHeading)) * radius, angle.vertex.y + Math.sin(Math.toRadians(angle.fromHeading)) * radius)
      val endHeading = angle.fromHeading + angle.degrees
      val end = Point(angle.vertex.x + Math.cos(Math.toRadians(endHeading)) * radius, angle.vertex.y + Math.sin(Math.toRadians(endHeading)) * radius)

      def helper(point: Point[Double]): js.Dynamic = board.create("point", js.Array(point.x, point.y), js.Dynamic.literal(visible = false, fixed = true))

      val arc = board.create("angle", js.Array(helper(end), pointObject(angle.vertex), helper(start)), js.Dynamic.literal(
        name = "", radius = radius, fillColor = "#FFA420", strokeColor = "#FFA420", fillOpacity = 0.9, strokeOpacity = 0.9, fixed = true
      ))
      arc.on("over", (_: js.Any) => setAngleHover(arc, angle, active = true, board))
      arc.on("out", (_: js.Any) => setAngleHover(arc, angle, active = false, board))
      angleObjects(index) = arc
    }

    scene.lines.zipWithIndex.foreach { case (line, index) => createLine(line, index) }

    scene.angles.zipWithIndex.foreach { case (angle, index) => createAngle(angle, index) }
    board.update()
    board

  private def hoverRelated(scene: Scene, lineIndex: Int, points: collection.mutable.Map[Point[Double], js.Dynamic], angles: collection.mutable.Map[Int, js.Dynamic], board: js.Dynamic, active: Boolean): Unit =
    val line = scene.lines(lineIndex)
    List(line.start, line.end).foreach { p =>
      points.get(p).foreach(_.setAttribute(js.Dynamic.literal(
        name = (if active then s"(${format(p.x)}, ${format(p.y)})" else ""), fillOpacity = (if active then 1.0 else 0.1), strokeOpacity = (if active then 1.0 else 0.1)
      )))
    }
    scene.angles.zipWithIndex.filter { case (a, _) => a.lineBefore == lineIndex || a.lineAfter == lineIndex }.foreach { case (a, i) => angles.get(i).foreach(setAngleHover(_, a, active, board)) }
    board.update()

  private def setAngleHover(obj: js.Dynamic, angle: RenderedAngle, active: Boolean, board: js.Dynamic): Unit =
    obj.setAttribute(js.Dynamic.literal(name = (if active then s"${format(math.abs(angle.degrees))}°" else ""), fillOpacity = (if active then 1.0 else 0.9), strokeOpacity = (if active then 1.0 else 0.1)))
    board.update()

  private def sameLine(a: Point[Double], b: Point[Double], c: Point[Double], d: Point[Double], tolerance: Double): Boolean =
    (close(a, c, tolerance) && close(b, d, tolerance)) || (close(a, d, tolerance) && close(b, c, tolerance))

  private def close(a: Point[Double], b: Point[Double], tolerance: Double): Boolean =
    math.abs(a.x - b.x) <= tolerance && math.abs(a.y - b.y) <= tolerance

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
