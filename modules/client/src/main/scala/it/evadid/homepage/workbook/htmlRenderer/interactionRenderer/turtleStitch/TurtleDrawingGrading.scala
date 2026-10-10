package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.{TurtleDrawingComparison, TurtlePathBuilder}
import it.evadid.core.datastructures.vectorShapes.svg.TurtleDrawingComparison.{Segment, TurtleComparisonResult}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.{LineResult, RenderedLine, Scene}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic

object TurtleDrawingGrading {
  private val tolerance = 0.25
  private val maxCommands = 10000
  private val maxSamples = 20000L
  private val maxComparisons = 4000000L
  private val commandArity = Map(
    "forward" -> 1, "fd" -> 1, "backward" -> 1, "back" -> 1, "bk" -> 1,
    "right" -> 1, "rt" -> 1, "turn" -> 1, "turnright" -> 1, "turn_right" -> 1,
    "left" -> 1, "lt" -> 1, "turnleft" -> 1, "turn_left" -> 1,
    "penup" -> 0, "pen_up" -> 0, "pu" -> 0, "up" -> 0,
    "pendown" -> 0, "pen_down" -> 0, "pd" -> 0, "down" -> 0,
    "goto" -> 2, "gotoxy" -> 2, "goto_x_y" -> 2, "setpos" -> 2, "setposition" -> 2,
    "setx" -> 1, "set_x" -> 1, "setxposition" -> 1,
    "sety" -> 1, "set_y" -> 1, "setyposition" -> 1,
    "setheading" -> 1, "set_heading" -> 1, "seth" -> 1,
    "home" -> 0, "clear" -> 0, "clearscreen" -> 0, "reset" -> 0
  )

  private final case class Drawing(path: TurtlePathBuilder[Double], lines: List[Segment])

  def validateDrawing(commands: List[TurtleCommand[Double]]): Unit = {
    checkedDrawing(commands)
    ()
  }

  def compare(commands: List[TurtleCommand[Double]], target: TurtleGraphic): TurtleComparisonResult = {
    val actual = checkedDrawing(commands)
    val expected = checkedDrawing(target.toTurtleProgram.toList)
    def samples(lines: List[Segment]): Long = lines.foldLeft(0L) { (total, line) =>
      val count = math.ceil(line.length / (tolerance / 2.0)) + 1.0
      if !count.isFinite || count > maxSamples then maxSamples + 1
      else (total + count.toLong).min(maxSamples + 1)
    }
    val actualSamples = samples(actual.lines)
    val expectedSamples = samples(expected.lines)
    if actualSamples + expectedSamples > maxSamples ||
      actualSamples * expected.lines.size + expectedSamples * actual.lines.size > maxComparisons then
      throw IllegalArgumentException("Your drawing is too large to compare. Check the distances in your program.")
    TurtleDrawingComparison.compare(expected.path, actual.path, Some(tolerance))
  }

  def assessedScene(commands: List[TurtleCommand[Double]], target: TurtleGraphic): Scene =
    assessedScene(commands, target, compare(commands, target))

  def assessedScene(commands: List[TurtleCommand[Double]], target: TurtleGraphic, comparison: TurtleComparisonResult): Scene = {
    validateDrawing(commands)
    val expectedCommands = target.toTurtleProgram.toList
    val expected = checkedDrawing(expectedCommands)
    val scene = drawingScene(expectedCommands, expected)
    val missing = comparison.missing.toSet
    val lines = scene.lines.map { line =>
      line.copy(result = if missing.contains(Segment(line.start, line.end)) then LineResult.Missing else LineResult.Correct)
    }
    val extra = comparison.extra.map(line => RenderedLine(line.from, line.to, LineResult.Unexpected, jump = false))
    Scene(lines ++ extra, scene.angles)
  }

  def targetScene(target: TurtleGraphic): Scene = drawingScene(target.toTurtleProgram.toList)

  def drawingScene(commands: List[TurtleCommand[Double]]): Scene =
    drawingScene(commands, checkedDrawing(commands))

  private def drawingScene(commands: List[TurtleCommand[Double]], drawing: Drawing): Scene = {
    val trace = TurtleJsxGraphRenderer.buildScene(commands, List.empty[TurtleJsxGraphRenderer.LineToRender[Double]])
    val indexes = scala.collection.mutable.Map.empty[Int, Int]
    var next = 0
    trace.lines.zipWithIndex.foreach { (line, index) =>
      if !line.jump && math.hypot(line.end.x - line.start.x, line.end.y - line.start.y) > 1e-9 && next < drawing.lines.size then {
        val segment = drawing.lines(next)
        def close(a: Point[Double], b: Point[Double]): Boolean =
          math.abs(a.x - b.x) <= 1e-7 && math.abs(a.y - b.y) <= 1e-7
        if close(line.start, segment.from) && close(line.end, segment.to) then {
          indexes(index) = next
          next += 1
        }
      }
    }
    val angles = trace.angles.flatMap { angle =>
      for {
        before <- indexes.get(angle.lineBefore)
        after <- indexes.get(angle.lineAfter)
      } yield angle.copy(vertex = drawing.lines(before).to, lineBefore = before, lineAfter = after)
    }
    Scene(drawing.lines.map(line => RenderedLine(line.from, line.to, LineResult.Neutral, jump = false)), angles)
  }

  private def checkedDrawing(commands: List[TurtleCommand[Double]]): Drawing = {
    if commands.size > maxCommands then
      throw IllegalArgumentException("Your drawing is too large to compare. Check the distances in your program.")
    var path = TurtlePathBuilder[Double]()
    commands.foreach { command =>
      val name = command.name.trim.toLowerCase.replace('-', '_')
      if !commandArity.get(name).contains(command.args.size) || command.stringArgs.nonEmpty || command.args.exists(!_.isFinite) then
        throw IllegalArgumentException("This drawing uses commands that cannot be compared yet.")
      path = path.handleStringCommand(command)
      if !path.turtleState.x.isFinite || !path.turtleState.y.isFinite || !path.turtleState.headingDeg.isFinite then
        throw IllegalArgumentException("Your drawing exceeds the display range. Check its distances and angles.")
    }
    val lines = TurtleDrawingComparison.extractSegments(path)
    val points = lines.flatMap(line => List(line.from, line.to)) ++
      List(Point(path.turtleState.x, path.turtleState.y), Point(0.0, 0.0))
    val xs = points.map(_.x)
    val ys = points.map(_.y)
    val span = math.max(math.max(xs.max - xs.min, ys.max - ys.min), 1.0)
    val padding = span * 0.12
    if points.exists(point => !point.x.isFinite || !point.y.isFinite) ||
      List(xs.min - padding, xs.max + padding, ys.min - padding, ys.max + padding).exists(!_.isFinite) then
      throw IllegalArgumentException("Your drawing exceeds the display range. Check its distances and angles.")
    Drawing(path, lines)
  }
}
