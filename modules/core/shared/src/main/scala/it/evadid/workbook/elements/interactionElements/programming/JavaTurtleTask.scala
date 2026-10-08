package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import upickle.default.*

final case class JavaTurtleCase(arguments: List[Int], expectedShape: TurtleGraphic) derives ReadWriter

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
      JavaTurtleCase(List(side), TurtleGraphic.TurtleGraphicProgram(commands))
    }
  )
}
