package interactionPlugins.programmingExercise.pythonExercise.turtle
/*
import contentmanagement.model.color.{AppColor, RGBColor}
import contentmanagement.webElements.genericHtmlElements.canvas.WebCanvas
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.{JsDataVariable, SyncModuleBackend}
import util.numbers.AlgebriteNumber

import scala.scalajs.js

case class TurtleBackend(canvas: WebCanvas) extends SyncModuleBackend {

  override val moduleName: String = "turtle"

  private case class TurtleState(
      x: AlgebriteNumber = AlgebriteNumber("0"),
      y: AlgebriteNumber = AlgebriteNumber("0"),
      headingDeg: AlgebriteNumber = AlgebriteNumber("0"),
      penDown: Boolean = true,
      penSize: AlgebriteNumber = AlgebriteNumber("1"),
      penColor: String = "#000000",
      fillColor: String = "#000000",
      visible: Boolean = true
  )

  private var turtle = TurtleState()
  private var backgroundColor: String = "#ffffff"

  override def handleModuleCall(callbackName: String, args: Seq[JsDataVariable]): js.Any = {
    print("handleModuleCall(" + callbackName + ", " + args.mkString(", ") + ")")
    callbackName match {
      case "forward" | "fd" => forward(numberArg(args, 0)); js.undefined
      case "backward" | "back" | "bk" => forward(-numberArg(args, 0)); js.undefined
      case "left" | "lt" => left(numberArg(args, 0)); js.undefined
      case "right" | "rt" => right(numberArg(args, 0)); js.undefined
      case "goto" | "setpos" | "setposition" => goto(numberArg(args, 0), numberArg(args, 1)); js.undefined
      case "setx" => goto(numberArg(args, 0), turtle.y); js.undefined
      case "sety" => goto(turtle.x, numberArg(args, 0)); js.undefined
      case "setheading" | "seth" =>
        turtle = turtle.copy(headingDeg = normalizeHeading(numberArg(args, 0))); js.undefined
      case "home" =>
        turtle = turtle.copy(headingDeg = AlgebriteNumber("0"))
        goto(AlgebriteNumber("0"), AlgebriteNumber("0"))
        js.undefined
      case "clear" => clearScreen(resetTurtle = false); js.undefined
      case "reset" => clearScreen(resetTurtle = true); js.undefined
      case "clearscreen" => clearScreen(resetTurtle = true); js.undefined
      case "penup" | "pu" | "up" => turtle = turtle.copy(penDown = false); js.undefined
      case "pendown" | "pd" | "down" => turtle = turtle.copy(penDown = true); js.undefined
      case "isdown" => turtle.penDown
      case "pensize" | "width" =>
        if args.nonEmpty then {
          val requested = numberArg(args, 0)
          val min = AlgebriteNumber("0.1")
          turtle = turtle.copy(penSize = if requested.toDouble < 0.1 then min else requested)
          js.undefined
        } else turtle.penSize.toDouble
      case "pencolor" =>
        if args.nonEmpty then {
          val color = colorArg(args, 0)
          turtle = turtle.copy(penColor = color)
          applyPenColor(color)
          js.undefined
        } else turtle.penColor
      case "fillcolor" =>
        if args.nonEmpty then {
          turtle = turtle.copy(fillColor = colorArg(args, 0))
          js.undefined
        } else turtle.fillColor
      case "color" =>
        if args.nonEmpty then {
          val pen = colorArg(args, 0)
          val fill = if args.length >= 2 then colorArg(args, 1) else pen
          turtle = turtle.copy(penColor = pen, fillColor = fill)
          applyPenColor(pen)
          js.undefined
        } else js.Array(turtle.penColor, turtle.fillColor)
      case "position" | "pos" => js.Array(turtle.x.toDouble, turtle.y.toDouble)
      case "xcor" => turtle.x.toDouble
      case "ycor" => turtle.y.toDouble
      case "heading" => turtle.headingDeg.toDouble
      case "distance" =>
        val dx = numberArg(args, 0).toDouble - turtle.x.toDouble
        val dy = numberArg(args, 1).toDouble - turtle.y.toDouble
        Math.hypot(dx, dy)
      case "dot" =>
        val size = if args.nonEmpty then numberArg(args, 0) else AlgebriteNumber("6")
        val dotColor = if args.length >= 2 then colorArg(args, 1) else turtle.penColor
        fillDot(size, dotColor)
        js.undefined
      case "circle" =>
        val radius = math.abs(numberArg(args, 0).toDouble)
        applyPenColor(turtle.penColor)
        canvas.drawCircle(toCanvasX(turtle.x), toCanvasY(turtle.y), radius * 2.0, turtle.penSize.toDouble)
        js.undefined
      case "bgcolor" =>
        if args.nonEmpty then {
          backgroundColor = colorArg(args, 0)
          clearScreen(resetTurtle = false)
          js.undefined
        } else backgroundColor
      case "showturtle" | "st" => turtle = turtle.copy(visible = true); js.undefined
      case "hideturtle" | "ht" => turtle = turtle.copy(visible = false); js.undefined
      case "isvisible" => turtle.visible
      case "speed" | "tracer" | "update" | "listen" | "onkey" | "onclick" | "ontimer" | "bye" | "done" | "mainloop" => js.undefined
      case "Turtle" | "RawTurtle" | "Screen" | "getscreen" => js.undefined
      case _ => js.undefined
    }
  }

  private def forward(distance: AlgebriteNumber): Unit = {
    val rad = turtle.headingDeg.toDouble * Math.PI / 180.0
    val dx = AlgebriteNumber((Math.cos(rad) * distance.toDouble).toString)
    val dy = AlgebriteNumber((Math.sin(rad) * distance.toDouble).toString)
    val nx = turtle.x + dx
    val ny = turtle.y + dy
    drawMove(nx, ny)
    turtle = turtle.copy(x = nx, y = ny)
  }

  private def left(angle: AlgebriteNumber): Unit =
    turtle = turtle.copy(headingDeg = normalizeHeading(turtle.headingDeg + angle))

  private def right(angle: AlgebriteNumber): Unit =
    turtle = turtle.copy(headingDeg = normalizeHeading(turtle.headingDeg - angle))

  private def goto(x: AlgebriteNumber, y: AlgebriteNumber): Unit = {
    drawMove(x, y)
    turtle = turtle.copy(x = x, y = y)
  }

  private def drawMove(newX: AlgebriteNumber, newY: AlgebriteNumber): Unit = {
    if turtle.penDown then {
      applyPenColor(turtle.penColor)
      canvas.drawLine(
        toCanvasX(turtle.x),
        toCanvasY(turtle.y),
        toCanvasX(newX),
        toCanvasY(newY),
        turtle.penSize.toDouble
      )
    }
  }

  private def fillDot(size: AlgebriteNumber, color: String): Unit = {
    toAppColor(color).foreach(canvas.setFillColor)
    canvas.fillCircle(toCanvasX(turtle.x), toCanvasY(turtle.y), size.toDouble)
  }

  private def clearScreen(resetTurtle: Boolean): Unit = {
    canvas.clear(toAppColor(backgroundColor).getOrElse(RGBColor.white))
    if resetTurtle then turtle = TurtleState()
  }

  private def numberArg(args: Seq[PyodideEnvironment.JsDataVariable], idx: Int): AlgebriteNumber =
    args.lift(idx)
      .map(_.stringRepresentation)
      .map(_.trim.stripPrefix("'").stripSuffix("'").stripPrefix("\"").stripSuffix("\""))
      .flatMap(str => summon[Fractional[AlgebriteNumber]].parseString(str))
      .getOrElse(AlgebriteNumber("0"))

  private def colorArg(args: Seq[PyodideEnvironment.JsDataVariable], idx: Int): String =
    args.lift(idx).map(_.stringRepresentation).map(normalizeColor).getOrElse("#000000")

  private def normalizeHeading(angle: AlgebriteNumber): AlgebriteNumber =
    AlgebriteNumber((((angle.toDouble % 360.0) + 360.0) % 360.0).toString)

  private def normalizeColor(raw: String): String = {
    val cleaned = raw.trim.toLowerCase.stripPrefix("'").stripSuffix("'").stripPrefix("\"").stripSuffix("\"")
    cleaned match {
      case "black" => "#000000"
      case "white" => "#ffffff"
      case "red" => "#ff0000"
      case "green" => "#008000"
      case "blue" => "#0000ff"
      case "yellow" => "#ffff00"
      case s if s.matches("#[0-9a-f]{6}") => s
      case _ => "#000000"
    }
  }

  private def toAppColor(value: String): Option[AppColor] = {
    val normalized = normalizeColor(value)
    if normalized.matches("#[0-9a-f]{6}") then Some(AppColor.fromWebStyleString(normalized)) else None
  }

  private def applyPenColor(color: String): Unit =
    toAppColor(color).foreach(canvas.setStrokeColor)

  private def toCanvasX(x: AlgebriteNumber): Double = canvas.getWidth / 2.0 + x.toDouble

  private def toCanvasY(y: AlgebriteNumber): Double = canvas.getHeight / 2.0 - y.toDouble
}
*/