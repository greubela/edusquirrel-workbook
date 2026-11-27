package it.evadid.core.datastructures.vectorShapes.blocks

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder


/** Control-flow arrows, connectors and backgrounds, scaled by the control-flow segment size. */
case class FlowDecorationPaths[T: Fractional](segmentSize: T) {
  val N = summon[Fractional[T]]

  import N.*

  private def intToSeg(nr: Int): T = segmentSize / fromInt(5) * fromInt(nr)

  private def tToSeg(nr: T): T = segmentSize / fromInt(5) * nr

  private def fromTenths(value: Int): T = fromInt(value) / fromInt(10)

  private def tenthsToSeg(value: Int): T = tToSeg(fromTenths(value))

  private def segDim(w: Int, h: Int): Dimension[T] = Dimension(intToSeg(w), intToSeg(h))

  private def segDim(w: T, h: T): Dimension[T] = Dimension(tToSeg(w), tToSeg(h))

  private def tenthsDim(w: Int, h: Int): Dimension[T] = segDim(fromTenths(w), fromTenths(h))

  val one: T = fromInt(1)
  val two: T = fromInt(2)
  val half: T = one / two

  def controlArrowRight(centeredAround: Point[T]): SvgPathBuilder[T] = {
    SvgPathBuilder(centeredAround)
      .moveToRel(segDim(-3, 0))
      .lineToRel(segDim(-one - half, one + half))
      .horizontalLineWithWidth(intToSeg(6))
      .verticalLineWithHeight(tToSeg(one + half))
      .lineToRel(segDim(3, -3))
      .lineToRel(segDim(-3, -3))
      .verticalLineWithHeight(tToSeg(one + half))
      .horizontalLineWithWidth(intToSeg(-6))
      .lineToRel(segDim(one + half, one + half))
  }

  def controlArrowLeft(centeredAround: Point[T]): SvgPathBuilder[T] = {
    SvgPathBuilder(centeredAround)
      .moveToRel(segDim(3, 0))
      .lineToRel(segDim(one + half, one + half))
      .horizontalLineWithWidth(intToSeg(-6))
      .verticalLineWithHeight(tToSeg(one + half))
      .lineToRel(segDim(-3, -3))
      .lineToRel(segDim(3, -3))
      .verticalLineWithHeight(tToSeg(one + half))
      .horizontalLineWithWidth(intToSeg(6))
      .lineToRel(segDim(-one - half, one + half))
  }

  def controlArrowDown(centeredAround: Point[T]): SvgPathBuilder[T] = {
    SvgPathBuilder(centeredAround)
      .moveToRel(segDim(0, -3))
      .lineToRel(segDim(one + half, -one - half))
      .verticalLineWithHeight(intToSeg(6))
      .horizontalLineWithWidth(tToSeg(one + half))
      .lineToRel(segDim(-3, 3))
      .lineToRel(segDim(-3, -3))
      .horizontalLineWithWidth(tToSeg(one + half))
      .verticalLineWithHeight(intToSeg(-6))
      .lineToRel(segDim(one + half, one + half))
  }

  def controlArrowCrossDown(centeredAround: Point[T]): SvgPathBuilder[T] = {
    /*
    M28.6,132.5l-1.1,1.1l1.4,1.4l-0.8,0.8l-0.6,-0.5v2.2h2.2l-0.6,-0.6l0.8,-0.8l0.8,0.8l-0.4,0.6h2.2v-2.2l-0.6,0.6l-0.8,-0.8l1.4,-1.5l-1.1,-1.1l-1.4,1.4z
     */
    // todo write this correctly
    ???
  }

  def controlArrowUp(centeredAround: Point[T]): SvgPathBuilder[T] = {
    SvgPathBuilder(centeredAround)
      .moveToRel(segDim(0, 3))
      .lineToRel(segDim(one + half, one + half))
      .verticalLineWithHeight(intToSeg(-6))
      .horizontalLineWithWidth(tToSeg(one + half))
      .lineToRel(segDim(-3, -3))
      .lineToRel(segDim(-3, 3))
      .horizontalLineWithWidth(tToSeg(one + half))
      .verticalLineWithHeight(intToSeg(6))
      .lineToRel(segDim(one + half, -one - half))
  }

  def controlFlowSplitDown(centeredAround: Point[T], cleanOrigin: Boolean): SvgPathBuilder[T] = {
    val basePath =
      SvgPathBuilder(centeredAround)
        .moveToRel(tenthsDim(-8, -25))
        .verticalLineWithHeight(tenthsToSeg(22))
        .lineToRel(tenthsDim(-3, 3))
        .lineToRel(tenthsDim(-8, 8))
        .lineToRel(tenthsDim(-6, -5))
        .verticalLineWithHeight(tenthsToSeg(22))
        .horizontalLineWithWidth(tenthsToSeg(22))
        .lineToRel(tenthsDim(-5, -6))
        .lineToRel(tenthsDim(8, -8))
        .lineToRel(tenthsDim(8, 8))
        .lineToRel(tenthsDim(-5, 6))
        .horizontalLineWithWidth(tenthsToSeg(22))
        .verticalLineWithHeight(tenthsToSeg(-22))
        .lineToRel(tenthsDim(-6, 5))
        .lineToRel(tenthsDim(-8, -8))
        .lineToRel(tenthsDim(-3, -3))
        .verticalLineWithHeight(tenthsToSeg(-22))

    val withOptionalOrigin =
      if (cleanOrigin) basePath
      else basePath.lineToRel(tenthsDim(-8, 8))

    withOptionalOrigin.closePath()
  }

  def controlFlowSplitUp(centeredAround: Point[T], cleanOrigin: Boolean): SvgPathBuilder[T] = {
    val basePath =
      SvgPathBuilder(centeredAround)
        .moveToRel(tenthsDim(-8, 25))
        .verticalLineWithHeight(tenthsToSeg(-22))
        .lineToRel(tenthsDim(-3, -3))
        .lineToRel(tenthsDim(-8, -8))
        .lineToRel(tenthsDim(-6, 5))
        .verticalLineWithHeight(tenthsToSeg(-22))
        .horizontalLineWithWidth(tenthsToSeg(22))
        .lineToRel(tenthsDim(-5, 6))
        .lineToRel(tenthsDim(8, 8))
        .lineToRel(tenthsDim(8, -8))
        .lineToRel(tenthsDim(-5, -6))
        .horizontalLineWithWidth(tenthsToSeg(22))
        .verticalLineWithHeight(tenthsToSeg(22))
        .lineToRel(tenthsDim(-6, -5))
        .lineToRel(tenthsDim(-8, 8))
        .lineToRel(tenthsDim(-3, 3))
        .verticalLineWithHeight(tenthsToSeg(22))

    val withOptionalOrigin =
      if (cleanOrigin) basePath
      else basePath.lineToRel(tenthsDim(-8, -8))

    withOptionalOrigin.closePath()
  }

  /** A cross or union junction: the corner at the origin is cut diagonally when `cleanOrigin`, otherwise square. */
  private def junction(centeredAround: Point[T], cleanOrigin: Boolean, goingDown: Boolean)
                      (body: SvgPathBuilder[T] => SvgPathBuilder[T]): SvgPathBuilder[T] = {
    val sign = if (goingDown) 1 else -1
    val start = SvgPathBuilder(centeredAround).moveToRel(tenthsDim(-14, -25 * sign))
    val entered =
      if (cleanOrigin) start.lineToRel(tenthsDim(-11, 11 * sign))
      else start.verticalLineWithHeight(tenthsToSeg(11 * sign)).horizontalLineWithWidth(tenthsToSeg(-11))
    val drawn = body(entered)
    val exited =
      if (cleanOrigin) drawn.lineToRel(tenthsDim(-11, -11 * sign))
      else drawn.horizontalLineWithWidth(tenthsToSeg(-11)).verticalLineWithHeight(tenthsToSeg(-11 * sign))
    exited.lineToRel(tenthsDim(-14, 14 * sign)).closePath()
  }

  def controlFlowCrossDown(centeredAround: Point[T], cleanOrigin: Boolean): SvgPathBuilder[T] =
    junction(centeredAround, cleanOrigin, goingDown = true) { path =>
      path
        .lineToRel(tenthsDim(14, 14))
        .lineToRel(tenthsDim(-8, 8))
        .lineToRel(tenthsDim(-6, -6))
        .verticalLineWithHeight(tenthsToSeg(23))
        .horizontalLineWithWidth(tenthsToSeg(22))
        .lineToRel(tenthsDim(-6, -6))
        .lineToRel(tenthsDim(8, -8))
        .lineToRel(tenthsDim(8, 8))
        .lineToRel(tenthsDim(-4, 6))
        .horizontalLineWithWidth(tenthsToSeg(22))
        .verticalLineWithHeight(tenthsToSeg(-22))
        .lineToRel(tenthsDim(-6, 5))
        .lineToRel(tenthsDim(-8, -8))
        .lineToRel(tenthsDim(14, -14))
    }

  def controlFlowCrossUp(centeredAround: Point[T], cleanOrigin: Boolean): SvgPathBuilder[T] =
    junction(centeredAround, cleanOrigin, goingDown = false) { path =>
      path
        .lineToRel(tenthsDim(14, -14))
        .lineToRel(tenthsDim(-8, -8))
        .lineToRel(tenthsDim(-6, 6))
        .verticalLineWithHeight(tenthsToSeg(-23))
        .horizontalLineWithWidth(tenthsToSeg(22))
        .lineToRel(tenthsDim(-6, 6))
        .lineToRel(tenthsDim(8, 8))
        .lineToRel(tenthsDim(8, -8))
        .lineToRel(tenthsDim(-4, -6))
        .horizontalLineWithWidth(tenthsToSeg(22))
        .verticalLineWithHeight(tenthsToSeg(22))
        .lineToRel(tenthsDim(-6, -5))
        .lineToRel(tenthsDim(-8, 8))
        .lineToRel(tenthsDim(14, 14))
    }

  def controlFlowUnionDown(centeredAround: Point[T], cleanOrigin: Boolean): SvgPathBuilder[T] = {
    // The square-origin variant draws the short vertical steps as relative lines.
    def verticalStep(path: SvgPathBuilder[T], tenths: Int): SvgPathBuilder[T] =
      if (cleanOrigin) path.verticalLineWithHeight(tenthsToSeg(tenths))
      else path.lineToRel(segDim(zero, fromTenths(tenths)))

    junction(centeredAround, cleanOrigin, goingDown = true) { path =>
      val toArrowBase = verticalStep(path.lineToRel(tenthsDim(14, 14)).lineToRel(tenthsDim(3, 3)), 5)
        .horizontalLineWithWidth(tenthsToSeg(-9))
        .lineToRel(tenthsDim(17, 17))
        .lineToRel(tenthsDim(17, -17))
        .horizontalLineWithWidth(tenthsToSeg(-9))
      verticalStep(toArrowBase, -6)
        .lineToRel(tenthsDim(3, -3))
        .lineToRel(tenthsDim(14, -13))
    }
  }

  def controlFlowUnionUp(centeredAround: Point[T], cleanOrigin: Boolean): SvgPathBuilder[T] =
    junction(centeredAround, cleanOrigin, goingDown = false) { path =>
      path
        .lineToRel(tenthsDim(14, -14))
        .lineToRel(tenthsDim(3, -3))
        .verticalLineWithHeight(tenthsToSeg(-5))
        .horizontalLineWithWidth(tenthsToSeg(-9))
        .lineToRel(tenthsDim(17, -17))
        .lineToRel(tenthsDim(17, 17))
        .horizontalLineWithWidth(tenthsToSeg(-9))
        .verticalLineWithHeight(tenthsToSeg(6))
        .lineToRel(tenthsDim(3, 3))
        .lineToRel(tenthsDim(14, 13))
    }


  def dataArrowLeft(centeredAround: Point[T]): SvgPathBuilder[T] = {
    SvgPathBuilder(centeredAround)
      .moveToRel(segDim(0, -1))
      .verticalLineWithHeight(intToSeg(-2))
      .lineToRel(segDim(-3, 3))
      .lineToRel(segDim(3, 3))
      .verticalLineWithHeight(intToSeg(-2))
      .horizontalLineWithWidth(intToSeg(4))
      .cubicBezierToRel(segDim(0, 2), segDim(-2, 3), segDim(-4, 3))
      .cubicBezierToRel(segDim(-2, 0), segDim(-4, -2), segDim(-4, -4))
      .cubicBezierToRel(segDim(0, -2), segDim(2, -4), segDim(4, -4))
      .cubicBezierToRel(segDim(2, 0), segDim(4, 1), segDim(4, 3))
      .horizontalLineWithWidth(intToSeg(-4))
      .closePath()
  }

  def dataArrowRight(centeredAround: Point[T]): SvgPathBuilder[T] = {
    SvgPathBuilder(centeredAround)
      .moveToRel(segDim(-0, -1))
      .verticalLineWithHeight(intToSeg(-2))
      .lineToRel(segDim(3, 3))
      .lineToRel(segDim(-3, 3))
      .verticalLineWithHeight(intToSeg(-2))
      .horizontalLineWithWidth(intToSeg(-4))
      .cubicBezierToRel(segDim(-0, 2), segDim(2, 3), segDim(4, 3))
      .cubicBezierToRel(segDim(2, 0), segDim(4, -2), segDim(4, -4))
      .cubicBezierToRel(segDim(-0, -2), segDim(-2, -4), segDim(-4, -4))
      .cubicBezierToRel(segDim(-2, 0), segDim(-4, 1), segDim(-4, 3))
      .horizontalLineWithWidth(intToSeg(4))
      .closePath()
  }


  def buildControlFlowBackgroundMultipleSize(bounds: Bounds[T], drawConnectors: List[(Boolean, Boolean)], commandBracket: Boolean): SvgPathBuilder[T] = {
    val missingWidth = bounds.width - segmentSize * fromInt(6) * fromInt(drawConnectors.size)
    val hasMissingWidth = missingWidth > fromInt(0)

    val top = drawConnectors.map(_._1).foldLeft(SvgPathBuilder(bounds.startPoint)) { (path, connector) =>
      if (connector) path.addControlFlowConnector(segmentSize)
      else path.horizontalLineWithWidth(segmentSize * fromInt(6))
    }
    val topRight = if (hasMissingWidth) top.horizontalLineWithWidth(missingWidth) else top

    val bottomRight =
      if (commandBracket) topRight.addCommandBracketDown(segmentSize, bounds.height)
      else topRight.verticalLineWithHeight(bounds.height)
    val bottomStart = if (hasMissingWidth) bottomRight.horizontalLineWithWidth(-missingWidth) else bottomRight

    val bottom = drawConnectors.map(_._2).reverse.foldLeft(bottomStart) { (path, connector) =>
      if (connector) path.addControlFlowConnector(-segmentSize, true)
      else path.horizontalLineWithWidth(-segmentSize * fromInt(6))
    }

    bottom
      .verticalLineWithHeight(-bounds.height)
      .closePath()
  }


  def buildControlFlowBackground(bounds: Bounds[T], connectorTop: Boolean = true, connectorBottom: Boolean = true): SvgPathBuilder[T] =
    buildControlFlowBackgroundMultipleSize(bounds, List(connectorTop -> connectorBottom), commandBracket = false)

  def buildControlFlowStop(bounds: Bounds[T]): SvgPathBuilder[T] = {

    val connectorW = fromInt(6) * segmentSize
    val freeWidth = if (bounds.width > connectorW) bounds.width - connectorW else fromInt(0)

    SvgPathBuilder(bounds.startPoint)
      .addControlFlowConnector(segmentSize)
      .horizontalLineWithWidth(freeWidth)
      .verticalLineWithHeight(bounds.height)
      .horizontalLineWithWidth(-bounds.width)
      .closePath()
  }

  def buildControlFlowStart(bounds: Bounds[T]): SvgPathBuilder[T] = {

    val arcL = fromInt(2) * segmentSize
    val starterW = two * arcL + two * segmentSize

    val freeWidth = if (bounds.width > starterW) bounds.width - starterW else fromInt(0)
    val freeHeight = if (bounds.height > arcL) bounds.height - arcL else fromInt(0)

    SvgPathBuilder(bounds.startPoint)
      .moveToRel(Dimension(fromInt(0), arcL))
      .cubicBezierToRel(
        Dimension(-zero, -arcL),
        Dimension(arcL, -arcL),
        Dimension(arcL, -arcL)
      )
      .horizontalLineWithWidth(two * segmentSize)
      .cubicBezierToRel(
        Dimension(arcL, zero),
        Dimension(arcL, arcL),
        Dimension(arcL, arcL)
      )
      .horizontalLineWithWidth(freeWidth)
      .verticalLineWithHeight(freeHeight)
      .horizontalLineWithWidth(-freeWidth)
      .addControlFlowConnector(-segmentSize, true)
      .closePath()
  }


}
