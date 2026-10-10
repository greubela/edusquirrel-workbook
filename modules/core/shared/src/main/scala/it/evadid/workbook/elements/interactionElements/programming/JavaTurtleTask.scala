package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic
import upickle.default.*

enum JavaTurtleArgument {
  case IntValue(value: Int)
  case DoubleValue(value: Double)

  def isValid: Boolean = this match {
    case IntValue(_) => true
    case DoubleValue(value) => value.isFinite
  }

  def literal: String = this match {
    case IntValue(value) => value.toString
    case DoubleValue(value) if JavaTurtleArgument.isNegativeZero(value) => "-0.0"
    case DoubleValue(value) =>
      val text = java.lang.Double.toString(value)
      if value.isFinite && !text.exists(char => char == '.' || char == 'e' || char == 'E') then text + ".0" else text
  }
}

object JavaTurtleArgument {
  private def isNegativeZero(value: Double): Boolean = java.lang.Double.doubleToRawLongBits(value) == Long.MinValue

  given ReadWriter[JavaTurtleArgument] = readwriter[ujson.Value].bimap[JavaTurtleArgument](
    {
      case IntValue(value) => ujson.Num(value)
      case DoubleValue(value) if value.isFinite =>
        val number: ujson.Value = if isNegativeZero(value) then ujson.Str("-0.0") else ujson.Num(value)
        ujson.Obj("type" -> "double", "value" -> number)
      case _ => throw IllegalArgumentException("A task argument must be finite.")
    },
    {
      case ujson.Num(value) if value.isFinite && value.isWhole && value >= Int.MinValue && value <= Int.MaxValue =>
        IntValue(value.toInt)
      case value: ujson.Obj if value.value.keySet == Set("type", "value") =>
        (value("type"), value("value")) match {
          case (ujson.Str("double"), ujson.Num(number)) if number.isFinite => DoubleValue(number)
          case (ujson.Str("double"), ujson.Str("-0.0")) => DoubleValue(-0.0)
          case _ => throw IllegalArgumentException("Use a finite double task argument.")
        }
      case _ => throw IllegalArgumentException("Use an int32 number or a typed double task argument.")
    }
  )
}

final case class JavaTurtleCase(arguments: List[JavaTurtleArgument], expectedShape: TurtleGraphic) derives ReadWriter {
  def call(methodName: String): String = s"$methodName(${arguments.map(_.literal).mkString(", ")})"
}

final case class JavaTurtleTask(
    startingProgram: String,
    methodName: String,
    cases: List[JavaTurtleCase]
) derives ReadWriter

object JavaTurtleTask {
  val squarePilot: JavaTurtleTask = JavaTurtleTask(
    """public class Drawing {
      |  static void square(int side) {
      |
      |  }
      |
      |  public static void main(String[] args) {
      |    square(25);
      |  }
      |}
      |""".stripMargin,
    "square",
    List(25, 40, 0).map { side =>
      val commands = if side == 0 then Nil else List.fill(4)(List(
        TurtleCommand[Double]("forward", List(side.toDouble)),
        TurtleCommand[Double]("turnRight", List(90.0)))).flatten
      JavaTurtleCase(List(JavaTurtleArgument.IntValue(side)), TurtleGraphic.TurtleGraphicProgram(commands))
    }
  )
}
