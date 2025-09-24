package interactionPlugins.turtleEnvironment

import scala.collection.mutable.ListBuffer

case class TurtleExecutionResult(
  lines: List[TurtleLineSegment],
  width: Double,
  height: Double,
  offset: TurtlePoint
) {
  def toSvg(strokeColor: String = "#222", strokeWidth: Double = 3.0): String =
    TurtleSvgRenderer.render(lines.map(line => ColoredTurtleLine(line, strokeColor)), width, height, strokeWidth)

  def toColoredSvg(coloredLines: List[ColoredTurtleLine], strokeWidth: Double = 3.0): String =
    TurtleSvgRenderer.render(coloredLines, width, height, strokeWidth)
}

object TurtleProgramExecutor {

  private val MaxLoopIterations = 1000

  private sealed trait ExecutableInstruction

  private object ExecutableInstruction {
    case class Simple(command: TurtleCommand) extends ExecutableInstruction
    case class Repeat(times: Int, body: List[ExecutableInstruction]) extends ExecutableInstruction
    case class If(expectPenDown: Boolean, body: List[ExecutableInstruction]) extends ExecutableInstruction
    case class While(expectPenDown: Boolean, body: List[ExecutableInstruction]) extends ExecutableInstruction
  }

  private enum ControlEnd {
    case Repeat, If, While
  }

  def execute(program: TurtleProgramState): TurtleExecutionResult = {
    var position = TurtlePoint(0, 0)
    var heading = 0.0 // 0° -> facing east
    var penDown = true
    val lines = ListBuffer.empty[TurtleLineSegment]

    def forward(distance: Double): Unit = {
      val radians = math.toRadians(heading)
      val delta = TurtlePoint(math.cos(radians) * distance, math.sin(radians) * distance)
      val next = position + delta
      if (penDown) lines += TurtleLineSegment(position, next)
      position = next
    }

    def runInstructions(instructions: List[ExecutableInstruction]): Unit = {
      instructions.foreach {
        case ExecutableInstruction.Simple(command) =>
          command match {
            case TurtleCommand.Forward(distance) => forward(distance)
            case TurtleCommand.TurnRight(angle)  => heading -= angle
            case TurtleCommand.TurnLeft(angle)   => heading += angle
            case TurtleCommand.PenUp             => penDown = false
            case TurtleCommand.PenDown           => penDown = true
            case TurtleCommand.TurnAround        => heading += 180
            case TurtleCommand.WhenProgramStarted => ()
            case _ => ()
          }
        case ExecutableInstruction.Repeat(times, body) =>
          val safeTimes = math.max(0, times)
          var iteration = 0
          while (iteration < safeTimes) {
            runInstructions(body)
            iteration += 1
          }
        case ExecutableInstruction.If(expectPenDown, body) =>
          if (penDown == expectPenDown) {
            runInstructions(body)
          }
        case ExecutableInstruction.While(expectPenDown, body) =>
          var iteration = 0
          while (iteration < MaxLoopIterations && penDown == expectPenDown) {
            runInstructions(body)
            iteration += 1
          }
      }
    }

    val instructions = buildInstructions(program.commands)
    runInstructions(instructions)

    val (normalized, width, height, offset) = normalize(lines.toList)
    TurtleExecutionResult(normalized, width, height, offset)
  }

  private def buildInstructions(commands: List[TurtleCommand]): List[ExecutableInstruction] = {
    import ExecutableInstruction.*

    def parse(fromIndex: Int, terminator: Option[ControlEnd]): (List[ExecutableInstruction], Int) = {
      val buffer = ListBuffer.empty[ExecutableInstruction]
      var index = fromIndex
      while (index < commands.length) {
        commands(index) match {
          case TurtleCommand.WhenProgramStarted =>
            index += 1
          case command @ TurtleCommand.Forward(_) =>
            buffer += Simple(command)
            index += 1
          case command @ TurtleCommand.TurnRight(_) =>
            buffer += Simple(command)
            index += 1
          case command @ TurtleCommand.TurnLeft(_) =>
            buffer += Simple(command)
            index += 1
          case command @ TurtleCommand.PenUp =>
            buffer += Simple(command)
            index += 1
          case command @ TurtleCommand.PenDown =>
            buffer += Simple(command)
            index += 1
          case command @ TurtleCommand.TurnAround =>
            buffer += Simple(command)
            index += 1
          case TurtleCommand.Repeat(times) =>
            val (body, nextIndex) = parse(index + 1, Some(ControlEnd.Repeat))
            buffer += Repeat(times, body)
            index = nextIndex
          case TurtleCommand.EndRepeat =>
            if (terminator.contains(ControlEnd.Repeat)) {
              return (buffer.toList, index + 1)
            }
            index += 1
          case TurtleCommand.IfPenState(expectPenDown) =>
            val (body, nextIndex) = parse(index + 1, Some(ControlEnd.If))
            buffer += If(expectPenDown, body)
            index = nextIndex
          case TurtleCommand.EndIf =>
            if (terminator.contains(ControlEnd.If)) {
              return (buffer.toList, index + 1)
            }
            index += 1
          case TurtleCommand.WhilePenState(expectPenDown) =>
            val (body, nextIndex) = parse(index + 1, Some(ControlEnd.While))
            buffer += While(expectPenDown, body)
            index = nextIndex
          case TurtleCommand.EndWhile =>
            if (terminator.contains(ControlEnd.While)) {
              return (buffer.toList, index + 1)
            }
            index += 1
        }
      }
      (buffer.toList, index)
    }

    val (instructions, _) = parse(0, None)
    instructions
  }

  private def normalize(lines: List[TurtleLineSegment]): (List[TurtleLineSegment], Double, Double, TurtlePoint) = {
    if (lines.isEmpty) {
      val origin = TurtlePoint(0, 0)
      return (lines, 200, 200, origin)
    }
    val xs = lines.flatMap(line => List(line.start.x, line.end.x))
    val ys = lines.flatMap(line => List(line.start.y, line.end.y))
    val minX = xs.min
    val maxX = xs.max
    val minY = ys.min
    val maxY = ys.max
    val margin = 20.0
    val width = math.max(1.0, maxX - minX) + margin * 2
    val height = math.max(1.0, maxY - minY) + margin * 2
    val offset = TurtlePoint(minX - margin, minY - margin)
    val normalizedLines = lines.map { line =>
      TurtleLineSegment(
        TurtlePoint(line.start.x - offset.x, line.start.y - offset.y),
        TurtlePoint(line.end.x - offset.x, line.end.y - offset.y)
      )
    }
    (normalizedLines, width, height, offset)
  }
}

object TurtleSvgRenderer {
  def render(coloredLines: List[ColoredTurtleLine], width: Double, height: Double, strokeWidth: Double): String = {
    val sb = new StringBuilder
    sb.append(s"""<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ${width} ${height}' width='${width}' height='${height}'>""")
    coloredLines.foreach { colored =>
      val line = colored.segment
      val y1 = height - line.start.y
      val y2 = height - line.end.y
      sb.append(s"<line x1='${line.start.x}' y1='${y1}' x2='${line.end.x}' y2='${y2}' stroke='${colored.color}' stroke-width='${strokeWidth}' stroke-linecap='round'/>")
    }
    sb.append("</svg>")
    sb.toString()
  }
}
