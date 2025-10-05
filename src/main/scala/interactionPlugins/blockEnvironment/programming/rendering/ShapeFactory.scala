package interactionPlugins.blockEnvironment.programming.rendering

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}


object ShapeFactory {


  /*private def ensureDim[T: Fractional](pBounds: Bounds[T], minWidth: Int, minHeight: Int): Bounds[T] =
    pBounds.ensureAtLeastAsBigAs(getDim(minWidth, minHeight))

  private def getDim[T: Fractional](width: Int, height: Int): Dimension[T] = Dimension[T](intToT(width), intToT(height))*/

  private def intToT[T: Fractional](number: Int): T = {
    val N = summon[Fractional[T]]
    import N.*
    fromInt(number)
  }

  def buildDateShape[T: Fractional](pBounds: Bounds[T]): AppSvgElement = {
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
      .toAppSvgElement()
  }

  def buildStringShape[T: Fractional](pBounds: Bounds[T]): AppSvgElement = {
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
      .toAppSvgElement()

  }


  def buildBooleanShape[T: Fractional](pBounds: Bounds[T]): AppSvgElement = {
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
      .toAppSvgElement()
  }

  def buildNumericShape[T: Fractional](pBounds: Bounds[T]): AppSvgElement = {
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
      .toAppSvgElement()
  }

  def buildStarterShape[T: Fractional](pBounds: Bounds[T]): AppSvgElement = {
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
      .addInstructionConnector(fromInt(-20))
      .horizontalLineWithWidth(fromInt(-10))
      .closePath()

      .closePath()
      .toAppSvgElement()
  }

  def buildRectangle[T: Fractional](pBounds: Bounds[T]): AppSvgElement = {
    val N = summon[Fractional[T]]
    import N.*
    val bounds = pBounds

    SvgPathBuilder(bounds.startPoint)
      .horizontalLineWithWidth(bounds.width)
      .verticalLineWithHeight(bounds.height)
      .horizontalLineWithWidth(-bounds.width)
      .closePath()
      .toAppSvgElement()
  }

  def buildUnitShape[T: Fractional](pBounds: Bounds[T]): AppSvgElement = {
    val N = summon[Fractional[T]]
    import N.*
    val bounds = pBounds

    val offsetDist = fromInt(7) + fromInt(1) / fromInt(2)

    SvgPathBuilder(bounds.startPoint)
      .horizontalLineWithWidth(fromInt(10))
      .addInstructionConnector(fromInt(20), true)
      .horizontalLineWithWidth(bounds.width - fromInt(30))
      .verticalLineWithHeight(bounds.height)
      .horizontalLineWithWidth(-bounds.width + fromInt(30))
      .addInstructionConnector(fromInt(-20))
      .horizontalLineWithWidth(fromInt(-10))
      .closePath()
      .toAppSvgElement()
  }


}
