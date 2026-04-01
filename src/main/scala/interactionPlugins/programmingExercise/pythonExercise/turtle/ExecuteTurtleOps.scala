package interactionPlugins.programmingExercise.pythonExercise.turtle

import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.CallbackLibrary
import util.numbers.AlgebriteNumber
import contentmanagement.model.color.{AppColor, RGBColor}
import contentmanagement.webElements.genericHtmlElements.canvas.WebCanvas
import scala.scalajs.js
import scala.util.Try

case class ExecuteTurtleOps(canvas: WebCanvas) {


  private case class TurtleState(
                                  x: AlgebriteNumber,
                                  y: AlgebriteNumber,
                                  headingDeg: AlgebriteNumber,
                                  penDown: Boolean,
                                  penSize: AlgebriteNumber,
                                  penColor: AppColor,
                                  fillColor: AppColor,
                                  visible: Boolean
                                )

  private val initState = TurtleState(AlgebriteNumber("0"), AlgebriteNumber("0"), AlgebriteNumber("0"), true, AlgebriteNumber("5"), RGBColor.red, RGBColor.black, true)
  private var backgroundColor: AppColor = RGBColor.yellow


  def reset(): Unit = {
    canvas.clear(backgroundColor)
    canvas.setFillColor(initState.fillColor)
    canvas.setStrokeColor(initState.penColor)
    turtle = initState
  }

  private var turtle = {
    reset()
    initState
  }

  private def fillDot(args: Vector[js.Any]): Unit = {
    val size = if args.nonEmpty then numberArg(args, 0) else AlgebriteNumber("6")
    val dotColor = if args.length >= 2 then colorArg(args, 1) else turtle.penColor
    fillDot(size, dotColor)
  }

  private def drawCircle(radius: AlgebriteNumber): Unit = {
    canvas.setStrokeColor(turtle.penColor)
    canvas.drawCircle(toCanvasX(turtle.x), toCanvasY(turtle.y), math.abs(radius.toDouble) * 2.0, turtle.penSize.toDouble)
  }

  lazy val callbackLibrary: CallbackLibrary = CallbackLibrary("turtle", commandMap)

  private val commandMap: Map[String, Vector[js.Any] => Unit] = {

    def synonymMap(names: List[String], function: Vector[js.Any] => Unit): Map[String, Vector[js.Any] => Unit] = names.map(_ -> function).toMap

    def funcMap(name: String, function: Vector[js.Any] => Unit): Map[String, Vector[js.Any] => Unit] = Map(name -> function)

    synonymMap(List("forward", "fd"), args => forward(numberArg(args, 0)))
      ++ synonymMap(List("backward", "back", "bk"), args => forward(-numberArg(args, 0)))
      ++ synonymMap(List("left", "lt"), args => left(numberArg(args, 0)))
      ++ synonymMap(List("right", "rt"), args => right(numberArg(args, 0)))
      ++ synonymMap(List("goto", "setpos", "setposition"), args => goto(numberArg(args, 0), numberArg(args, 1)))
      ++ synonymMap(List("setheading", "seth"), args => turtle = turtle.copy(headingDeg = normalizeHeading(numberArg(args, 0))))
      ++ synonymMap(List("penup", "pu", "up"), args => turtle = turtle.copy(penDown = false))
      ++ synonymMap(List("pendown", "pd", "down"), args => turtle = turtle.copy(penDown = true))
      ++ synonymMap(List("showturtle", "st"), args => turtle = turtle.copy(visible = true))
      ++ synonymMap(List("hideturtle", "ht"), args => turtle = turtle.copy(visible = false))
      ++ synonymMap(List("clear", "clearscreen"), args => clearScreen(resetTurtle = false))
      ++ funcMap("setx", args => goto(numberArg(args, 0), turtle.y))
      ++ funcMap("sety", args => goto(turtle.x, numberArg(args, 0)))
      ++ funcMap("home", args => turtle = turtle.copy(x = AlgebriteNumber("0"), y = AlgebriteNumber("0"), headingDeg = AlgebriteNumber("0")))
      ++ funcMap("reset", args => clearScreen(resetTurtle = true))
      ++ funcMap("dot", fillDot)
      ++ funcMap("circle", args => drawCircle(numberArg(args, 0)))
  }

  private def twoDigitDoubleToAlgebrite(double: Double): AlgebriteNumber = AlgebriteNumber(("%.2f").format(double))

  private def forward(distance: AlgebriteNumber): Unit = {
    println("##### foooorwaaaard!!!")
    val rad: Double = turtle.headingDeg.toDouble * Math.PI / 180.0
    val dx = twoDigitDoubleToAlgebrite(Math.cos(rad) * distance.toDouble)
    val dy = twoDigitDoubleToAlgebrite(Math.sin(rad) * distance.toDouble)
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
      canvas.setStrokeColor(turtle.penColor)
      canvas.drawLine(
        toCanvasX(turtle.x),
        toCanvasY(turtle.y),
        toCanvasX(newX),
        toCanvasY(newY),
        turtle.penSize.toDouble
      )
    } else {
      println("pen is not down")
    }
  }

  private def fillDot(size: AlgebriteNumber, color: AppColor): Unit = {
    canvas.setFillColor(color)
    canvas.fillCircle(toCanvasX(turtle.x), toCanvasY(turtle.y), size.toDouble)
  }

  private def clearScreen(resetTurtle: Boolean): Unit = {
    canvas.clear(backgroundColor)
    if resetTurtle then turtle = initState
  }

  private def numberArg(args: Vector[js.Any], idx: Int): AlgebriteNumber =
    args
      .lift(idx)
      .flatMap(parseNumberArg)
      .getOrElse(AlgebriteNumber("0"))

  private def parseNumberArg(value: js.Any): Option[AlgebriteNumber] = {
    val parse = summon[Fractional[AlgebriteNumber]].parseString(_)
    js.typeOf(value) match {
      case "number" => Some(AlgebriteNumber(value.asInstanceOf[Double].toString))
      case "boolean" => Some(if value.asInstanceOf[Boolean] then AlgebriteNumber("1") else AlgebriteNumber("0"))
      case "string" => parse(stripWrappingQuotes(value.asInstanceOf[String].trim))
      case _ => parse(stripWrappingQuotes(value.toString.trim))
    }
  }

  private def colorArg(args: Vector[js.Any], idx: Int): AppColor =
    args
      .lift(idx)
      .flatMap(parseColorArg)
      .getOrElse(RGBColor.black)

  private def parseColorArg(value: js.Any): Option[AppColor] = {
    val normalized = stripWrappingQuotes(value.toString.trim)
    Try(AppColor.fromWebStyleString(normalized)).toOption
  }

  private def stripWrappingQuotes(value: String): String =
    value.stripPrefix("'").stripSuffix("'").stripPrefix("\"").stripSuffix("\"")

  private def normalizeHeading(angle: AlgebriteNumber): AlgebriteNumber =
    AlgebriteNumber((((angle.toDouble % 360.0) + 360.0) % 360.0).toString)

  private def toCanvasX(x: AlgebriteNumber): Double = canvas.getWidth / 2.0 + x.toDouble

  private def toCanvasY(y: AlgebriteNumber): Double = canvas.getHeight / 2.0 - y.toDouble


}
