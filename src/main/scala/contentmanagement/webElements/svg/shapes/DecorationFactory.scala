package contentmanagement.webElements.svg.shapes

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import interactionPlugins.blockEnvironment.config.BeRenderingConfig


case class DecorationFactory[T: Fractional](renderingConfig: BeRenderingConfig) {
  val N = summon[Fractional[T]]

  import N.*

  val segmentSize: T = fromInt(renderingConfig.controlSegmentSizeInt)

  private def intDim(width: Int, height: Int): Dimension[T] = Dimension[T](fromInt(width), fromInt(height))

  private def intPoint(x: Int, y: Int): Point[T] = Point[T](fromInt(x), fromInt(y))

  private def intToSeg(nr: Int): T = segmentSize / fromInt(5) * fromInt(nr)

  private def tToSeg(nr: T): T = segmentSize / fromInt(5) * nr

  private def fromTenths(value: Int): T = fromInt(value) / fromInt(10)

  private def segDim(w: Int, h: Int): Dimension[T] = Dimension(segmentSize / fromInt(5) * fromInt(w), segmentSize / fromInt(5) * fromInt(h))

  private def segDim(w: T, h: T): Dimension[T] = Dimension(segmentSize / fromInt(5) * w, segmentSize / fromInt(5) * h)

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
        .moveToRel(segDim(fromTenths(-8), fromTenths(-25)))
        .verticalLineWithHeight(tToSeg(fromTenths(22)))
        .lineToRel(segDim(fromTenths(-3), fromTenths(3)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(-5)))
        .verticalLineWithHeight(tToSeg(fromTenths(22)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .lineToRel(segDim(fromTenths(-5), fromTenths(-6)))
        .lineToRel(segDim(fromTenths(8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(-5), fromTenths(6)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .verticalLineWithHeight(tToSeg(fromTenths(-22)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(5)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(-3), fromTenths(-3)))
        .verticalLineWithHeight(tToSeg(fromTenths(-22)))

    val withOptionalOrigin =
      if (cleanOrigin) basePath
      else basePath.lineToRel(segDim(fromTenths(-8), fromTenths(8)))

    withOptionalOrigin.closePath()
  }

  def controlFlowSplitUp(centeredAround: Point[T], cleanOrigin: Boolean): SvgPathBuilder[T] = {
    val basePath =
      SvgPathBuilder(centeredAround)
        .moveToRel(segDim(fromTenths(-8), fromTenths(25)))
        .verticalLineWithHeight(tToSeg(fromTenths(-22)))
        .lineToRel(segDim(fromTenths(-3), fromTenths(-3)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(5)))
        .verticalLineWithHeight(tToSeg(fromTenths(-22)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .lineToRel(segDim(fromTenths(-5), fromTenths(6)))
        .lineToRel(segDim(fromTenths(8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(-5), fromTenths(-6)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .verticalLineWithHeight(tToSeg(fromTenths(22)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(-5)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(-3), fromTenths(3)))

    val withOptionalOrigin =
      if (cleanOrigin) basePath.verticalLineWithHeight(tToSeg(fromTenths(22)))
      else basePath
        .verticalLineWithHeight(tToSeg(fromTenths(22)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(-8)))

    withOptionalOrigin.closePath()
  }

  def controlFlowCrossDown(centeredAround: Point[T], cleanOrigin: Boolean): SvgPathBuilder[T] = {
    if (cleanOrigin) {
      SvgPathBuilder(centeredAround)
        .moveToRel(segDim(fromTenths(-14), fromTenths(-25)))
        .lineToRel(segDim(fromTenths(-11), fromTenths(11)))
        .lineToRel(segDim(fromTenths(14), fromTenths(14)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(-6)))
        .verticalLineWithHeight(tToSeg(fromTenths(23)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(-6)))
        .lineToRel(segDim(fromTenths(8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(-4), fromTenths(6)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .verticalLineWithHeight(tToSeg(fromTenths(-22)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(5)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(14), fromTenths(-14)))
        .lineToRel(segDim(fromTenths(-11), fromTenths(-11)))
        .lineToRel(segDim(fromTenths(-14), fromTenths(14)))
        .closePath()
    } else {
      SvgPathBuilder(centeredAround)
        .moveToRel(segDim(fromTenths(-14), fromTenths(-25)))
        .verticalLineWithHeight(tToSeg(fromTenths(11)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-11)))
        .lineToRel(segDim(fromTenths(14), fromTenths(14)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(-6)))
        .verticalLineWithHeight(tToSeg(fromTenths(23)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(-6)))
        .lineToRel(segDim(fromTenths(8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(-4), fromTenths(6)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .verticalLineWithHeight(tToSeg(fromTenths(-22)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(5)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(14), fromTenths(-14)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-11)))
        .verticalLineWithHeight(tToSeg(fromTenths(-11)))
        .lineToRel(segDim(fromTenths(-14), fromTenths(14)))
        .closePath()
    }
  }

  def controlFlowCrossUp(centeredAround: Point[T], cleanOrigin: Boolean): SvgPathBuilder[T] = {
    if (cleanOrigin) {
      SvgPathBuilder(centeredAround)
        .moveToRel(segDim(fromTenths(-14), fromTenths(25)))
        .lineToRel(segDim(fromTenths(-11), fromTenths(-11)))
        .lineToRel(segDim(fromTenths(14), fromTenths(-14)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(6)))
        .verticalLineWithHeight(tToSeg(fromTenths(-23)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(6)))
        .lineToRel(segDim(fromTenths(8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(-4), fromTenths(-6)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .verticalLineWithHeight(tToSeg(fromTenths(22)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(-5)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(14), fromTenths(14)))
        .lineToRel(segDim(fromTenths(-11), fromTenths(11)))
        .lineToRel(segDim(fromTenths(-14), fromTenths(-14)))
        .closePath()
    } else {
      SvgPathBuilder(centeredAround)
        .moveToRel(segDim(fromTenths(-14), fromTenths(25)))
        .verticalLineWithHeight(tToSeg(fromTenths(-11)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-11)))
        .lineToRel(segDim(fromTenths(14), fromTenths(-14)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(6)))
        .verticalLineWithHeight(tToSeg(fromTenths(-23)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(6)))
        .lineToRel(segDim(fromTenths(8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(8), fromTenths(-8)))
        .lineToRel(segDim(fromTenths(-4), fromTenths(-6)))
        .horizontalLineWithWidth(tToSeg(fromTenths(22)))
        .verticalLineWithHeight(tToSeg(fromTenths(22)))
        .lineToRel(segDim(fromTenths(-6), fromTenths(-5)))
        .lineToRel(segDim(fromTenths(-8), fromTenths(8)))
        .lineToRel(segDim(fromTenths(14), fromTenths(14)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-11)))
        .verticalLineWithHeight(tToSeg(fromTenths(11)))
        .lineToRel(segDim(fromTenths(-14), fromTenths(-14)))
        .closePath()
    }
  }

  def controlFlowUnionDown(centeredAround: Point[T], cleanOrigin: Boolean): SvgPathBuilder[T] = {
    if (cleanOrigin) {
      SvgPathBuilder(centeredAround)
        .moveToRel(segDim(fromTenths(-14), fromTenths(-25)))
        .lineToRel(segDim(fromTenths(-11), fromTenths(11)))
        .lineToRel(segDim(fromTenths(14), fromTenths(14)))
        .lineToRel(segDim(fromTenths(3), fromTenths(3)))
        .verticalLineWithHeight(tToSeg(fromTenths(5)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-9)))
        .lineToRel(segDim(fromTenths(17), fromTenths(17)))
        .lineToRel(segDim(fromTenths(17), fromTenths(-17)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-9)))
        .verticalLineWithHeight(tToSeg(fromTenths(-6)))
        .lineToRel(segDim(fromTenths(3), fromTenths(-3)))
        .lineToRel(segDim(fromTenths(14), fromTenths(-13)))
        .lineToRel(segDim(fromTenths(-11), fromTenths(-11)))
        .lineToRel(segDim(fromTenths(-14), fromTenths(14)))
        .closePath()
    } else {
      SvgPathBuilder(centeredAround)
        .moveToRel(segDim(fromTenths(-14), fromTenths(-25)))
        .verticalLineWithHeight(tToSeg(fromTenths(11)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-11)))
        .lineToRel(segDim(fromTenths(14), fromTenths(14)))
        .lineToRel(segDim(fromTenths(3), fromTenths(3)))
        .lineToRel(segDim(zero, fromTenths(5)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-9)))
        .lineToRel(segDim(fromTenths(17), fromTenths(17)))
        .lineToRel(segDim(fromTenths(17), fromTenths(-17)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-9)))
        .lineToRel(segDim(zero, fromTenths(-6)))
        .lineToRel(segDim(fromTenths(3), fromTenths(-3)))
        .lineToRel(segDim(fromTenths(14), fromTenths(-13)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-11)))
        .verticalLineWithHeight(tToSeg(fromTenths(-11)))
        .lineToRel(segDim(fromTenths(-14), fromTenths(14)))
        .closePath()
    }
  }

  def controlFlowUnionUp(centeredAround: Point[T], cleanOrigin: Boolean): SvgPathBuilder[T] = {
    if (cleanOrigin) {
      SvgPathBuilder(centeredAround)
        .moveToRel(segDim(fromTenths(-14), fromTenths(25)))
        .lineToRel(segDim(fromTenths(-11), fromTenths(-11)))
        .lineToRel(segDim(fromTenths(14), fromTenths(-14)))
        .lineToRel(segDim(fromTenths(3), fromTenths(-3)))
        .verticalLineWithHeight(tToSeg(fromTenths(-5)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-9)))
        .lineToRel(segDim(fromTenths(17), fromTenths(-17)))
        .lineToRel(segDim(fromTenths(17), fromTenths(17)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-9)))
        .verticalLineWithHeight(tToSeg(fromTenths(6)))
        .lineToRel(segDim(fromTenths(3), fromTenths(3)))
        .lineToRel(segDim(fromTenths(14), fromTenths(13)))
        .lineToRel(segDim(fromTenths(-11), fromTenths(11)))
        .lineToRel(segDim(fromTenths(-14), fromTenths(-14)))
        .closePath()
    } else {
      SvgPathBuilder(centeredAround)
        .moveToRel(segDim(fromTenths(-14), fromTenths(25)))
        .verticalLineWithHeight(tToSeg(fromTenths(-11)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-11)))
        .lineToRel(segDim(fromTenths(14), fromTenths(-14)))
        .lineToRel(segDim(fromTenths(3), fromTenths(-3)))
        .verticalLineWithHeight(tToSeg(fromTenths(-5)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-9)))
        .lineToRel(segDim(fromTenths(17), fromTenths(-17)))
        .lineToRel(segDim(fromTenths(17), fromTenths(17)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-9)))
        .verticalLineWithHeight(tToSeg(fromTenths(6)))
        .lineToRel(segDim(fromTenths(3), fromTenths(3)))
        .lineToRel(segDim(fromTenths(14), fromTenths(13)))
        .horizontalLineWithWidth(tToSeg(fromTenths(-11)))
        .verticalLineWithHeight(tToSeg(fromTenths(11)))
        .lineToRel(segDim(fromTenths(-14), fromTenths(-14)))
        .closePath()
    }
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

    var res = SvgPathBuilder(bounds.startPoint)


    for (curConnector <- drawConnectors.map(_._1)) {
      res =
        if (curConnector) res.addControlFlowConnector(segmentSize)
        else res.horizontalLineWithWidth(segmentSize * fromInt(6))
    }

    //res = res.markSpot(fromInt(2))

    if (missingWidth > fromInt(0)){
      res = res.horizontalLineWithWidth(missingWidth)
    }


    if(commandBracket){
      res = res.addCommandBracketDown(segmentSize, bounds.height)
    }else{
      res = res.verticalLineWithHeight(bounds.height)

    }


    if (missingWidth > fromInt(0)) {
      res = res.horizontalLineWithWidth(-missingWidth)
    }

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

    /*
      def buildStarterShape[T: Fractional](pBounds: Bounds[T]): SvgPathBuilder[T] = {
        val N = summon[Fractional[T]]
        import N.*
        val bounds = pBounds

        val offsetDist = fromInt(10) // fromInt(7) + fromInt(1) / fromInt(2)

        SvgPathBuilder(bounds.startPoint + Point[T](fromInt(0), offsetDist))
          .cubicBezierToRel(
            new Dimension[T](fromInt(0), -offsetDist),
            new Dimension[T](offsetDist, -offsetDist),
            new Dimension[T](fromInt(20), -offsetDist)
          )
          .cubicBezierToRel(
            new Dimension[T](offsetDist, fromInt(0)),
            new Dimension[T](fromInt(20), fromInt(0)),
            new Dimension[T](fromInt(20), offsetDist)
          )
          .horizontalLineWithWidth(bounds.width - fromInt(40))
          .verticalLineWithHeight(bounds.height - offsetDist)

          .horizontalLineWithWidth(-bounds.width + fromInt(30))
          //.addInstructionConnector(fromInt(-20))
          .horizontalLineWithWidth(fromInt(-20))
          .horizontalLineWithWidth(fromInt(-10))
          .closePath()

          .closePath()
      }*/

  }


}
