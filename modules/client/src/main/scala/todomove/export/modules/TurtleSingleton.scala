package todomove.`export`.modules

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.{SvgPathBuilderCommand, TurtlePathBuilder}
import SvgPathBuilderCommand.*
import it.evadid.util.JsHelpers

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("turtle")
object TurtleSingleton {

  type T = Double

  private var turtlePathBuilder = TurtlePathBuilder[T]()

  @JSExport def reset(): Unit = {
    turtlePathBuilder = TurtlePathBuilder[T]()
  }

  @JSExport def getX(): T = turtlePathBuilder.getX()

  @JSExport def getY(): T = turtlePathBuilder.turtleState.y

  @JSExport def heading(): T = turtlePathBuilder.turtleState.headingDeg

  @JSExport def xcor(): T = getX()

  @JSExport def ycor(): T = getY()

  @JSExport def position(): js.Array[T] = js.Array(getX(), getY())

  @JSExport def pos(): js.Array[T] = position()

  @JSExport def isdown(): Boolean = turtlePathBuilder.turtleState.penDown

  @JSExport def isvisible(): Boolean = turtlePathBuilder.turtleState.visible

  @JSExport def towards(x: T, y: T): T = turtlePathBuilder.towards(x, y)

  @JSExport def forward(distance: T): Unit = {
    turtlePathBuilder = turtlePathBuilder.forward(distance)
  }

  @JSExport def backward(distance: T): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.Backward[T](distance))
  }

  @JSExport def left(rotationDeg: T): Unit = {
    turtlePathBuilder = turtlePathBuilder.left(rotationDeg)
  }

  @JSExport def right(rotationDeg: T): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.Right[T](rotationDeg))
  }

  @JSExport def goto(x: T, y: T): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.Goto[T](x, y))
  }

  @JSExport def setpos(x: T, y: T): Unit = goto(x, y)

  @JSExport def setposition(x: T, y: T): Unit = goto(x, y)

  @JSExport def setx(x: T): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.SetX[T](x))
  }

  @JSExport def sety(y: T): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.SetY[T](y))
  }

  @JSExport def setheading(degrees: T): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.SetHeading[T](degrees))
  }

  @JSExport def seth(degrees: T): Unit = setheading(degrees)

  @JSExport def penup(): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.PenUp[T]())
  }

  @JSExport def pendown(): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.PenDown[T]())
  }

  @JSExport def home(): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.Home[T]())
  }

  @JSExport def clear(): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.Clear[T]())
  }

  @JSExport def clearscreen(): Unit = clear()

  @JSExport def showturtle(): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.ShowTurtle[T]())
  }

  @JSExport def hideturtle(): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.HideTurtle[T]())
  }

  @JSExport def dot(size: T): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.Dot[T](size))
  }

  @JSExport def circle(radius: T): Unit = {
    turtlePathBuilder = turtlePathBuilder.handleCommand(TurtleCommand.Circle[T](radius))
  }

  @JSExport def allowedCommands(): Set[String] = Set(
    "forward", "fd",
    "backward", "back", "bk",
    "left", "lt",
    "right", "rt",
    "goto", "setpos", "setposition",
    "setheading", "seth",
    "penup", "pu", "up",
    "pendown", "pd", "down",
    "showturtle", "st",
    "hideturtle", "ht",
    "clear", "clearscreen",
    "setx",
    "sety",
    "home",
    "reset",
    "dot",
    "circle",
    "position", "pos",
    "xcor", "ycor",
    "heading",
    "towards",
    "isdown",
    "isvisible"
  )


  @JSExport def handleCommand(name: String, args: Vector[js.Any]): Unit = {
    val cmd = TurtleCommand.fromText[T](name, args.flatMap(JsHelpers.doubleHelper.fromJsToScala).toList)
    turtlePathBuilder = turtlePathBuilder.handleCommand(cmd)
  }

  @JSExport def TurtleCommands(): List[TurtleCommand[T]] = {
    turtlePathBuilder.turtleCommands
  }

  @JSExport def svgPathBuilderCommands(): List[SvgPathBuilderCommand[T]] = {
    turtlePathBuilder.pathBuilderCommands
  }

  @JSExport def executionSnapshot(): js.Object = {
    val turtleCommandsDto = turtlePathBuilder.turtleCommands.map(cmd => js.Dynamic.literal(
      name = cmd.name.universalInterpretation(),
      args = cmd.args.toJSArray
    ))

    val svgCommandsDto = turtlePathBuilder.pathBuilderCommands.flatMap(serializeSvgCommand)

    js.Dynamic.literal(
      startPoint = pointDto(turtlePathBuilder.startPoint),
      turtleState = js.Dynamic.literal(
        x = turtlePathBuilder.turtleState.x,
        y = turtlePathBuilder.turtleState.y,
        headingDeg = turtlePathBuilder.turtleState.headingDeg,
        penDown = turtlePathBuilder.turtleState.penDown,
        visible = turtlePathBuilder.turtleState.visible
      ),
      turtleCommands = turtleCommandsDto.toJSArray,
      svgPathBuilderCommands = svgCommandsDto.toJSArray
    ).asInstanceOf[js.Object]
  }

  private def pointDto(point: Point[T]): js.Object =
    js.Dynamic.literal(x = point.x, y = point.y).asInstanceOf[js.Object]

  private def dimensionDto(d: Dimension[T]): js.Object =
    js.Dynamic.literal(dx = d.width, dy = d.height).asInstanceOf[js.Object]

  private def serializeSvgCommand(command: SvgPathBuilderCommand[T]): Option[js.Object] = {
    command match {
      case MoveAbs(p) =>
        Some(js.Dynamic.literal(kind = "MoveAbs", x = p.x, y = p.y).asInstanceOf[js.Object])
      case LineAbs(p) =>
        Some(js.Dynamic.literal(kind = "LineAbs", x = p.x, y = p.y).asInstanceOf[js.Object])
      case MoveRel(d) =>
        Some(js.Dynamic.literal(kind = "MoveRel", dx = d.width, dy = d.height).asInstanceOf[js.Object])
      case ArcRel(rx, ry, rot, largeArc, sweep, d) =>
        Some(js.Dynamic.literal(
          kind = "ArcRel",
          rx = rx,
          ry = ry,
          rotationDeg = rot,
          largeArc = largeArc,
          sweep = sweep,
          dx = d.width,
          dy = d.height
        ).asInstanceOf[js.Object])
      case CenteredCircleControl(radius) =>
        Some(js.Dynamic.literal(kind = "CenteredCircleControl", radius = radius).asInstanceOf[js.Object])
      case _ => None
    }
  }
}
