package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.TurtleGraphic
import it.evadid.workbook.elements.interactionElements.programming.TurtleGraphic.TurtleGraphicProgram
import scala.collection.mutable.ListBuffer

/** Geometric equivalents of the original raster targets. Units are turtle steps;
  * pen-up travel is explicit and never becomes a stitch in the expected drawing.
  */
object EmbroideryPreviewTargets {
  private class Drawing {
    val commands = ListBuffer.empty[TurtleCommand[Double]]
    def forward(length: Double): Unit = commands += TurtleCommand("forward", List(length))
    def turn(degrees: Double): Unit = commands += TurtleCommand("turnRight", List(degrees))
    def up(): Unit = commands += TurtleCommand("penUp")
    def down(): Unit = commands += TurtleCommand("penDown")
    def move(x: Double, y: Double, heading: Double = 0): Unit = {
      up()
      commands += TurtleCommand("goto", List(x, y))
      commands += TurtleCommand("setHeading", List(heading))
      down()
    }
    def polygon(sides: Int, length: Double): Unit =
      for (_ <- 0 until sides) { forward(length); turn(360.0 / sides) }
    def house(length: Double): Unit = {
      polygon(4, length)
      turn(-60); forward(length); turn(120); forward(length); turn(-60)
      up(); forward(-length); down()
    }
    def koch(depth: Int, length: Double): Unit =
      if (depth == 0) forward(length)
      else {
        koch(depth - 1, length / 3); turn(-60)
        koch(depth - 1, length / 3); turn(120)
        koch(depth - 1, length / 3); turn(-60)
        koch(depth - 1, length / 3)
      }
    def result: TurtleGraphicProgram = TurtleGraphicProgram(commands.toList)
  }
  private def draw(body: Drawing => Unit): TurtleGraphicProgram = {
    val d = new Drawing
    body(d)
    d.result
  }

  val shapeNames: Set[String] = Set("square", "two_squares", "four_squares", "five_triangles",
    "pinwheel", "squarewheel", "blocky_eight", "repeat_large", "houses_larger", "circles_larger")

  def shape(name: String): TurtleGraphic = draw { d =>
    name match {
      case "square" => d.polygon(4, 50)
      case "two_squares" | "four_squares" =>
        for (i <- 0 until (if (name == "two_squares") 2 else 4)) {
          d.move(0, i * 100); d.polygon(4, 50)
        }
      case "five_triangles" =>
        for (i <- 0 until 5) { d.move(i * 100, 0); d.polygon(3, 50) }
      case "pinwheel" =>
        for (_ <- 0 until 3) { d.polygon(3, 50); d.turn(120) }
      case "squarewheel" =>
        for (_ <- 0 until 6) { d.polygon(4, 50); d.turn(60) }
      case "blocky_eight" =>
        d.turn(-165); d.polygon(12, 20); d.turn(180); d.polygon(12, 20)
      case "repeat_large" =>
        // Five point-touching hexagons. Dashed spokes in the raster are pen-up travel.
        val height = 100.0
        for (i <- 0 until 5) { d.move(0, -i * height, 150); d.polygon(6, 50) }
      case "houses_larger" =>
        var x = 0.0
        for (i <- 1 to 4) { d.move(x, 0); d.house(i * 20); x += i * 20 + 20 }
      case "circles_larger" =>
        for (i <- 1 to 10) {
          d.move(0, 0, 5); d.polygon(36, i * 3)
          d.move(0, 0, 185); d.polygon(36, i * 3)
        }
      case _ => throw new IllegalArgumentException("Unknown embroidery preview target: " + name)
    }
  }

  val alternatingShapes: TurtleGraphic = draw { d =>
    for (i <- 1 to 6) {
      d.move((i - 1) * 80, 0)
      d.polygon(if (i % 2 == 0) 3 else 4, 40)
    }
  }

  val nestedShapes: TurtleGraphic = draw { d =>
    for (i <- 0 to 10) {
      d.move(i * 50, 0)
      d.polygon(if (i % 3 == 1) 4 else 3, 25)
    }
  }

  val nestedExample: String =
    """def square(length):
      |    for edge in range(4):
      |        forward(length)
      |        turn(90)
      |
      |def triangle(length):
      |    for edge in range(3):
      |        forward(length)
      |        turn(120)
      |
      |for i in range(11):
      |    penup()
      |    goto(i * 50, 0)
      |    pendown()
      |    if i % 3 == 0:
      |        triangle(25)
      |    else:
      |        if i % 3 == 1:
      |            square(25)
      |        else:
      |            triangle(25)
      |""".stripMargin

  def housePattern(period: Int): TurtleGraphic = draw { d =>
    require(period == 2 || period == 4)
    for (i <- 1 to 11) {
      d.move((i - 1) * 80, 0)
      if (i % period == 0) { d.turn(-60); d.polygon(3, 60) }
      else d.house(30)
    }
  }

  def koch(depth: Int, length: Double): TurtleGraphic = draw(_.koch(depth, length))
  def snowflake(depth: Int): TurtleGraphic = draw { d =>
    for (_ <- 0 until 3) { d.koch(depth, 270); d.turn(120) }
  }

  val branches: TurtleGraphic = draw { d =>
    def branch(length: Double, depth: Int): Unit = {
      d.forward(length)
      if (depth > 0) {
        d.turn(-60); branch(length / 3, depth - 1)
        d.turn(60); branch(length / 3, depth - 1)
        d.turn(60); branch(length / 3, depth - 1)
        d.turn(-60)
      }
      // Return without stitching any segment twice.
      d.up(); d.forward(-length); d.down()
    }
    for (_ <- 0 until 6) { branch(90, 2); d.turn(60) }
  }

  val conditionExample: String =
    """def square(length):
      |    for edge in range(4):
      |        forward(length)
      |        turn(90)
      |
      |def triangle(length):
      |    for edge in range(3):
      |        forward(length)
      |        turn(120)
      |
      |for i in range(1, 7):
      |    penup()
      |    goto((i - 1) * 80, 0)
      |    pendown()
      |    if i % 2 == 0:
      |        triangle(40)
      |    else:
      |        square(40)
      |""".stripMargin

  val kochExample: String =
    """def koch(level):
      |    if level == 0:
      |        forward(50)
      |    else:
      |        koch(level - 1)
      |        turn(-60)
      |        koch(level - 1)
      |        turn(120)
      |        koch(level - 1)
      |        turn(-60)
      |        koch(level - 1)
      |
      |koch(0)
      |""".stripMargin
}
