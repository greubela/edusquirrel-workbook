package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleGradingLogic, TurtleGraphic}
import TurtleGradingLogic.{GradingLine, TurtleGraphicComparison, TurtleLineStatus}
import it.evadid.facades
import it.evadid.facades.JSXGraph
import org.scalajs.dom
import upickle.default.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.control.NonFatal


/** Renders and compares a turtle trace on a JSXGraph board.
 *
 * JSXGraph must be loaded by the host page (`JXG` must be available globally).
 * Scene coordinates follow TurtlePathBuilder's SVG convention (positive y points downwards).
 * They are converted to JSXGraph's upward-positive coordinates when displayed.
 */
object TurtleJsxGraphRenderer:

  def render[T: Fractional](program: Signal[Seq[TurtleCommand[T]]], graphic: TurtleGraphic): Modifier[HtmlElement] =
    render(program.map(commands => buildScene(commands, graphic)), "Turtle drawing")

  /** Stitch reproduction accepts any pen-up route between the drawn segments. */
  def renderStrokes[T: Fractional](program: Signal[Seq[TurtleCommand[T]]], expected: TurtleGraphic): Modifier[HtmlElement] =
    render(program.map(commands => buildScene(commands, expected, 1e-7, gradeJumps = false)), "Turtle drawing")

  def render[T: Fractional](program: Seq[TurtleCommand[T]], expected: TurtleGraphic): Element =
    render(buildScene(program, expected), "Turtle drawing")

  def render[T: Fractional](program: Seq[TurtleCommand[T]], expected: Seq[LineToRender[T]]): Element =
    render(buildScene(program, expected), "Turtle drawing")

  /** Inserts one panel per scene. Each new scene replaces the whole panel with a fresh board: a reused JSXGraph
    * board does not return to its default view (zoom, bounds). An unchanged scene keeps its panel.
    */
  def render(scenes: Signal[Scene], title: String): Modifier[HtmlElement] =
    child <-- scenes.distinct.map(scene => render(scene, title))

  /** A panel drawing `scene` on its own board, created once the library is loaded and released on unmount. */
  def render(scene: Scene, title: String): Element = {
    val board = Var(Option.empty[JSXGraph.Board])
    def show(container: dom.html.Div): Unit =
      scala.util.Try(render(container, scene, title)) match {
        case scala.util.Success(created) =>
          board.set(Some(created))
          container.classList.remove("turtle-gradig-panel--unavailable")
          container.setAttribute("aria-busy", "false")
        case scala.util.Failure(error) => showError(container, error)
      }
    div(
      cls := "turtle-gradig-panel",
      aria.busy := true,
      onMountBind(context => libraryLoaded --> {
        case Some(scala.util.Success(_)) => if board.now().isEmpty then show(context.thisNode.ref)
        case Some(scala.util.Failure(error)) => showError(context.thisNode.ref, error)
        case None => ()
      }),
      onUnmountCallback { _ =>
        board.now().foreach(JSXGraph.free)
        board.set(None)
      }
    )
  }

  /** Loads JSXGraph on demand; the signal completes with the loading outcome. */
  private def libraryLoaded: Signal[Option[scala.util.Try[Unit]]] = {
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    if facades.JSXGraph.available then Val(Some(scala.util.Success(())))
    else scala.util.Try(facades.JSXGraph.preload()).fold(
      error => Val(Some(scala.util.Failure(error))),
      loading => Signal.fromFuture(loading.transform(outcome => scala.util.Success(outcome))))
  }

  private def showError(container: dom.html.Div, error: Throwable): Unit = {
    container.setAttribute("aria-busy", "false")
    container.classList.add("turtle-gradig-panel--unavailable")
    container.textContent = error match {
      case _: IllegalArgumentException => "This drawing is outside the supported display range."
      case _ => "The drawing preview is unavailable. Try reloading the page."
    }
  }

  /* Factories */



  /* details */

  /** A line segment used as the expected result of a turtle exercise. */
  final case class LineToRender[T: Fractional](start: Point[T], end: Point[T], jump: Boolean = false)

  object LineToRender {
    given [T: Fractional: ReadWriter]: ReadWriter[LineToRender[T]] =
      upickle.default.readwriter[(Point[T], Point[T], Boolean)].bimap(
        value => (value.start, value.end, value.jump),
        value => LineToRender(value._1, value._2, value._3))
  }


  enum LineResult derives ReadWriter:
    case Correct, Unexpected, Missing, Neutral
    /** Step-by-step replay: the segment drawn by the current step, and segments of later steps. */
    case Current, Upcoming

  final case class RenderedLine(start: Point[Double], end: Point[Double], result: LineResult, jump: Boolean) derives ReadWriter

  final case class RenderedAngle(vertex: Point[Double], fromHeading: Double, degrees: Double, lineBefore: Int, lineAfter: Int) derives ReadWriter

  /** `marker` shows the turtle's position, e.g. after the current step of a replay. `frame` lists points that
    * stay visible whatever is drawn (e.g. the target drawing), so a partial or empty program does not zoom in.
    */
  final case class Scene(lines: Seq[RenderedLine], angles: Seq[RenderedAngle], marker: Option[Point[Double]] = None,
      frame: Seq[Point[Double]] = Nil) derives ReadWriter

  private[turtleStitch] final case class AngleSector(first: Point[Double], last: Point[Double], degrees: Double) derives upickle.default.ReadWriter

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

  private def graphic[T: Fractional](program: Seq[TurtleCommand[T]]): TurtleGraphic = {
    val numeric = summon[Fractional[T]]
    TurtleGraphic.TurtleGraphicProgram(program.map(command =>
      command.mapNumbers(numeric.toDouble)))
  }

  private def renderedLines(lines: Seq[GradingLine]): Seq[RenderedLine] = lines.map { graded =>
    val result = graded.status match
      case TurtleLineStatus.CORRECT => LineResult.Correct
      case TurtleLineStatus.EXPECTED_BUT_MISSING => LineResult.Missing
      case TurtleLineStatus.EXISTING_BUT_UNEXPECTED => LineResult.Unexpected
    RenderedLine(graded.line.start, graded.line.end, result, graded.jump)
  }

  private def renderedAngles(angles: Seq[TurtleGraphic.Angle]): Seq[RenderedAngle] = angles.map { angle =>
    RenderedAngle(angle.vertex, angle.fromHeading, angle.degrees, angle.lineBefore, angle.lineAfter)
  }.toList

  /** JSXGraph projects the shared-core geometry and grading result into display objects. */
  def buildScene[T: Fractional](program: Seq[TurtleCommand[T]], expected: Seq[LineToRender[T]], tolerance: Double = 1e-7, gradeJumps: Boolean = true): Scene = {
    val actual = graphic(program)
    val targets = expected.map(line => TurtleGraphic.Movement(line.start.toDouble, line.end.toDouble, line.jump))
    Scene(renderedLines(TurtleGradingLogic.compareLines(actual.renderMovements, targets, tolerance, gradeJumps)),
      renderedAngles(actual.renderAngles))
  }

  def buildScene[T: Fractional](program: Seq[TurtleCommand[T]], expected: TurtleGraphic): Scene =
    buildScene(program, expected, 1e-7)

  def buildScene[T: Fractional](program: Seq[TurtleCommand[T]], expected: TurtleGraphic, tolerance: Double): Scene =
    buildScene(program, expected, tolerance, gradeJumps = true)

  def buildScene[T: Fractional](program: Seq[TurtleCommand[T]], expected: TurtleGraphic, tolerance: Double, gradeJumps: Boolean): Scene =
    buildScene(graphic(program), expected, tolerance, gradeJumps)

  private[turtleStitch] def buildScene(comparison: TurtleGraphicComparison): Scene =
    Scene(renderedLines(comparison.difference), renderedAngles(comparison.angles))

  private[turtleStitch] def buildScene(actual: TurtleGraphic, expected: TurtleGraphic, tolerance: Double, gradeJumps: Boolean): Scene = {
    val comparison = TurtleGraphicComparison(actual, expected, tolerance, gradeJumps)
    buildScene(comparison)
  }

  /** Creates the JSXGraph board inside `container` and returns the board object. */
  def render[T: Fractional](container: dom.html.Div, program: Seq[TurtleCommand[T]], expected: Seq[LineToRender[T]]): JSXGraph.Board =
    render(container, buildScene(program, expected), "Turtle drawing")

  /** Creates the JSXGraph board using the expected graphic for both comparison and angle overlays. */
  def render[T: Fractional](container: dom.html.Div, program: Seq[TurtleCommand[T]], expected: TurtleGraphic): JSXGraph.Board =
    render(container, buildScene(program, expected), "Turtle drawing")

  def render(container: dom.html.Div, scene: Scene, title: String): JSXGraph.Board = {
    val bounds = fitToContainer(displayBounds(scene), container)
    container.classList.add("turtle-gradig-panel")
    // SVG tick labels avoid a forced HTML layout for each label on every board update.
    val axisOptions = js.Dynamic.literal(ticks = js.Dynamic.literal(label = js.Dynamic.literal(display = "internal")))
    val board = JSXGraph.board(container, js.Dynamic.literal(
      title = Option(title).map(_.trim).filter(_.nonEmpty).getOrElse("Turtle drawing"),
      renderer = "svg", boundingbox = js.Array(bounds(0), bounds(1), bounds(2), bounds(3)), axis = true,
      defaultAxes = js.Dynamic.literal(x = axisOptions, y = axisOptions),
      keepaspectratio = true, showCopyright = false
    )).createLoadedInstance()
    try {
      draw(board, scene, bounds)
      board
    } catch
      case NonFatal(error) =>
        JSXGraph.free(board)
        throw error
  }

  /** JSXGraph bounds of the scene; rejects coordinates the board cannot display. */
  private def displayBounds(scene: Scene): Array[Double] =
    val points = scene.lines.flatMap(line => List(line.start, line.end)) ++ scene.marker ++ scene.frame
    if points.exists(point => !point.x.isFinite || !point.y.isFinite) then
      throw IllegalArgumentException("The drawing contains non-finite coordinates.")
    val bounds = boundingBox(points.map(p => Point(p.x, -p.y)))
    if bounds.exists(!_.isFinite) || !((bounds(2) - bounds(0)).isFinite && (bounds(1) - bounds(3)).isFinite) ||
      bounds(2) <= bounds(0) || bounds(1) <= bounds(3) then
      throw IllegalArgumentException("The drawing exceeds the supported display range.")
    bounds

  /** With `keepaspectratio`, JSXGraph fits only one axis of the bounding box to the board; a drawing whose shape
    * differs from the board's (e.g. wide on a square board) is cut off and looks zoomed in. Widening the box to the
    * board's aspect ratio, around the same centre, shows the whole drawing. An unmeasured board counts as square.
    */
  private[turtleStitch] def fitToContainer(bounds: Array[Double], container: dom.html.Div): Array[Double] =
    def dimension(name: String): Double = container.asInstanceOf[js.Dynamic].selectDynamic(name).asInstanceOf[Any] match
      case value: Double if value.isFinite && value > 0 => value
      case _ => 0.0
    val (width, height) = (dimension("clientWidth"), dimension("clientHeight"))
    val ratio = if width > 0 && height > 0 then width / height else 1.0
    fitToRatio(bounds, ratio)

  /** `bounds` (left, top, right, bottom) widened along one axis so that width / height equals `ratio`. */
  private[turtleStitch] def fitToRatio(bounds: Array[Double], ratio: Double): Array[Double] =
    val (dx, dy) = (bounds(2) - bounds(0), bounds(1) - bounds(3))
    val (centreX, centreY) = ((bounds(0) + bounds(2)) / 2, (bounds(1) + bounds(3)) / 2)
    val (width, height) = if dx / dy >= ratio then (dx, dx / ratio) else (dy * ratio, dy)
    Array(centreX - width / 2, centreY + height / 2, centreX + width / 2, centreY - height / 2)

  private val hoverLabel = js.Dynamic.literal(display = "internal", cssClass = "turtle-hover-label", highlightCssClass = "turtle-hover-label")

  /** Creates the scene's objects in one suspended update. Angles are filled curves: JSXGraph angle objects
    * end the suspended update and redraw the whole board for every angle.
    */
  private def draw(board: JSXGraph.Board, scene: Scene, bounds: Array[Double]): Unit =
    // Dense embroidery targets otherwise redraw the entire board after each object.
    val canBatch = js.typeOf(board.asInstanceOf[js.Dynamic].selectDynamic("suspendUpdate")) == "function" &&
      js.typeOf(board.asInstanceOf[js.Dynamic].selectDynamic("unsuspendUpdate")) == "function"
    if canBatch then board.suspendUpdate()

    val pointObjects: Map[Point[Double], js.Dynamic] =
      scene.lines.flatMap(line => List(line.start, line.end)).distinct.map { point =>
        point -> board.create("point", js.Array(point.x, -point.y), js.Dynamic.literal(
          name = "", fixed = true, showInfobox = false, label = hoverLabel,
          cssClass = "turtle-point", highlightCssClass = "turtle-point is-emphasized"))
      }.toMap

    val segments = scene.lines.map { line =>
      val classes = lineClasses(line)
      board.create("segment", js.Array(pointObjects(line.start), pointObjects(line.end)), js.Dynamic.literal(
        cssClass = classes, highlightCssClass = classes, fixed = true))
    }

    // Hundreds of repeated angle sectors obscure dense stitch patterns. Keep one representative of each turn there.
    val angleEntries = scene.angles.zipWithIndex
    val visibleAngles = if angleEntries.size <= 128 then angleEntries
      else angleEntries.groupBy { case (angle, _) => math.round(angle.degrees * 1e6) }
        .values.map(_.head).toList.sortBy(_._2)
    val sectors: Map[Int, (js.Dynamic, AngleSector, Double)] = visibleAngles.map { case (angle, index) =>
      def length(line: RenderedLine): Double = math.hypot(line.end.x - line.start.x, line.end.y - line.start.y)
      val radius = math.min((bounds(2) - bounds(0)) / 18.0,
        math.min(length(scene.lines(angle.lineBefore)), length(scene.lines(angle.lineAfter))) / 4.0)
      val sector = angleSector(scene, angle, radius)
      val outline = sectorOutline(Point(angle.vertex.x, -angle.vertex.y), sector, radius)
      index -> (board.create("curve", js.Array(outline.map(_.x).toJSArray, outline.map(_.y).toJSArray), js.Dynamic.literal(
        // Hovering anywhere inside the sector, not only on its outline.
        cssClass = "turtle-angle", highlightCssClass = "turtle-angle", fixed = true, withLabel = false, hasInnerPoints = true)), sector, radius)
    }.toMap

    scene.marker.foreach { position =>
      board.create("point", js.Array(position.x, -position.y), js.Dynamic.literal(
        name = "", fixed = true, showInfobox = false, withLabel = false, size = 5,
        cssClass = "turtle-marker", highlightCssClass = "turtle-marker"))
    }

    val labels = Var(Map.empty[Int, js.Dynamic])
    def hoverAngle(index: Int, active: Boolean): Unit = sectors.get(index).foreach { (curve, sector, radius) =>
      curve.setAttribute(js.Dynamic.literal(cssClass = if active then "turtle-angle is-emphasized" else "turtle-angle"))
      labels.now().get(index) match {
        case Some(label) => label.setAttribute(js.Dynamic.literal(visible = active))
        case None if active =>
          // Created on first hover: measuring many text labels while drawing is costly.
          val position = sectorLabelPosition(Point(scene.angles(index).vertex.x, -scene.angles(index).vertex.y), sector, radius)
          labels.update(_.updated(index, board.create("text", js.Array(position.x, position.y, s"${format(sector.degrees)}°"),
            js.Dynamic.literal(fixed = true, display = "internal", anchorX = "middle", anchorY = "middle",
              cssClass = "turtle-hover-label", highlightCssClass = "turtle-hover-label"))))
        case None => ()
      }
    }
    def hoverLine(index: Int, active: Boolean): Unit = {
      val line = scene.lines(index)
      List(line.start, line.end).foreach(point => pointObjects.get(point).foreach(_.setAttribute(js.Dynamic.literal(
        name = if active then s"(${format(point.x)}, ${format(point.y)})" else "",
        cssClass = if active then "turtle-point is-emphasized" else "turtle-point"))))
      scene.angles.zipWithIndex.filter((angle, _) => angle.lineBefore == index || angle.lineAfter == index)
        .foreach((_, angleIndex) => hoverAngle(angleIndex, active))
    }
    segments.zipWithIndex.foreach { (segment, index) =>
      segment.on("over", (_: js.Any) => { hoverLine(index, active = true); board.update() })
      segment.on("out", (_: js.Any) => { hoverLine(index, active = false); board.update() })
    }
    sectors.foreach { case (index, (curve, _, _)) =>
      curve.on("over", (_: js.Any) => { hoverAngle(index, active = true); board.update() })
      curve.on("out", (_: js.Any) => { hoverAngle(index, active = false); board.update() })
    }

    if canBatch then board.unsuspendUpdate() else board.update()

  /** Closed outline of an angle sector in JSXGraph coordinates: vertex, arc from `first` to `last`, vertex. */
  private[turtleStitch] def sectorOutline(vertex: Point[Double], sector: AngleSector, radius: Double): List[Point[Double]] =
    val start = math.atan2(sector.first.y - vertex.y, sector.first.x - vertex.x)
    val sweep = math.toRadians(sector.degrees)
    val steps = math.max(8, math.ceil(sector.degrees / 5.0).toInt)
    val arc = (0 to steps).map { step =>
      val angle = start + sweep * step / steps
      Point(vertex.x + radius * math.cos(angle), vertex.y + radius * math.sin(angle))
    }.toList
    vertex :: arc ::: List(vertex)

  /** The angle's label sits outside its arc, on the sector's bisector. */
  private[turtleStitch] def sectorLabelPosition(vertex: Point[Double], sector: AngleSector, radius: Double): Point[Double] =
    val middle = math.atan2(sector.first.y - vertex.y, sector.first.x - vertex.x) + math.toRadians(sector.degrees) / 2
    Point(vertex.x + 1.6 * radius * math.cos(middle), vertex.y + 1.6 * radius * math.sin(middle))

  /** CSS classes of a rendered segment: its result and, for pen-up travel, the dashed jump style. */
  private[htmlRenderer] def lineClasses(line: RenderedLine): String =
    val resultClass = line.result match
      case LineResult.Correct => "turtle-line--correct"
      case LineResult.Unexpected => "turtle-line--unexpected"
      case LineResult.Missing => "turtle-line--missing"
      case LineResult.Neutral => "turtle-line--neutral"
      case LineResult.Current => "turtle-line--current"
      case LineResult.Upcoming => "turtle-line--upcoming"
    s"turtle-line $resultClass" + (if line.jump then " turtle-line--jump" else "")

  private def format(value: Double): String =
    val rounded = math.rint(value * 1000.0) / 1000.0
    if rounded == rounded.toLong then rounded.toLong.toString else rounded.toString

  private def boundingBox(points: Seq[Point[Double]]): Array[Double] =
    if points.isEmpty then Array(-10.0, 10.0, 10.0, -10.0)
    else
      val xs = points.map(_.x);
      val ys = points.map(_.y)
      val span = math.max(math.max(xs.max - xs.min, ys.max - ys.min), 1.0)
      val padding = span * 0.12
      Array(xs.min - padding, ys.max + padding, xs.max + padding, ys.min - padding)
