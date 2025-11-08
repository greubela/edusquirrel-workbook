package contentmanagement.webElements.svg.builder

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.atomarElements.AppPathSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilderCommand.*
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeAtomic
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

trait SvgPathBuilder[T: Fractional] {

  def fromDouble(nr: Double): T = {
    val N = summon[Fractional[T]];
    import N.*
    lazy val thousand = fromInt(1000)
    lazy val convertedFromInt: T = fromInt((nr * 1000).toInt) / fromInt(1000)
    N.parseString(nr.toString).getOrElse(convertedFromInt)
  }

  // ---- Introspection / results ----
  def absStartPoint: Point[T]

  def toSvgPathD: String

  def current: Point[T]

  def pathPoints: List[Point[T]]

  lazy val requiresDimension: Dimension[T] = Bounds.thatContainsAll(pathPoints).dimension

  // M / m
  def moveToAbs(endPoint: Point[T]): SvgPathBuilder[T]

  def moveToRel(dimension: Dimension[T]): SvgPathBuilder[T]

  // Z / z
  def closePath(): SvgPathBuilder[T]

  // L / l
  def lineToAbs(point: Point[T]): SvgPathBuilder[T]

  def lineToRel(dimension: Dimension[T]): SvgPathBuilder[T]

  // H / V (modeled in your API as relative widths/heights)
  def horizontalLineWithWidth(width: T): SvgPathBuilder[T]

  def verticalLineWithHeight(height: T): SvgPathBuilder[T]

  // C / c
  def cubicBezierToAbs(
                        controlPointStart: Point[T],
                        controlPointEnd: Point[T],
                        endPoint: Point[T]
                      ): SvgPathBuilder[T]

  def cubicBezierToRel(
                        controlDimensionStart: Dimension[T],
                        controlDimensionEnd: Dimension[T],
                        endPointDimension: Dimension[T]
                      ): SvgPathBuilder[T]

  // Q / q
  def quadraticBezierToAbs(
                            controlPoint: Point[T],
                            endPoint: Point[T]
                          ): SvgPathBuilder[T]

  def quadraticBezierWithRel(
                              controlDimension: Dimension[T],
                              endPointDimension: Dimension[T]
                            ): SvgPathBuilder[T]

  // a (your convenience helpers)
  def addArcToTheTopMoveRight(radius: T): SvgPathBuilder[T]

  def addArcToTheRightMoveBottom(radius: T): SvgPathBuilder[T]

  // convenience macros
  def addCenteredCircle(radius: T): SvgPathBuilder[T]

  def drawBoundRectangle(bounds: Bounds[T]): SvgPathBuilder[T]

  def addControlFlowConnector(segmentWidth: T, invertHeight: Boolean = false): SvgPathBuilder[T]

  // whole-shape transform
  def moveWholePath(dimension: Dimension[T]): SvgPathBuilder[T]

  private lazy val renderDirectly: AppSvgElement = {
    //val controlLines = absoluteCommands.flatMap(_.controlPointsAbsolute) // todo: fix to re-introduce control lines
    AppPathSvgElement[T](toSvgPathD, pathPoints, List())
  }

  def toFixedDimensionShape: BeShapeAtomic = new BeShapeAtomic {
    override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = {
      requiresDimension.toDouble
    }

    override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
      //moveWholePath(Dimension(fromDouble(bounds.startPoint.x), fromDouble(bounds.startPoint.y))).
      renderDirectly
    }
  }

}

object SvgPathBuilder {

  def apply[T: Fractional](startPoint: Point[T]): SvgPathBuilder[T] = immutableBuilder(startPoint)

  def mutableBuilder[T: Fractional](startPoint: Point[T]): SvgPathBuilderMutable[T] = SvgPathBuilderMutable[T](startPoint)

  def immutableBuilder[T: Fractional](startPoint: Point[T]): SvgPathBuilderImmutable[T] = SvgPathBuilderImmutable[T](StartPathCommand(startPoint), List())

}


/*
object SvgPathBuilder {

  trait SvgPathbuilderCommand[T: Fractional]() {
    def controlPointsAbsolute: List[Point[T]] = List()
    def getPathDString(): String

    def toRelativeCommand(startPosition: Point[T]): RelativeCommand[T]
    def toAbsoluteCommand(startPosition: Point[T]): AbsoluteCommand[T]

  }

  trait AbsoluteCommand[T: Fractional]() extends SvgPathbuilderCommand[T] {
    def positionAfterCommand: Point[T]
  }

  trait RelativeCommand[T: Fractional]() extends SvgPathbuilderCommand[T] {
      def relativeMovement: Dimension[T]
  }

  case class AddControlLinesCommand[T: Fractional](predecessorPos: Point[T], override val controlPointsAbsolute: List[Point[T]]) extends RelativeCommand[T], AbsoluteCommand[T] {
    def getPathDString(): String = ""

    def toRelativeCommand(startPosition: Point[T]): RelativeCommand[T] = this
    def toAbsoluteCommand(startPosition: Point[T]): AbsoluteCommand[T] = this

    def positionAfterCommand: Point[T] = predecessorPos
    def relativeMovement: Dimension[T] = Dimension[T](0,0)
  }


  case class StartPathCommand[T: Fractional](absoluteStartPos: Point[T]) {
    def startCommand(): String = {
      s"M ${absoluteStartPos.toDouble.x} ${absoluteStartPos.toDouble.y}"
    }
  }

  case class MoveCommand[T: Fractional](override val relativeMovement: Dimension[T]) extends SvgPathbuilderCommand[T] {
    def toRelativeCommandString(): String = s"m ${relativeMovement.toDouble.width} ${relativeMovement.toDouble.height}"

    def toAbsoluteCommandString(lastAbsPos: Point[T]): String = {
      val curDestPos = newAbsolutePos(lastAbsPos)
      s"M ${curDestPos.toDouble.x} ${curDestPos.toDouble.y}"
    }
  }

}

case class SvgPathBuilder[T: Fractional](startCommand: StartPathCommand[T], furtherCommands: List[SvgPathbuilderCommand[T]]) {

  def pathD: String = ???

  def moveToRel(dimension: Dimension[T]): SvgPathBuilder[T] = {
    SvgPathBuilder[T](startCommand, furtherCommands :+ MoveCommand(dimension))
  }

  def moveToAbs(endPoint: Point[T]): SvgPathBuilder[T] = ???

  def moveWholePath(dimension: Dimension[T]): SvgPathBuilder[T] = {
    SvgPathBuilder[T](StartPathCommand[T](startCommand.absoluteStartPos.moveWithDimension(dimension)), furtherCommands)
  }
}


*/