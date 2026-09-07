package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderCommand.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.{TurtleCommand, TurtlePenStyle, TurtleState, TurtleStyledSegment}

case class TurtlePathBuilder[T: Fractional](
                                             startPoint: Point[T],
                                             turtleState: TurtleState[T],
                                             turtleCommands: List[TurtleCommand[T]],
                                             svgPathBuilder: SvgPathBuilderImmutable[T],
                                             styledSegments: List[TurtleStyledSegment[T]] = Nil,
                                             currentSegmentPath: SvgPathBuilderImmutable[T]
                                           ) {

  def pathBuilderCommands: List[SvgPathBuilderCommand[T]] = svgPathBuilder.furtherCommands

  val N = summon[Fractional[T]];

  import N.*

  lazy val zero = fromInt(0)

  def currentPenStyle: TurtlePenStyle[T] =
    TurtlePenStyle(turtleState.penColor, turtleState.penSize)

  def completedStyledSegments: List[TurtleStyledSegment[T]] =
    flushCurrentSegment.styledSegments

  private def asImmutable(builder: SvgPathBuilder[T]): SvgPathBuilderImmutable[T] =
    builder.asInstanceOf[SvgPathBuilderImmutable[T]]

  private def appendGeometry(endPoint: Point[T], draw: Boolean): TurtlePathBuilder[T] = {
    val applyOp = (path: SvgPathBuilderImmutable[T]) =>
      if draw then asImmutable(path.lineToAbs(endPoint)) else asImmutable(path.moveToAbs(endPoint))
    copy(
      turtleState = turtleState.copy(x = endPoint.x, y = endPoint.y),
      svgPathBuilder = applyOp(svgPathBuilder),
      currentSegmentPath = applyOp(currentSegmentPath)
    )
  }

  private def currentSegmentHasStroke: Boolean =
    currentSegmentPath.furtherCommands.exists {
      case _: MoveAbs[?] | _: MoveRel[?] => false
      case _ => true
    }

  def flushCurrentSegment: TurtlePathBuilder[T] = {
    if !currentSegmentHasStroke then this
    else
      val atCurrent = Point[T](turtleState.x, turtleState.y)
      copy(
        styledSegments = styledSegments :+ TurtleStyledSegment(currentSegmentPath, currentPenStyle),
        currentSegmentPath = SvgPathBuilderImmutable[T](atCurrent)
      )
  }

  private def updatePenStyle(color: String, size: T): TurtlePathBuilder[T] =
    if turtleState.penColor == color && turtleState.penSize == size then this
    else
      val flushed = flushCurrentSegment
      flushed.copy(turtleState = flushed.turtleState.copy(penColor = color, penSize = size))

  private def snapNearZero(value: Double): Double =
    if math.abs(value) < 1e-10 then 0.0 else value

  private def wrapHeading(degrees: T): T = {
    val wrapped = ((degrees.toDouble % 360) + 360) % 360
    SvgPathBuilder.fromDouble[T](wrapped)
  }

  def forward(distance: T): TurtlePathBuilder[T] = {
    val headingRad = Math.toRadians(turtleState.headingDeg.toDouble)
    val nextX = snapNearZero(turtleState.x.toDouble + (Math.cos(headingRad) * distance.toDouble))
    // SVG y-axis grows downward, while turtle math usually grows upward.
    val nextY = snapNearZero(turtleState.y.toDouble - (Math.sin(headingRad) * distance.toDouble))
    val endPoint = Point[T](SvgPathBuilder.fromDouble[T](nextX), SvgPathBuilder.fromDouble[T](nextY))
    appendGeometry(endPoint, turtleState.penDown)
  }

  def left(rotationDeg: T): TurtlePathBuilder[T] =
    this.copy(turtleState = turtleState.copy(headingDeg = wrapHeading(turtleState.headingDeg + rotationDeg)))

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
    this.copy(turtleState = turtleState.copy(headingDeg = wrapHeading(degrees)))

  def goto(x: T, y: T): TurtlePathBuilder[T] = {
    val endPoint = Point[T](x, y)
    appendGeometry(endPoint, turtleState.penDown)
  }

  def setX(x: T): TurtlePathBuilder[T] = goto(x, turtleState.y)

  def setY(y: T): TurtlePathBuilder[T] = goto(turtleState.x, y)

  def penUp(): TurtlePathBuilder[T] = this.copy(turtleState = turtleState.copy(penDown = false))

  def penDown(): TurtlePathBuilder[T] = this.copy(turtleState = turtleState.copy(penDown = true))

  def showTurtle(): TurtlePathBuilder[T] = this.copy(turtleState = turtleState.copy(visible = true))

  def hideTurtle(): TurtlePathBuilder[T] = this.copy(turtleState = turtleState.copy(visible = false))

  def setPenColor(color: String): TurtlePathBuilder[T] =
    updatePenStyle(color, turtleState.penSize)

  def setPenSize(size: T): TurtlePathBuilder[T] =
    updatePenStyle(turtleState.penColor, size)

  def setStitchMode(mode: String): TurtlePathBuilder[T] =
    this.copy(turtleState = turtleState.copy(stitchMode = mode))

  def clear(): TurtlePathBuilder[T] = {
    val atCurrent = Point[T](turtleState.x, turtleState.y)
    this.copy(
      svgPathBuilder = SvgPathBuilderImmutable[T](atCurrent),
      styledSegments = Nil,
      currentSegmentPath = SvgPathBuilderImmutable[T](atCurrent)
    )
  }

  def home(): TurtlePathBuilder[T] = {
    val moved = goto(zero, zero)
    moved.copy(turtleState = moved.turtleState.copy(headingDeg = turtleState.homeHeadingDeg))
  }

  def reset(): TurtlePathBuilder[T] = TurtlePathBuilder[T]()

  def dot(size: T): TurtlePathBuilder[T] = {
    this.copy(svgPathBuilder = svgPathBuilder.addCenteredCircle(size / fromInt(2)).asInstanceOf[SvgPathBuilderImmutable[T]])
  }

  /** Walk a circle like Python turtle (center to the left of the current heading). */
  def circle(radius: T): TurtlePathBuilder[T] = circle(radius, fromInt(360))

  def circle(radius: T, extentDeg: T): TurtlePathBuilder[T] = {
    val extent = extentDeg.toDouble
    val steps = math.max(8, math.round(math.abs(extent) / 10.0).toInt)
    val stepLen = (2 * Math.PI * radius.toDouble) * (extent / 360.0) / steps
    val stepAngle = extent / steps
    val lenT = SvgPathBuilder.fromDouble[T](stepLen)
    val angT = SvgPathBuilder.fromDouble[T](stepAngle)
    (0 until steps).foldLeft(this) { (builder, _) =>
      builder.forward(lenT).left(angT)
    }
  }

  def arcLeft(radius: T, extentDeg: T): TurtlePathBuilder[T] = circle(radius, extentDeg)

  def arcRight(radius: T, extentDeg: T): TurtlePathBuilder[T] = {
    val extent = extentDeg.toDouble
    val steps = math.max(8, math.round(math.abs(extent) / 10.0).toInt)
    val stepLen = (2 * Math.PI * radius.toDouble) * (extent / 360.0) / steps
    val stepAngle = extent / steps
    val lenT = SvgPathBuilder.fromDouble[T](stepLen)
    val angT = SvgPathBuilder.fromDouble[T](stepAngle)
    (0 until steps).foldLeft(this) { (builder, _) =>
      builder.forward(lenT).right(angT)
    }
  }

  def getX(): T = turtleState.x

  def handleStringCommand(turtleCommand: TurtleCommand[T]): TurtlePathBuilder[T] = {
    val name = turtleCommand.name.trim.toLowerCase.replace("-", "_")
    name match {
      case "forward" | "fd" =>
        turtleCommand.args.headOption.map(forward).getOrElse(this)
      case "backward" | "back" | "bk" =>
        turtleCommand.args.headOption.map(backward).getOrElse(this)
      case "left" | "lt" | "turn_left" | "turnleft" =>
        turtleCommand.args.headOption.map(left).getOrElse(this)
      case "right" | "rt" | "turn" =>
        turtleCommand.args.headOption.map(right).getOrElse(this)
      case "goto" | "setpos" | "setposition" | "goto_x_y" | "gotoxy" =>
        if (turtleCommand.args.size >= 2) goto(turtleCommand.args(0), turtleCommand.args(1)) else this
      case "setheading" | "seth" | "set_heading" =>
        turtleCommand.args.headOption.map(setHeading).getOrElse(this)
      case "towards" =>
        this
      case "setx" | "set_x" | "setxposition" =>
        turtleCommand.args.headOption.map(setX).getOrElse(this)
      case "sety" | "set_y" | "setyposition" =>
        turtleCommand.args.headOption.map(setY).getOrElse(this)
      case "home" => home()
      case "reset" => reset()
      case "clear" | "clearscreen" => clear()
      case "penup" | "pu" | "up" | "pen_up" =>
        penUp()
      case "pendown" | "pd" | "down" | "pen_down" =>
        penDown()
      case "showturtle" | "st" => showTurtle()
      case "hideturtle" | "ht" => hideTurtle()
      case "dot" => turtleCommand.args.headOption.map(dot).getOrElse(this)
      case "circle" =>
        turtleCommand.args match
          case radius :: extent :: _ => circle(radius, extent)
          case radius :: Nil => circle(radius)
          case _ => this
      case "arc" | "arcleft" | "arc_left" =>
        if turtleCommand.args.size >= 2 then arcLeft(turtleCommand.args(0), turtleCommand.args(1)) else this
      case "arcright" | "arc_right" =>
        if turtleCommand.args.size >= 2 then arcRight(turtleCommand.args(0), turtleCommand.args(1)) else this
      case "color" | "pencolor" | "setcolor" =>
        val color =
          turtleCommand.stringArgs.headOption.orElse {
            if turtleCommand.args.size >= 3 then
              Some(s"rgb(${turtleCommand.args(0).toDouble.toInt},${turtleCommand.args(1).toDouble.toInt},${turtleCommand.args(2).toDouble.toInt})")
            else None
          }
        color.map(setPenColor).getOrElse(this)
      case "pensize" | "setsize" | "width" =>
        turtleCommand.args.headOption.map(setPenSize).getOrElse(this)
      case "running_stitch" | "runningstitch" | "cross_stitch" | "crossstitch" |
           "bean_stitch" | "beanstitch" | "zigzag_stitch" | "zigzagstitch" |
           "z_stitch" | "zstitch" | "satin_stitch" | "satinstitch" |
           "tatami_stitch" | "tatamistitch" | "jump_stitch" | "jumpstitch" |
           "tie_stitch" | "tiestitch" | "trim" | "trim_stitch" | "trimstitch" |
           "stop_running" | "stoprunning" =>
        setStitchMode(name)
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

  case class TurtleCommand[T: Fractional](name: String, args: List[T] = Nil, stringArgs: List[String] = Nil)

  case class TurtlePenStyle[T](color: String, size: T)

  case class TurtleStyledSegment[T: Fractional](pathBuilder: SvgPathBuilderImmutable[T], style: TurtlePenStyle[T])

  case class TurtleState[T: Fractional](
      x: T,
      y: T,
      headingDeg: T,
      penDown: Boolean,
      visible: Boolean,
      penColor: String,
      penSize: T,
      stitchMode: String,
      homeHeadingDeg: T
  )

  object TurtleState {
    def initial[T: Fractional](
        x: T,
        y: T,
        headingDeg: T,
        penDown: Boolean = true,
        visible: Boolean = true
    ): TurtleState[T] = {
      val N = summon[Fractional[T]]
      TurtleState(
        x,
        y,
        headingDeg,
        penDown,
        visible,
        penColor = "black",
        penSize = N.fromInt(1),
        stitchMode = "running",
        homeHeadingDeg = headingDeg
      )
    }
  }

  def apply[T: Fractional](startPoint: Point[T], turtleCommands: List[TurtleCommand[T]]): TurtlePathBuilder[T] =
    apply(startPoint, turtleCommands, summon[Fractional[T]].fromInt(0))

  def apply[T: Fractional](
      startPoint: Point[T],
      turtleCommands: List[TurtleCommand[T]],
      initialHeadingDeg: T
  ): TurtlePathBuilder[T] = {
    val initialPath = SvgPathBuilderImmutable[T](startPoint)
    val initial = TurtlePathBuilder[T](
      startPoint,
      TurtleState.initial(startPoint.x, startPoint.y, initialHeadingDeg),
      List(),
      initialPath,
      Nil,
      initialPath
    )
    turtleCommands.foldLeft(initial) { case (builder, cmd) => builder.handleStringCommand(cmd) }
  }

  def apply[T: Fractional](): TurtlePathBuilder[T] = {
    lazy val zero: T = summon[Fractional[T]].fromInt(0)
    val start = Point[T](zero, zero)
    val initialPath = SvgPathBuilderImmutable[T](start)
    TurtlePathBuilder[T](start, TurtleState.initial(zero, zero, zero), List(), initialPath, Nil, initialPath)
  }

}
