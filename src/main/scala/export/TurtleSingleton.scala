package `export`

import contentmanagement.webElements.svg.TurtlePathBuilder
import contentmanagement.webElements.svg.TurtlePathBuilder.TurtleCommand
import contentmanagement.webElements.svg.builder.SvgPathBuilderCommand
import util.numbers.AlgebriteNumber
import util.web.JsHelpers

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("turtle")
object TurtleSingleton {

  type T = Double

  private var turtlePathBuilder = TurtlePathBuilder[T]()

  @JSExport def reset(): Unit = {
    turtlePathBuilder = TurtlePathBuilder[T]()
  }

  @JSExport def getX(): T = turtlePathBuilder.getX()

  @JSExport def forward(distance: T): Unit = {
    turtlePathBuilder = turtlePathBuilder.forward(distance)
  }

  @JSExport def allowedCommands(): Set[String] = Set("forward", "fd",
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
    // todo: finish
  )


  @JSExport def handleCommand(name: String, args: Vector[js.Any]): Unit = {
    val cmd = TurtleCommand[T](name, args.flatMap(JsHelpers.parseDouble).toList)
    turtlePathBuilder = turtlePathBuilder.handleStringCommand(cmd)
  }

  @JSExport def TurtleCommands(): List[TurtleCommand[T]] = {
    turtlePathBuilder.turtleCommands
  }

  @JSExport def svgPathBuilderCommands(): List[SvgPathBuilderCommand[T]] = {
    turtlePathBuilder.pathBuilderCommands
  }

  // todo finish

}
