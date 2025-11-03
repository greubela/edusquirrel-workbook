package contentmanagement.webElements.svg.shapes

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.SvgPathBuilder
import interactionPlugins.blockEnvironment.config.BeRenderingConfig


case class ControlFlowShapeFactory[T: Fractional](renderingConfig: BeRenderingConfig) {
  val N = summon[Fractional[T]]

  import N.*

  val segmentSize: T = fromInt(renderingConfig.controlSegmentSize)


  private def intDim(width: Int, height: Int): Dimension[T] = Dimension[T](fromInt(width), fromInt(height))

  private def intPoint(x: Int, y: Int): Point[T] = Point[T](fromInt(x), fromInt(y))

  private def intToSeg(nr: Int): T = segmentSize / fromInt(5) * fromInt(nr)

  private def tToSeg(nr: T): T = segmentSize / fromInt(5) * nr

  private def segDim(w: Int, h: Int): Dimension[T] = Dimension(segmentSize / fromInt(5) * fromInt(w), segmentSize / fromInt(5) * fromInt(h))

  private def segDim(w: T, h: T): Dimension[T] = Dimension(segmentSize / fromInt(5) * w, segmentSize / fromInt(5) * h)

  val one: T = fromInt(1)
  val two: T = fromInt(2)
  val half: T = one / two

  def controlArrowDown(bounds: Bounds[T]): SvgPathBuilder[T] = {
    SvgPathBuilder(bounds.centerPoint)
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

  def dataArrowLeft(bounds: Bounds[T]): SvgPathBuilder[T] = {
    SvgPathBuilder(bounds.centerPoint)
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

  def dataArrowRight(bounds: Bounds[T]): SvgPathBuilder[T] = {
    SvgPathBuilder(bounds.centerPoint)
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


  def buildControlFlowBackgroundMultipleSize(bounds: Bounds[T], drawConnectors: List[(Boolean, Boolean)]): SvgPathBuilder[T] = {
    val missingWidth = bounds.width - segmentSize * fromInt(6) * fromInt(drawConnectors.size)

    var res = SvgPathBuilder(bounds.startPoint)

    for (curConnector <- drawConnectors.map(_._1)) {
      res =
        if (curConnector) res.addControlFlowConnector(segmentSize)
        else res.horizontalLineWithWidth(segmentSize * fromInt(6))
    }
    if (missingWidth > fromInt(0)) res = res.horizontalLineWithWidth(missingWidth)

    res = res.verticalLineWithHeight(bounds.height)

    if (missingWidth > fromInt(0)) res = res.horizontalLineWithWidth(-missingWidth)

    for (curConnector <- drawConnectors.map(_._2).reverse) {
      res =
        if (curConnector) res.addControlFlowConnector(-segmentSize, true)
        else res.horizontalLineWithWidth(-segmentSize * fromInt(6))
    }

    res
      .verticalLineWithHeight(-bounds.height)
      .closePath()
  }


  def buildControlFlowBackground(bounds: Bounds[T], connectorTop: Boolean = true, connectorBottom: Boolean = true): SvgPathBuilder[T] = {

    var res = SvgPathBuilder(bounds.startPoint)

    res = if (connectorTop) res.addControlFlowConnector(segmentSize)
    else res.horizontalLineWithWidth(segmentSize * fromInt(6))

    val missingWidth = bounds.width - segmentSize * fromInt(6)
    if (missingWidth > fromInt(0)) res = res.horizontalLineWithWidth(missingWidth)

    res = res.verticalLineWithHeight(bounds.height)

    if (missingWidth > fromInt(0)) res = res.horizontalLineWithWidth(-missingWidth)

    res = if (connectorBottom) res.addControlFlowConnector(-segmentSize, true)
    else res.horizontalLineWithWidth(-segmentSize * fromInt(6))

    res
      .verticalLineWithHeight(-bounds.height)
      .closePath()
  }


}
