package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.{TurtleDrawingComparison, TurtlePathBuilder}
import it.evadid.core.datastructures.vectorShapes.svg.TurtleDrawingComparison.{Segment, TurtleComparisonResult}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.{LineResult, RenderedAngle, RenderedLine, Scene}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog

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
    "home" -> 0, "clear" -> 0, "clearscreen" -> 0, "reset" -> 0,
    "arc" -> 2, "arcleft" -> 2, "arc_left" -> 2, "arcright" -> 2, "arc_right" -> 2, "dot" -> 1
  ).map((name, arity) => name -> Set(arity)) + ("circle" -> Set(1, 2))

  private final case class Drawing(graphic: TurtleGraphic, path: TurtlePathBuilder[Double],
      lines: List[Segment], lineIndexes: Map[Int, Int])
  final case class Assessment(matches: Boolean, scene: Scene)

  def assess(commands: List[TurtleCommand[Double]], target: TurtleGraphic, policy: TurtleDrawingPolicy): Assessment = policy match {
    case TurtleDrawingPolicy.Coverage =>
      val result = compare(commands, target)
      Assessment(result.matches, assessedScene(commands, target, result))
    case _ =>
      val actual = checkedDrawing(commands)
      val expected = checkedDrawing(target.toTurtleProgram.toList)
      if actual.graphic.renderMovements.size.toLong * expected.graphic.renderMovements.size > maxComparisons then tooLarge()
      val scene = TurtleJsxGraphRenderer.buildScene(actual.graphic, expected.graphic, 1e-7,
        gradeJumps = policy == TurtleDrawingPolicy.Segments)
      Assessment(scene.lines.forall(_.result == LineResult.Correct), scene)
  }

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
      actualSamples * expected.lines.size + expectedSamples * actual.lines.size > maxComparisons then tooLarge()
    TurtleDrawingComparison.compare(expected.path, actual.path, Some(tolerance))
  }

  def assessedScene(commands: List[TurtleCommand[Double]], target: TurtleGraphic): Scene =
    assessedScene(commands, target, compare(commands, target))

  def assessedScene(commands: List[TurtleCommand[Double]], target: TurtleGraphic, comparison: TurtleComparisonResult): Scene = {
    validateDrawing(commands)
    val scene = drawingScene(checkedDrawing(target.toTurtleProgram.toList))
    val missing = comparison.missing.toSet
    val lines = scene.lines.map { line =>
      line.copy(result = if missing.contains(Segment(line.start, line.end)) then LineResult.Missing else LineResult.Correct)
    }
    val extra = comparison.extra.map(line => RenderedLine(line.from, line.to, LineResult.Unexpected, jump = false))
    Scene(lines ++ extra, scene.angles)
  }

  def targetScene(target: TurtleGraphic): Scene = drawingScene(checkedDrawing(target.toTurtleProgram.toList))

  def targetScene(target: TurtleGraphic, policy: TurtleDrawingPolicy): Scene = {
    val drawing = checkedDrawing(target.toTurtleProgram.toList)
    if policy == TurtleDrawingPolicy.Coverage then drawingScene(drawing)
    else Scene(drawing.graphic.renderMovements.map(movement =>
      RenderedLine(movement.start, movement.end, LineResult.Neutral, movement.jump)).toList,
      drawing.graphic.renderAngles.map(angle =>
        RenderedAngle(angle.vertex, angle.fromHeading, angle.degrees, angle.lineBefore, angle.lineAfter)).toList)
  }

  def drawingScene(commands: List[TurtleCommand[Double]]): Scene = drawingScene(checkedDrawing(commands))

  private def drawingScene(drawing: Drawing): Scene = {
    val angles = drawing.graphic.renderAngles.flatMap { angle =>
      for {
        before <- drawing.lineIndexes.get(angle.lineBefore)
        after <- drawing.lineIndexes.get(angle.lineAfter)
      } yield RenderedAngle(angle.vertex, angle.fromHeading, angle.degrees, before, after)
    }.toList
    Scene(drawing.lines.map(line => RenderedLine(line.from, line.to, LineResult.Neutral, jump = false)), angles)
  }

  private def tooLarge(): Nothing =
    throw IllegalArgumentException("Your drawing is too large to compare. Check the distances in your program.")

  private def checkedDrawing(commands: List[TurtleCommand[Double]]): Drawing = {
    if commands.size > maxCommands then tooLarge()
    var traceWork = 0L
    commands.foreach { command =>
      val name = command.name.trim.toLowerCase.replace('-', '_')
      val geometry = commandArity.get(name).exists(_.contains(command.args.size)) && command.stringArgs.isEmpty
      val decoration = SnapTurtleCatalog.primitiveByPythonName.get(name).exists { primitive =>
        Set(SnapTurtleCatalog.PaletteTab.Pen, SnapTurtleCatalog.PaletteTab.Control).contains(primitive.tab) &&
          command.args.size + command.stringArgs.size == primitive.arity
      } || Set("color", "pencolor", "setcolor").contains(name) && command.args.size == 3 && command.stringArgs.isEmpty ||
        Set("showturtle", "st", "hideturtle", "ht").contains(name) && command.args.isEmpty && command.stringArgs.isEmpty
      if (!geometry && !decoration) || command.args.exists(!_.isFinite) then
        throw IllegalArgumentException("This drawing uses commands that cannot be compared yet.")
      val work = name match {
        case "circle" | "arc" | "arcleft" | "arc_left" | "arcright" | "arc_right" =>
          val steps = math.max(8L, math.round(math.abs(command.args.lift(1).getOrElse(360.0)) / 10.0))
          if steps > 10000 then tooLarge()
          steps
        case "dot" => 256L
        case _ => if geometry then 1L else 0L
      }
      traceWork += work
      if traceWork > maxSamples then tooLarge()
    }
    val graphic = TurtleGraphic.TurtleGraphicProgram(commands)
    val movements = graphic.renderMovements.toList
    if movements.size > maxSamples then tooLarge()
    val selected = movements.zipWithIndex.filter { (movement, _) =>
      !movement.jump && math.hypot(movement.end.x - movement.start.x, movement.end.y - movement.start.y) > 1e-9
    }
    val lines = selected.map((movement, _) => Segment(movement.start, movement.end))
    val lineIndexes = selected.zipWithIndex.map { case ((_, original), rendered) => original -> rendered }.toMap
    val points = movements.flatMap(movement => List(movement.start, movement.end)) :+ Point(0.0, 0.0)
    val xs = points.map(_.x)
    val ys = points.map(_.y)
    val span = math.max(math.max(xs.max - xs.min, ys.max - ys.min), 1.0)
    val padding = span * 0.12
    if points.exists(point => !point.x.isFinite || !point.y.isFinite) ||
      List(xs.min - padding, xs.max + padding, ys.min - padding, ys.max + padding).exists(!_.isFinite) then
      throw IllegalArgumentException("Your drawing exceeds the display range. Check its distances and angles.")
    val path = lines.foldLeft(TurtlePathBuilder[Double]()) { (builder, line) =>
      builder.penUp().goto(line.from.x, line.from.y).penDown().goto(line.to.x, line.to.y)
    }
    Drawing(graphic, path, lines, lineIndexes)
  }
}
