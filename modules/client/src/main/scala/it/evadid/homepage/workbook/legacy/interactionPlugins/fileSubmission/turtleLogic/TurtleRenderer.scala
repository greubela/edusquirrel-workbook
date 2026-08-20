package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleLogic

import org.scalajs.dom
import org.scalajs.dom.html

import scala.math.*
import scala.scalajs.js

object TurtleRenderer {

  // Verified 1x1 transparent PNG (valid IDAT CRC).
  val transparentPngDataUrl =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGBgAAAABQABpfZFQAAAAABJRU5ErkJggg=="

  case class Segment(x1: Double, y1: Double, x2: Double, y2: Double)

  private case class TurtleState(
      x: Double,
      y: Double,
      headingDegrees: Double,
      penDown: Boolean,
      segments: List[Segment],
      clearFrom: Int
  )

  def renderToPngDataUrl(commands: List[TurtleXmlParser.Command]): String = {
    val segments = simulateSegments(commands)
    renderSegments(segments)
  }

  private[turtleLogic] def simulateSegments(commands: List[TurtleXmlParser.Command]): List[Segment] = {
    val initial = TurtleState(0.0, 0.0, headingDegrees = 90.0, penDown = true, segments = Nil, clearFrom = 0)
    val endState = runCommands(commands, initial)
    endState.segments.reverse.drop(endState.clearFrom)
  }

  private def runCommands(commands: List[TurtleXmlParser.Command], state: TurtleState): TurtleState = {
    commands.foldLeft(state) {
      case (s, TurtleXmlParser.Forward(distance)) =>
        val radians = (90.0 - s.headingDegrees) * Pi / 180.0
        val newX = s.x + cos(radians) * distance
        val newY = s.y + sin(radians) * distance
        val newSegment = Segment(s.x, s.y, newX, newY)
        s.copy(x = newX, y = newY, segments = if (s.penDown) newSegment :: s.segments else s.segments)

      case (s, TurtleXmlParser.TurnLeft(degrees)) =>
        s.copy(headingDegrees = s.headingDegrees - degrees)

      case (s, TurtleXmlParser.TurnRight(degrees)) =>
        s.copy(headingDegrees = s.headingDegrees + degrees)

      case (s, TurtleXmlParser.SetHeading(degrees)) =>
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        s.copy(headingDegrees = normalized)

      case (s, TurtleXmlParser.GotoXY(x, y)) =>
        val newSegment = Segment(s.x, s.y, x, y)
        s.copy(x = x, y = y, segments = if (s.penDown) newSegment :: s.segments else s.segments)

      case (s, TurtleXmlParser.ChangeYPosition(delta)) =>
        val newY = s.y + delta
        val newSegment = Segment(s.x, s.y, s.x, newY)
        s.copy(y = newY, segments = if (s.penDown) newSegment :: s.segments else s.segments)

      case (s, TurtleXmlParser.ArcRight(radius, degrees)) =>
        runArc(s, radius, degrees, clockwise = true)

      case (s, TurtleXmlParser.ArcLeft(radius, degrees)) =>
        runArc(s, radius, degrees, clockwise = false)

      case (s, TurtleXmlParser.Clear) =>
        s.copy(clearFrom = s.segments.length)

      case (s, TurtleXmlParser.ReceiveGo) =>
        s

      case (s, TurtleXmlParser.PenUp) =>
        s.copy(penDown = false)

      case (s, TurtleXmlParser.PenDown) =>
        s.copy(penDown = true)

      case (s, TurtleXmlParser.Repeat(times, body)) =>
        (0 until times).foldLeft(s) { (acc, _) => runCommands(body, acc) }

      case (s, TurtleXmlParser.IfThen(body)) =>
        runCommands(body, s)

      case (s, TurtleXmlParser.WhileLoop(body)) =>
        runCommands(body, s)
    }
  }

  private def runArc(state: TurtleState, radius: Double, degrees: Double, clockwise: Boolean): TurtleState = {
    if (!radius.isFinite || !degrees.isFinite || radius == 0.0 || degrees == 0.0) return state
    if (degrees < 0) return runArc(state, radius, -degrees, clockwise = !clockwise)

    val stitchLen = 10.0
    val turns = math.ceil(radius * (degrees / 360.0) * (6.283 / stitchLen)).toInt.max(1)
    val perTurnAngle = degrees / turns.toDouble

    (0 until turns).foldLeft(state) { (acc, _) =>
      val halfTurn = if (clockwise) perTurnAngle / 2.0 else -perTurnAngle / 2.0
      val stepLength = 2.0 * radius * sin((perTurnAngle * (Pi / 180.0)) / 2.0)
      val turned = runCommands(List(TurtleXmlParser.TurnRight(halfTurn)), acc)
      val moved = runCommands(List(TurtleXmlParser.Forward(stepLength)), turned)
      runCommands(List(TurtleXmlParser.TurnRight(halfTurn)), moved)
    }
  }

  private def renderSegments(segments: List[Segment]): String = {
    val maybeDocument = scala.util.Try(dom.document).toOption
    if (maybeDocument.isEmpty || maybeDocument.get == null) return transparentPngDataUrl

    val canvas = maybeDocument.get.createElement("canvas").asInstanceOf[html.Canvas]
    canvas.width = 512
    canvas.height = 512

    val rawContext = canvas.getContext("2d")
    if (rawContext == null) return transparentPngDataUrl

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
