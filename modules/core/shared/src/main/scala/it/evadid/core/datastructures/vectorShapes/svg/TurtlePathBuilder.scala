package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderCommand.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.{TurtlePenStyle, TurtleState, TurtleStyledSegment}
import it.evadid.core.util.io.serializer.AutoSerializable.*

import upickle.ReadWriter
import upickle.default.*

case class TurtlePathBuilder[T : Fractional](
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

  private def appendGeometry(endPoint: Point[T], draw: Boolean): TurtlePathBuilder[T] = {
    val applyOp = (path: SvgPathBuilderImmutable[T]) =>
      if draw then path.lineToAbs(endPoint) else path.moveToAbs(endPoint)
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
    this.copy(svgPathBuilder = svgPathBuilder.addCenteredCircle(size / fromInt(2)))
  }

  /** Walk a circle like Python turtle (center to the left of the current heading). */
  def circle(radius: T): TurtlePathBuilder[T] = circle(radius, fromInt(360))

  def circle(radius: T, extentDeg: T): TurtlePathBuilder[T] = drawArc(radius, extentDeg, turnLeft = true)

  def arcLeft(radius: T, extentDeg: T): TurtlePathBuilder[T] = drawArc(radius, extentDeg, turnLeft = true)

  def arcRight(radius: T, extentDeg: T): TurtlePathBuilder[T] = drawArc(radius, extentDeg, turnLeft = false)

  private def drawArc(radius: T, extentDeg: T, turnLeft: Boolean): TurtlePathBuilder[T] = {
    val extent = extentDeg.toDouble
    val steps = math.max(8, math.round(math.abs(extent) / 10.0).toInt)
    val length = SvgPathBuilder.fromDouble[T]((2 * Math.PI * radius.toDouble) * (extent / 360.0) / steps)
    val rotation = SvgPathBuilder.fromDouble[T](extent / steps * (if turnLeft then 1 else -1))
    (0 until steps).foldLeft(this)((builder, _) => builder.forward(length).left(rotation))
  }

  def getX(): T = turtleState.x

  def handleCommand(command: TurtleCommand[T]): TurtlePathBuilder[T] = {
    import TurtleCommand.*
    val updated = command match {
      case Forward(distance) => forward(distance)
      case Backward(distance) => backward(distance)
      case Left(degrees) => left(degrees)
      case Right(degrees) => right(degrees)
      case SetHeading(degrees) => setHeading(degrees)
      case SetX(x) => setX(x)
      case SetY(y) => setY(y)
      case Dot(size) => dot(size)
      case PenSize(size) => setPenSize(size)
      case Goto(x, y) => goto(x, y)
      case ArcLeft(radius, extent) => arcLeft(radius, extent)
      case ArcRight(radius, extent) => arcRight(radius, extent)
      case Circle(radius, extent, _) => circle(radius, extent.getOrElse(fromInt(360)))
      case Color(color) => setPenColor(color)
      case Stitch(mode, _, _) => setStitchMode(mode.name.universalInterpretation())
      case PenUp() => penUp()
      case PenDown() => penDown()
      case ShowTurtle() => showTurtle()
      case HideTurtle() => hideTurtle()
      case Home() => home()
      case Reset() => reset()
      case Clear() => clear()
      case Towards(_, _) | Speed(_) | Unsupported(_, _, _) => this
    }
    updated.copy(turtleCommands = updated.turtleCommands :+ command)
  }

}

object TurtlePathBuilder {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[TurtlePathBuilder[T]] =
    upickle.default.readwriter[(Point[T], TurtleState[T], List[TurtleCommand[T]], SvgPathBuilderImmutable[T], List[TurtleStyledSegment[T]], SvgPathBuilderImmutable[T])].bimap(value => (value.startPoint, value.turtleState, value.turtleCommands, value.svgPathBuilder, value.styledSegments, value.currentSegmentPath), value => TurtlePathBuilder[T](value._1, value._2, value._3, value._4, value._5, value._6))


  case class TurtlePenStyle[T](color: String, size: T) derives upickle.default.ReadWriter

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
    given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[TurtleState[T]] =
      upickle.default.readwriter[(T, T, T, Boolean, Boolean, String, T, String, T)].bimap(value => (value.x, value.y, value.headingDeg, value.penDown, value.visible, value.penColor, value.penSize, value.stitchMode, value.homeHeadingDeg), value => TurtleState[T](value._1, value._2, value._3, value._4, value._5, value._6, value._7, value._8, value._9))

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
    turtleCommands.foldLeft(initial) { case (builder, cmd) => builder.handleCommand(cmd) }
  }

  def apply[T: Fractional](): TurtlePathBuilder[T] = {
    lazy val zero: T = summon[Fractional[T]].fromInt(0)
    val start = Point[T](zero, zero)
    val initialPath = SvgPathBuilderImmutable[T](start)
    TurtlePathBuilder[T](start, TurtleState.initial(zero, zero, zero), List(), initialPath, Nil, initialPath)
  }


  object TurtleStyledSegment {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[TurtleStyledSegment[T]] =
    upickle.default.readwriter[(SvgPathBuilderImmutable[T], TurtlePenStyle[T])].bimap(value => (value.pathBuilder, value.style), value => TurtleStyledSegment[T](value._1, value._2))
  }
}
