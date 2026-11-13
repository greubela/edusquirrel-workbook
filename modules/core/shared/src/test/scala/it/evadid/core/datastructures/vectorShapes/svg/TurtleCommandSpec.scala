package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.core.datastructures.language.AppLanguage.English
import munit.FunSuite
import upickle.default.*

class TurtleCommandSpec extends FunSuite {
  import TurtleCommand.*

  private val commands: List[TurtleCommand[Double]] = List(
    Forward(5), Backward(4), Left(90), Right(45), SetHeading(180), SetX(2), SetY(3),
    Goto(1, 2), Towards(3, 4), ArcLeft(5, 90), ArcRight(6, 180), Dot(2), PenSize(3), Speed(4),
    PenUp(), PenDown(), ShowTurtle(), HideTurtle(), Home(), Reset(), Clear(),
    Circle(10), Circle(10, Some(90), Some(12)), Color("red"),
    Stitch(TurtleStitchMode.Cross, Seq(8, 13), Seq("False")),
    Unsupported(BeEntityName.fromCodeString("custom_command"), Seq(1), Seq("value")))

  test("typed command variants have default JSON and MessagePack codecs") {
    commands.foreach { command =>
      assertEquals(read[TurtleCommand[Double]](write(command)), command)
      assertEquals(readBinary[TurtleCommand[Double]](writeBinary(command)), command)
    }
    assertEquals(read[Forward[Double]](write(Forward(5.0))), Forward(5.0))
    intercept[Exception](read[TurtleCommand[Double]]("[\"forward\",[5]]"))
  }

  test("names use the shared entity-name model and text aliases decode at the boundary") {
    assertEquals(Forward(2.0).name.getNameIn(English, NamingStyle.CamelCase), "forward")
    assertEquals(PenUp[Double]().name.getNameIn(English, NamingStyle.CamelCase), "penUp")
    assertEquals(fromCall(BeEntityName.fromCodeString("turnRight"), Seq(90.0)), Right(90.0))
    assertEquals(fromText("fd", Seq(5.0)), Forward(5.0))
    assertEquals(fromText(" TURN-RIGHT ", Seq(90.0)), Right(90.0))
    assertEquals(fromText[Double]("pu"), PenUp[Double]())
    assertEquals(fromText("color", Seq(255.0, 0.0, 10.0)), Color[Double]("rgb(255,0,10)"))
  }

  test("incomplete or unsupported calls preserve their payload without becoming valid drawing operations") {
    val missing = fromText[Double]("forward")
    assert(missing.isInstanceOf[Unsupported[?]])
    val extra = fromText("forward", Seq(1.0, 2.0), Seq("private"))
    assert(extra.isInstanceOf[Unsupported[?]])
    assertEquals(extra.args, Seq(1.0, 2.0))
    assertEquals(extra.stringArgs, Seq("private"))
    val builder = TurtlePathBuilder[Double]().handleCommand(extra)
    assertEquals(builder.turtleCommands, List(extra))
    assertEquals(builder.turtleState.x, 0.0)
  }

  test("numeric mapping preserves variant identity and named fields") {
    assertEquals(Forward(BigDecimal("2.5")).asDouble, Forward(2.5))
    commands.foreach { command =>
      assertEquals(command.mapNumbers(identity[Double]), command)
      assertEquals(command.mapNumbers(_ * 2).name, command.name)
      assertEquals(command.mapNumbers(_ * 2).args, command.args.map(_ * 2))
      assertEquals(command.mapNumbers(_ * 2).stringArgs, command.stringArgs)
    }
  }

  test("typed path execution and text decoding produce the same geometry") {
    val typed = List(Forward(10.0), Right(90.0), PenUp[Double](), Goto(3.0, 4.0), PenDown[Double](), Forward(2.0))
    val decoded = List(fromText("fd", Seq(10.0)), fromText("rt", Seq(90.0)), fromText[Double]("pu"),
      fromText("setpos", Seq(3.0, 4.0)), fromText[Double]("pd"), fromText("forward", Seq(2.0)))
    assertEquals(TurtlePathBuilder(Point(0.0, 0.0), typed), TurtlePathBuilder(Point(0.0, 0.0), decoded))
  }

  test("shared arc stepping retains clockwise and counterclockwise symmetry") {
    val left = TurtlePathBuilder[Double]().arcLeft(10, 90)
    val right = TurtlePathBuilder[Double]().arcRight(10, 90)
    assert(math.abs(left.getX() - right.getX()) < 1e-10)
    assert(math.abs(left.getY() + right.getY()) < 1e-10)
    assertEquals(left.heading(), 90.0)
    assertEquals(right.heading(), 270.0)
    val path: SvgPathBuilderImmutable[Double] = SvgPathBuilderImmutable(Point(0.0, 0.0))
      .moveToAbs(Point(1.0, 2.0)).lineToAbs(Point(3.0, 4.0)).addCenteredCircle(1.0)
    assert(path.furtherCommands.nonEmpty)
  }
  test("embroidery calls preserve their payload and typed mode through decoding and persistence") {
    TurtleStitchMode.values.foreach { mode =>
      val command = Stitch[Double](mode, Seq(8, 13), Seq("False"))
      assertEquals(fromCall(mode.name, command.args, command.stringArgs), command)
      assertEquals(read[TurtleCommand[Double]](write[TurtleCommand[Double]](command)), command)
      assertEquals(readBinary[TurtleCommand[Double]](writeBinary[TurtleCommand[Double]](command)), command)
      assertEquals(TurtlePathBuilder[Double]().handleCommand(command).turtleState.stitchMode,
        mode.name.universalInterpretation())
    }
  }

}
