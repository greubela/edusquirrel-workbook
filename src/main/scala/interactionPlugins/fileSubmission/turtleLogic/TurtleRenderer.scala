package interactionPlugins.fileSubmission.turtleLogic

import org.scalajs.dom
import org.scalajs.dom.html
import scala.scalajs.js

import scala.math.{Pi, cos, max, min, sin}

object TurtleRenderer {

  private val TransparentPngDataUrl =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO8B5hoAAAAASUVORK5CYII="

  case class Segment(x1: Double, y1: Double, x2: Double, y2: Double)

  private case class TurtleState(
      x: Double,
      y: Double,
      headingDegrees: Double,
      penDown: Boolean,
      segments: List[Segment]
  )

  def renderToPngDataUrl(commands: List[TurtleXmlParser.Command]): String = {
    val segments = execute(commands)
    renderSegments(segments)
  }

  private def execute(commands: List[TurtleXmlParser.Command]): List[Segment] = {
    val initial = TurtleState(0.0, 0.0, headingDegrees = 0.0, penDown = true, segments = Nil)
    val endState = runCommands(commands, initial)
    endState.segments.reverse
  }

  private def runCommands(commands: List[TurtleXmlParser.Command], state: TurtleState): TurtleState = {
    commands.foldLeft(state) {
      case (s, TurtleXmlParser.Forward(distance)) =>
        val radians = s.headingDegrees * Pi / 180.0
        val newX = s.x + cos(radians) * distance
        val newY = s.y + sin(radians) * distance
        val newSegment = Segment(s.x, s.y, newX, newY)
        s.copy(x = newX, y = newY, segments = if (s.penDown) newSegment :: s.segments else s.segments)

      case (s, TurtleXmlParser.TurnLeft(degrees)) =>
        s.copy(headingDegrees = s.headingDegrees + degrees)

      case (s, TurtleXmlParser.TurnRight(degrees)) =>
        s.copy(headingDegrees = s.headingDegrees - degrees)

      case (s, TurtleXmlParser.PenUp) =>
        s.copy(penDown = false)

      case (s, TurtleXmlParser.PenDown) =>
        s.copy(penDown = true)

      case (s, TurtleXmlParser.Repeat(times, body)) =>
        (0 until times).foldLeft(s) { (acc, _) => runCommands(body, acc) }
    }
  }

  private def renderSegments(segments: List[Segment]): String = {
    val hasDocument = scala.util.Try(js.Dynamic.global.selectDynamic("document")).toOption
      .exists(d => !(js.isUndefined(d) || d == null))
    if (!hasDocument) return TransparentPngDataUrl

    val canvas = dom.document.createElement("canvas").asInstanceOf[html.Canvas]
    canvas.width = 512
    canvas.height = 512

    val rawContext = canvas.getContext("2d")
    if (rawContext == null) return TransparentPngDataUrl

    val ctx = rawContext.asInstanceOf[dom.CanvasRenderingContext2D]
    ctx.fillStyle = "white"
    ctx.fillRect(0, 0, canvas.width, canvas.height)

    if (segments.nonEmpty) {
      val minX = segments.foldLeft(Double.PositiveInfinity)((acc, s) => min(acc, min(s.x1, s.x2)))
      val maxX = segments.foldLeft(Double.NegativeInfinity)((acc, s) => max(acc, max(s.x1, s.x2)))
      val minY = segments.foldLeft(Double.PositiveInfinity)((acc, s) => min(acc, min(s.y1, s.y2)))
      val maxY = segments.foldLeft(Double.NegativeInfinity)((acc, s) => max(acc, max(s.y1, s.y2)))

      val width = max(maxX - minX, 1.0)
      val height = max(maxY - minY, 1.0)
      val padding = 24.0
      val scale = min((canvas.width - 2 * padding) / width, (canvas.height - 2 * padding) / height)

      def toCanvasX(x: Double): Double =
        (x - minX) * scale + (canvas.width - width * scale) / 2.0

      def toCanvasY(y: Double): Double =
        canvas.height - ((y - minY) * scale + (canvas.height - height * scale) / 2.0)

      ctx.beginPath()
      ctx.lineWidth = 2
      ctx.lineCap = "round"
      ctx.strokeStyle = "#222"

      segments.foreach { segment =>
        ctx.moveTo(toCanvasX(segment.x1), toCanvasY(segment.y1))
        ctx.lineTo(toCanvasX(segment.x2), toCanvasY(segment.y2))
      }
      ctx.stroke()
    }

    canvas.toDataURL("image/png")
  }
}
