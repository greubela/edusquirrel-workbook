package contentmanagement.webElements.svg

import contentmanagement.webElements.svg.TurtlePathBuilder.{TurtleCommand, TurtleState}
import contentmanagement.webElements.svg.builder.{SvgPathBuilder, SvgPathBuilderCommand, SvgPathBuilderImmutable}
import datastructures.core.geometry.Point

case class TurtlePathBuilder[T: Fractional](
                                             startPoint: Point[T],
                                             turtleState: TurtleState[T],
                                             turtleCommands: List[TurtleCommand[T]],
                                             svgPathBuilder: SvgPathBuilderImmutable[T]
                                           ) {

  def pathBuilderCommands: List[SvgPathBuilderCommand[T]] = svgPathBuilder.furtherCommands

  val N = summon[Fractional[T]];

  import N.*

  lazy val zero = fromInt(0)

  def forward(distance: T): TurtlePathBuilder[T] = {
    val headingRad = Math.toRadians(turtleState.headingDeg.toDouble)
    val nextX = turtleState.x.toDouble + (Math.cos(headingRad) * distance.toDouble)
    // SVG y-axis grows downward, while turtle math usually grows upward.
    val nextY = turtleState.y.toDouble - (Math.sin(headingRad) * distance.toDouble)
    val endPoint = Point[T](SvgPathBuilder.fromDouble[T](nextX), SvgPathBuilder.fromDouble[T](nextY))
    val nextPathBuilder =
      if (turtleState.penDown) svgPathBuilder.lineToAbs(endPoint).asInstanceOf[SvgPathBuilderImmutable[T]]
      else svgPathBuilder.moveToAbs(endPoint).asInstanceOf[SvgPathBuilderImmutable[T]]

    this.copy(
      turtleState = turtleState.copy(x = endPoint.x, y = endPoint.y),
      svgPathBuilder = nextPathBuilder
    )
  }

  def left(rotationDeg: T): TurtlePathBuilder[T] = {
    this.copy(turtleState = turtleState.copy(headingDeg = turtleState.headingDeg + rotationDeg))
  }

  def right(rotationDeg: T): TurtlePathBuilder[T] = left(-rotationDeg)

  def backward(distance: T): TurtlePathBuilder[T] = forward(-distance)

  def getY(): T = turtleState.y

  def heading(): T = turtleState.headingDeg

  def towards(x: T, y: T): T = {
    val dx = x.toDouble - turtleState.x.toDouble
    val dy = turtleState.y.toDouble - y.toDouble
    val angleDeg = Math.toDegrees(Math.atan2(dy, dx))
    SvgPathBuilder.fromDouble[T](angleDeg)
  }

  def setHeading(degrees: T): TurtlePathBuilder[T] =
    this.copy(turtleState = turtleState.copy(headingDeg = degrees))

  def goto(x: T, y: T): TurtlePathBuilder[T] = {
    val endPoint = Point[T](x, y)
    val nextPathBuilder =
      if (turtleState.penDown) svgPathBuilder.lineToAbs(endPoint).asInstanceOf[SvgPathBuilderImmutable[T]]
      else svgPathBuilder.moveToAbs(endPoint).asInstanceOf[SvgPathBuilderImmutable[T]]
    this.copy(turtleState = turtleState.copy(x = x, y = y), svgPathBuilder = nextPathBuilder)
  }

  def setX(x: T): TurtlePathBuilder[T] = goto(x, turtleState.y)

  def setY(y: T): TurtlePathBuilder[T] = goto(turtleState.x, y)

  def penUp(): TurtlePathBuilder[T] = this.copy(turtleState = turtleState.copy(penDown = false))

  def penDown(): TurtlePathBuilder[T] = this.copy(turtleState = turtleState.copy(penDown = true))

  def showTurtle(): TurtlePathBuilder[T] = this.copy(turtleState = turtleState.copy(visible = true))

  def hideTurtle(): TurtlePathBuilder[T] = this.copy(turtleState = turtleState.copy(visible = false))

  def clear(): TurtlePathBuilder[T] = {
    val atCurrent = Point[T](turtleState.x, turtleState.y)
    this.copy(svgPathBuilder = SvgPathBuilderImmutable[T](atCurrent))
  }

  def home(): TurtlePathBuilder[T] = {
    val moved = goto(zero, zero)
    moved.copy(turtleState = moved.turtleState.copy(headingDeg = zero))
  }

  def reset(): TurtlePathBuilder[T] = TurtlePathBuilder[T]()

  def dot(size: T): TurtlePathBuilder[T] = {
    this.copy(svgPathBuilder = svgPathBuilder.addCenteredCircle(size / fromInt(2)).asInstanceOf[SvgPathBuilderImmutable[T]])
  }

  def circle(radius: T): TurtlePathBuilder[T] = {
    this.copy(svgPathBuilder = svgPathBuilder.addCenteredCircle(radius).asInstanceOf[SvgPathBuilderImmutable[T]])
  }

  def getX(): T = turtleState.x

  def handleStringCommand(turtleCommand: TurtleCommand[T]): TurtlePathBuilder[T] = {
    // take into accounts synonyms and MAYBE other languages like German
    val name = turtleCommand.name.trim.toLowerCase
    name match {
      case "forward" | "fd" =>
        turtleCommand.args.headOption.map(forward).getOrElse(this)
      case "backward" | "back" | "bk" =>
        turtleCommand.args.headOption.map(backward).getOrElse(this)
      case "left" | "lt" =>
        turtleCommand.args.headOption.map(left).getOrElse(this)
      case "right" | "rt" =>
        turtleCommand.args.headOption.map(right).getOrElse(this)
      case "goto" | "setpos" | "setposition" =>
        if (turtleCommand.args.size >= 2) goto(turtleCommand.args(0), turtleCommand.args(1)) else this
      case "setheading" | "seth" =>
        turtleCommand.args.headOption.map(setHeading).getOrElse(this)
      case "towards" =>
        this
      case "setx" =>
        turtleCommand.args.headOption.map(setX).getOrElse(this)
      case "sety" =>
        turtleCommand.args.headOption.map(setY).getOrElse(this)
      case "home" => home()
      case "reset" => reset()
      case "clear" | "clearscreen" => clear()
      case "penup" | "pu" | "up" =>
        penUp()
      case "pendown" | "pd" | "down" =>
        penDown()
      case "showturtle" | "st" => showTurtle()
      case "hideturtle" | "ht" => hideTurtle()
      case "dot" => turtleCommand.args.headOption.map(dot).getOrElse(this)
      case "circle" => turtleCommand.args.headOption.map(circle).getOrElse(this)
      case _ =>
        this
    } match {
      case same if same.eq(this) && name != "" =>
        this.copy(turtleCommands = turtleCommands :+ turtleCommand)
      case updated =>
        updated.copy(turtleCommands = updated.turtleCommands :+ turtleCommand)
    }
  }

}

object TurtlePathBuilder {

  case class TurtleCommand[T: Fractional](name: String, args: List[T])

  case class TurtleState[T: Fractional](x: T, y: T, headingDeg: T, penDown: Boolean, visible: Boolean)

  def apply[T: Fractional](startPoint: Point[T], turtleCommands: List[TurtleCommand[T]]): TurtlePathBuilder[T] = {
    turtleCommands.foldLeft(TurtlePathBuilder[T](startPoint, TurtleState(startPoint.x, startPoint.y, summon[Fractional[T]].fromInt(0), penDown = true, visible = true), List(), SvgPathBuilderImmutable[T](startPoint))) {
      case (builder, cmd) => builder.handleStringCommand(cmd)
    }
  }

  def apply[T: Fractional](): TurtlePathBuilder[T] = {
    lazy val zero: T = summon[Fractional[T]].fromInt(0)
    val start = Point[T](zero, zero)
    TurtlePathBuilder[T](start, TurtleState[T](zero, zero, zero, penDown = true, visible = true), List(), SvgPathBuilderImmutable[T](start))
  }



}
