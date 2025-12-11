package contentmanagement.webElements.svg.shapes

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.builder.SvgPathBuilder


private[shapes] object ShapeFactory {

  private def intToT[T: Fractional](number: Int): T = {
    val N = summon[Fractional[T]]
    import N.*
    fromInt(number)
  }

  private def intDim[T: Fractional](width: Int, height: Int): Dimension[T] = Dimension[T](intToT(width), intToT(height))

  private def intPoint[T: Fractional](x: Int, y: Int): Point[T] = Point[T](intToT(x), intToT(y))

  def buildCommandShape[T: Fractional](pBounds: Bounds[T], segmentWidth: T): SvgPathBuilder[T] = {
    val N = summon[Fractional[T]]
    import N.*

    val height = if(pBounds.height < fromInt(2) * segmentWidth) fromInt(2) * segmentWidth else pBounds.height
    val bracketWidth = segmentWidth * fromInt(4) / fromInt(5)
    val rightSideCornerHeight =  segmentWidth * fromInt(2) / fromInt(5)
    val zero: T = fromInt(0)
    val one: T = segmentWidth / fromInt(5)
    val two: T = one + one

    SvgPathBuilder(pBounds.startPoint)
      //.moveToRel(Dimension(bracketWidth, fromInt(0)))
      .addCommandBracketDown(segmentWidth, height)
      .horizontalLineWithWidth(pBounds.width - bracketWidth - bracketWidth)
      .cubicBezierToRel(Dimension(one, zero), Dimension(two, -one), Dimension(two, -two))
      .verticalLineWithHeight(- height + rightSideCornerHeight + rightSideCornerHeight)
     .cubicBezierToRel(Dimension(zero, -one), Dimension(-one, -two), Dimension(-two, -two))
      //.verticalLineWithHeight(-height)
      .closePath()
  }


  def buildLiteralShape[T: Fractional](fitIntoBounds: Bounds[T]): SvgPathBuilder[T] = {
    val N = summon[Fractional[T]]
    import N.*
    val bounds = fitIntoBounds

    val r = bounds.height / fromInt(10)
    val d = r * fromInt(2)

    val halfHeight = bounds.height / fromInt(2)
    val straightLineWidth = bounds.width - halfHeight - d

    SvgPathBuilder(fitIntoBounds.startPoint)
      //.drawBoundRectangle(bounds)
      // base shape
      .moveToRel(new Dimension[T](fromInt(0), halfHeight))
      .lineToRel(new Dimension[T](halfHeight, -halfHeight))
      .horizontalLineWithWidth(straightLineWidth)
      .lineToRel(new Dimension[T](d, d))
      .verticalLineWithHeight(d * fromInt(3))
      .lineToRel(new Dimension[T](-d, d))
      .horizontalLineWithWidth(-straightLineWidth)
      .lineToRel(new Dimension[T](-halfHeight, -halfHeight))
      .closePath()
      // circle
      .moveToAbs(fitIntoBounds.startPoint)
      .moveToRel(Dimension(bounds.height / fromInt(2), bounds.height / fromInt(2)))
      .moveToRel(Dimension(-r, fromInt(0)))
      .addCenteredCircle(r)
      .closePath()

  }


  def buildDuckShape[T: Fractional](fitIntoBounds: Bounds[T]): SvgPathBuilder[T] = {

    val N = summon[Fractional[T]]
    import N.*
    val drawBounds = new Dimension[T](fitIntoBounds.width * fromInt(100) / fromInt(125), fitIntoBounds.height / fromInt(2))

    // if the textarea is 100x25, then the duck is 125x50 and textarea starts at y(15/25). the duck takes y(15+25) width away

    def transformX(x: Int, referenceWidth: Int = 125): T = fromInt(x) * drawBounds.width / fromInt(referenceWidth)

    def transformY(y: Int, referenceHeight: Int = 25): T = fromInt(y) * drawBounds.height / fromInt(referenceHeight)

    def scaledDim(width: Int, height: Int): Dimension[T] = Dimension[T](transformY(width), transformY(height))

    SvgPathBuilder(fitIntoBounds.startPoint)
      /* test rectangle
      .horizontalLineWithWidth(fitIntoBounds.width)
      .verticalLineWithHeight(fitIntoBounds.height)
      .horizontalLineWithWidth(-fitIntoBounds.width)
      .verticalLineWithHeight(-fitIntoBounds.height)*/
      // left elements
      .moveToRel(scaledDim(15, 25))

      .cubicBezierToRel(scaledDim(-6, 0), scaledDim(-7, 0), scaledDim(-10, -5))
      .cubicBezierToRel(scaledDim(-1, -1), scaledDim(-1, -1), scaledDim(-2, -1))
      .cubicBezierToRel(scaledDim(-1, 0), scaledDim(-1, 0), scaledDim(-2, 1))
      .cubicBezierToRel(scaledDim(-3, 6), scaledDim(-1, 27), scaledDim(14, 30))

      .horizontalLineWithWidth(fitIntoBounds.width - transformY(15 + 35)) // to the right.

      // right elements
      .horizontalLineWithWidth(transformY(20))

      .cubicBezierToRel(scaledDim(11, 0), scaledDim(20, -12), scaledDim(8, -21))
      .cubicBezierToRel(scaledDim(-2, -2), scaledDim(-5, -5), scaledDim(-3, -8))
      .cubicBezierToRel(scaledDim(1, -1), scaledDim(3, -1), scaledDim(5, -1))

      .cubicBezierToRel(scaledDim(2, 0), scaledDim(4, -1), scaledDim(2, -3))
      .cubicBezierToRel(scaledDim(2, -2), scaledDim(4, -5), scaledDim(0, -5))

      .cubicBezierToRel(scaledDim(-3, 0), scaledDim(-5, 0), scaledDim(-5, -3))
      .cubicBezierToRel(scaledDim(0, -3), scaledDim(-5, -9), scaledDim(-12, -9))
      .cubicBezierToRel(scaledDim(-10, 0), scaledDim(-12, 10), scaledDim(-10, 15))
      .cubicBezierToRel(scaledDim(2, 4), scaledDim(5, 10), scaledDim(-5, 10))


      .closePath()
  }

  def buildDateShape[T: Fractional](pBounds: Bounds[T]): SvgPathBuilder[T] = {
    val N = summon[Fractional[T]]
    import N.*
    val bounds = pBounds

    SvgPathBuilder(bounds.startPoint)
      .quadraticBezierWithRel(
        Dimension[T](fromInt(10), bounds.height / fromInt(2)),
        Dimension[T](fromInt(0), bounds.height)
      )
      .horizontalLineWithWidth(bounds.width)
      .quadraticBezierWithRel(
        Dimension[T](fromInt(-10), -bounds.height / fromInt(2)),
        Dimension[T](fromInt(0), -bounds.height)
      )
      .horizontalLineWithWidth(-bounds.width)
  }


  def buildUnitShape[T: Fractional](pBounds: Bounds[T], pRadius: Int): SvgPathBuilder[T] = {
    val N = summon[Fractional[T]]
    import N.*
    val bounds = pBounds

    val circleR = fromInt(pRadius)
    val circleD = fromInt(2 * pRadius)

    val circleNrWidth = (pBounds.width / circleD).toInt
    val circleNrHeight = (pBounds.height / circleD).toInt

    var res = SvgPathBuilder(bounds.startPoint)
      //.moveToRel(Dimension(circleR + widthEmpty / fromInt(2), circleR + heightEmpty / fromInt(2)))
      .moveToRel(Dimension(circleR, circleR))

    for (curNr <- 0.until(circleNrWidth - 1)) {
      res = res.addArcToTheTopMoveRight(circleR)
    }
    for (curNr <- 0.until(circleNrHeight - 1)) {
      res = res.addArcToTheRightMoveBottom(circleR)
    }
    // and back
    for (curNr <- 0.until(circleNrWidth - 1)) {
      res = res.addArcToTheTopMoveRight(-circleR)
    }
    for (curNr <- 0.until(circleNrHeight - 1)) {
      res = res.addArcToTheRightMoveBottom(-circleR)
    }

    res
      .closePath()

  }

  def buildStringShape[T: Fractional](pBounds: Bounds[T]): SvgPathBuilder[T] = {
    val N = summon[Fractional[T]]
    import N.*
    val bounds = pBounds

    SvgPathBuilder(bounds.startPoint + Point[T](fromInt(5), fromInt(0)))
      .verticalLineWithHeight(bounds.height / fromInt(4))
      .horizontalLineWithWidth(fromInt(-5))
      .verticalLineWithHeight(bounds.height / fromInt(2))
      .horizontalLineWithWidth(fromInt(5))
      .verticalLineWithHeight(bounds.height / fromInt(4))

      .horizontalLineWithWidth(bounds.width - fromInt(10))

      .verticalLineWithHeight(-bounds.height / fromInt(4))
      .horizontalLineWithWidth(fromInt(5))
      .verticalLineWithHeight(-bounds.height / fromInt(2))
      .horizontalLineWithWidth(fromInt(-5))
      .verticalLineWithHeight(-bounds.height / fromInt(4))

      .closePath()

  }


  def buildBooleanShape[T: Fractional](pBounds: Bounds[T]): SvgPathBuilder[T] = {
    val N = summon[Fractional[T]]
    import N.*
    val bounds = pBounds

    SvgPathBuilder(bounds.startPoint + Point[T](fromInt(5), fromInt(0)))
      .lineToRel(new Dimension[T](fromInt(-5), bounds.height / fromInt(2)))
      .lineToRel(new Dimension[T](fromInt(5), bounds.height / fromInt(2)))
      .horizontalLineWithWidth(bounds.width - fromInt(10))
      .lineToRel(new Dimension[T](fromInt(5), -bounds.height / fromInt(2)))
      .lineToRel(new Dimension[T](fromInt(-5), -bounds.height / fromInt(2)))
      .closePath()
  }

  def buildNumericShape[T: Fractional](pBounds: Bounds[T]): SvgPathBuilder[T] = {
    val N = summon[Fractional[T]]
    import N.*
    val bounds = pBounds

    SvgPathBuilder(bounds.startPoint + Point[T](fromInt(5), fromInt(0)))
      .quadraticBezierWithRel(
        new Dimension[T](fromInt(-10), bounds.height / fromInt(2)),
        new Dimension[T](fromInt(0), bounds.height)
      )
      .horizontalLineWithWidth(bounds.width - fromInt(10))
      .quadraticBezierWithRel(
        new Dimension[T](fromInt(10), -bounds.height / fromInt(2)),
        new Dimension[T](fromInt(0), -bounds.height)
      )
      .closePath()
  }


  def buildRectangle[T: Fractional](pBounds: Bounds[T]): SvgPathBuilder[T] = {
    // AppRectangleSvgElement[T](pBounds)
    val bounds = pBounds
    SvgPathBuilder(bounds.startPoint).drawBoundRectangle(bounds)
  }

  def buildControlFlowShapeDown[T: Fractional](pBounds: Bounds[T], segmentWidth: T): SvgPathBuilder[T] = {
    val N = summon[Fractional[T]]
    import N.*
    val bounds = pBounds

    val missingWidth = bounds.width - segmentWidth * fromInt(6)
    println("buildControlFlowShapeDown: " + pBounds + " segmentWidth: " + segmentWidth + " missingWidth: " + missingWidth)

    SvgPathBuilder(bounds.startPoint)
      .addControlFlowConnector(segmentWidth)
      .horizontalLineWithWidth(missingWidth)
      .verticalLineWithHeight(bounds.height)
      .horizontalLineWithWidth(-missingWidth)
      .addControlFlowConnector(-segmentWidth, true)
      .verticalLineWithHeight(-bounds.height)
      .closePath()
  }

  def buildCreateStructureShape[T: Fractional](pBounds: Bounds[T], segmentWidth: T): SvgPathBuilder[T] = {
    val N = summon[Fractional[T]]
    import N.*
    val bounds = pBounds

    val centerCircleRadius = pBounds.width / fromInt(4)

    val circleCenter = pBounds.startPoint.moveWithDimension(Dimension(bounds.width - centerCircleRadius / fromInt(2), bounds.height - centerCircleRadius / fromInt(2)))

    val missingWidth = bounds.width - segmentWidth * fromInt(6)

    buildControlFlowShapeDown(pBounds, segmentWidth)
      // add shape center
      .moveToAbs(circleCenter)
      .addCenteredCircle(centerCircleRadius)
      // extension right
      .moveToAbs(circleCenter)
      .moveToRel(Dimension(centerCircleRadius, fromInt(0)))
      .horizontalLineWithWidth(pBounds.width / fromInt(2) - centerCircleRadius)

  }

}
