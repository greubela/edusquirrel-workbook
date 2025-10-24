package interactionPlugins.blockEnvironment.programming.rendering

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.model.color.RGBColor
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.atomarElements.{AppPathSvgElement, AppRectangleSvgElement, AppTextSvgElement}
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.programming.BeDataType

sealed trait BeShape {
  def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement


  def getAssociatedSvgWithTextChild(config: BeRendererConfig, bounds: Bounds[Double], languageMap: LanguageMap[HumanLanguage]): AppSvgElement = {
    val str = languageMap.getInLanguage(config.language)
    val textDim = config.appFont.measureText(str)
    val textBounds = bounds.startPoint.moveWithDimension(getRelativeChildOffset(config, textDim, bounds.dimension).asDimension).withDimension(textDim)

    val shape = getAssociatedSvgElement(bounds)
    val text = AppTextSvgElement[Double](str, textBounds, config.appFont)
    val textRect = BeShape.RectangleShape.getAssociatedSvgElement(textBounds).addMods(List(svg.fill := "transparent", svg.stroke := RGBColor.red.toWebStyleString))
    AppDecoratedSvgElement(shape, List(text), List())
  }

  def getAssociatedSvgWithShapeChild(config: BeRendererConfig, bounds: Bounds[Double], child: BeShape, childDim: Dimension[Double]): AppSvgElement = {
    val childBounds = bounds.startPoint.moveWithDimension(getRelativeChildOffset(config, childDim, bounds.dimension).asDimension).withDimension(childDim)

    val myShape = getAssociatedSvgElement(bounds)
    val childSvg = child.getAssociatedSvgElement(childBounds)

    val childRect = BeShape.RectangleShape.getAssociatedSvgElement(childBounds).addMods(List(svg.fill := "transparent", svg.stroke := RGBColor.red.toWebStyleString))
    AppDecoratedSvgElement(myShape, List(childSvg), List())
  }

  def getAssociatedSvgWithElementChild(config: BeRendererConfig, bounds: Bounds[Double], child: AppSvgElement): AppSvgElement = {
    val myShape = getAssociatedSvgElement(bounds)
    AppDecoratedSvgElement(myShape, List(child), List())
  }

  def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double]

  def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double]
}


object BeShape {
  def apply(dataTypes: Set[BeDataType]): BeShape = {
    if (dataTypes.isEmpty) {
      DuckShape
    } else if (dataTypes.size == 1) {
      dataTypes.head.associatedShape
    } else {
      DuckShape
    }
  }

  case class ShapeWithInnerLiteralShape(outerShape: BeShape) extends BeShape {


    def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
      outerShape.getAssociatedSvgElement(bounds)
    }

    def minSizeToContainChild(config: BeRendererConfig, smallestChildDimension: Dimension[Double]): Dimension[Double] = {
      val afterLiteral = BeShape.LiteralShape.minSizeToContainChild(config, smallestChildDimension)
      outerShape.minSizeToContainChild(config, afterLiteral)
    }

    def getRelativeChildOffset(config: BeRendererConfig, smallestChildDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val literalDim = BeShape.LiteralShape.minSizeToContainChild(config, smallestChildDimension)
      val literalOffset = outerShape.getRelativeChildOffset(config, literalDim, myDimension)
      val smallestOffset = BeShape.LiteralShape.getRelativeChildOffset(config, smallestChildDimension, literalDim)
      literalOffset.moveWithDimension(smallestOffset.asDimension)
    }

    override def getAssociatedSvgWithTextChild(config: BeRendererConfig, bounds: Bounds[Double], languageMap: LanguageMap[HumanLanguage]): AppSvgElement = {
      val str = languageMap.getInLanguage(config.language)
      val textDim = config.appFont.measureText(str)
      val textBounds = bounds.startPoint.moveWithDimension(getRelativeChildOffset(config, textDim, bounds.dimension).asDimension).withDimension(textDim)

      val literalMinDim = BeShape.LiteralShape.minSizeToContainChild(config, textDim)
      val literalOffset = outerShape.getRelativeChildOffset(config, literalMinDim, bounds.dimension)
      val literalBounds = bounds.startPoint.moveWithDimension(literalOffset.asDimension).withDimension(literalMinDim)
      val literalShape = BeShape.LiteralShape.getAssociatedSvgWithTextChild(config, literalBounds, languageMap).addMods(List(svg.fill := "transparent", svg.stroke := "black"))

      outerShape.getAssociatedSvgWithElementChild(config, bounds, literalShape)
    }

    override def getAssociatedSvgWithShapeChild(config: BeRendererConfig, bounds: Bounds[Double], child: BeShape, childDim: Dimension[Double]): AppSvgElement = {
      val childBounds = bounds.startPoint.moveWithDimension(getRelativeChildOffset(config, childDim, bounds.dimension).asDimension).withDimension(childDim)
      val literalMinDim = BeShape.LiteralShape.minSizeToContainChild(config, childDim)
      val literalOffset = outerShape.getRelativeChildOffset(config, literalMinDim, bounds.dimension)
      val literalBounds = bounds.startPoint.moveWithDimension(literalOffset.asDimension).withDimension(literalMinDim)

      val literalShape = BeShape.LiteralShape.getAssociatedSvgWithShapeChild(config, literalBounds, child, childDim).addMods(List(svg.fill := "transparent", svg.stroke := "black"))
      outerShape.getAssociatedSvgWithElementChild(config, bounds, literalShape)

    }


  }

  object DuckShape extends BeShape {

    override def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
      ShapeFactory.buildDuckShape(bounds)
    }

    override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      val minDim = childDimension.ensureWidth(childDimension.height)
      val leftOfText = minDim.height / 25 * 15
      val rightOfText = minDim.height / 25 * 10

      Dimension[Double](minDim.width + leftOfText + rightOfText, minDim.height * 2).increaseSize(config.paddingSmall).increaseSize(config.paddingSmall)
    }

    override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val minDim = childDimension.ensureWidth(childDimension.height)
      val leftOfText = minDim.height / 25 * 15
      val rightOfText = minDim.height / 25 * 10

      val availableWidth = myDimension.width - leftOfText - rightOfText
      val extraWidth = availableWidth - childDimension.width

      val availableHeight = myDimension.height / 2
      val extraHeight = availableHeight - childDimension.height

      new Point[Double](leftOfText + extraWidth / 2, myDimension.height / 2 + extraHeight / 2)
    }

    /*
        override def getTextBounds(config: BeRendererConfig, bounds: Bounds[Double]): Bounds[Double] = {

          // width = actualWidth + actualHeight / 25 * (15+10)
          // height = actualHeight * 2
          // =>
          // actualWidth = width - actualHeight / 25 * (15+10)
          // actualWidth = width - actualHeight

          val actualHeight = bounds.height / 2
          val actualWidth = bounds.width - actualHeight

          val offsetLeft = actualHeight / 25 * 15
          val offsetRight = actualHeight / 25 * 10

          val newWidth = bounds.width - offsetLeft - offsetRight
          bounds.startPoint.moveWithDimension(new Dimension[Double](offsetLeft, actualHeight)).withDimension(new Dimension[Double](actualWidth, actualHeight))
        }*/

  }

  object LiteralShape extends BeShape {
    override def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = ShapeFactory.buildLiteralShape(bounds)

    override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      val minDim = childDimension.ensureWidth(childDimension.height)
      Dimension[Double](minDim.width + minDim.height / 2 + minDim.height / 10, minDim.height).increaseSize(config.paddingSmall).increaseSize(config.paddingSmall)
    }

    override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val availableWidth = myDimension.width - myDimension.height / 2 - myDimension.height / 10
      val extraWidth = availableWidth - childDimension.width

      val availableHeight = myDimension.height
      val extraHeight = availableHeight - childDimension.height

      new Point[Double](myDimension.height / 2 + extraWidth / 2, extraHeight / 2)
    }

  }

  object NumericShape extends BeShape {

    override def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
      ShapeFactory.buildNumericShape(bounds)
    }

    override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      childDimension.increaseSize(config.paddingSmall).increaseSize(config.paddingSmall).increaseSize(10, 0)
    }

    override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val extraWidth = myDimension.width - 10 - childDimension.width
      val extraHeight = myDimension.height - childDimension.height
      new Point[Double](5 + extraWidth / 2, extraHeight / 2)
    }

  }

  object BooleanShape extends BeShape {

    override def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
      ShapeFactory.buildBooleanShape(bounds)
    }

    override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      childDimension.increaseSize(config.paddingSmall).increaseSize(config.paddingSmall).increaseSize(10, 0)
    }

    override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val extraWidth = myDimension.width - 10 - childDimension.width
      val extraHeight = myDimension.height - childDimension.height
      new Point[Double](5 + extraWidth / 2, extraHeight / 2)
    }
  }

  object StringShape extends BeShape {
    override def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
      ShapeFactory.buildStringShape(bounds)
    }

    override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      childDimension.increaseSize(config.paddingSmall).increaseSize(config.paddingSmall).increaseSize(10, 0)
    }

    override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val extraWidth = myDimension.width - 10 - childDimension.width
      val extraHeight = myDimension.height - childDimension.height
      new Point[Double](5 + extraWidth / 2, extraHeight / 2)
    }

  }

  object DateShape extends BeShape {

    override def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
      ShapeFactory.buildDateShape[Double](bounds)
    }

    override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      childDimension.increaseSize(config.paddingSmall).increaseSize(config.paddingSmall).increaseSize(10, 0)
    }

    override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val extraWidth = myDimension.width - 10 - childDimension.width
      val extraHeight = myDimension.height - childDimension.height
      new Point[Double](5 + extraWidth / 2, extraHeight / 2)
    }
  }

  object RectangleShape extends BeShape {

    override def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
      ShapeFactory.buildRectangle(bounds)
    }

    override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      childDimension.increaseSize(config.paddingSmall).increaseSize(config.paddingSmall)
    }

    override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val extraWidth = myDimension.width - childDimension.width
      val extraHeight = myDimension.height - childDimension.height
      new Point[Double](extraWidth / 2, extraHeight / 2)
    }

  }


  object FunctionCallShape extends BeShape {

    override def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
      ShapeFactory.buildUnitShape(bounds)
    }

    override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      childDimension.increaseSize(40, 20).increaseSize(config.paddingSmall).increaseSize(config.paddingSmall)
    }

    override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val extraWidth = myDimension.width - 40 - childDimension.width
      val extraHeight = myDimension.height - 20 - childDimension.height
      new Point[Double](extraWidth / 2, extraHeight / 2)
    }
  }

  object FunctionDefineShape extends BeShape {

    override def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
      ShapeFactory.buildStarterShape(bounds)
    }

    override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      childDimension.increaseSize(40, 20).increaseSize(config.paddingSmall).increaseSize(config.paddingSmall)
    }

    override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val extraWidth = myDimension.width -  childDimension.width
      val extraHeight = myDimension.height  - childDimension.height
      new Point[Double](extraWidth / 2, extraHeight / 2)
    }

  }


  private object ShapeFactory {

    private def intToT[T: Fractional](number: Int): T = {
      val N = summon[Fractional[T]]
      import N.*
      fromInt(number)
    }

    private def intDim[T: Fractional](width: Int, height: Int): Dimension[T] = Dimension[T](intToT(width), intToT(height))

    private def intPoint[T: Fractional](x: Int, y: Int): Point[T] = Point[T](intToT(x), intToT(y))

    def buildLiteralShape[T: Fractional](fitIntoBounds: Bounds[T]): AppPathSvgElement[T] = {

      val N = summon[Fractional[T]]
      import N.*
      val bounds = fitIntoBounds

      val r = bounds.height / fromInt(10)

      SvgPathBuilder(fitIntoBounds.startPoint)
        /* test rectangle
        .horizontalLineWithWidth(fitIntoBounds.width)
        .verticalLineWithHeight(fitIntoBounds.height)
        .horizontalLineWithWidth(-fitIntoBounds.width)
        .verticalLineWithHeight(-fitIntoBounds.height)*/
        // base shape
        .moveToRel(new Dimension[T](fromInt(0), bounds.height / fromInt(2)))
        .lineToRel(new Dimension[T](bounds.height / fromInt(2), bounds.height / fromInt(-2)))
        .horizontalLineWithWidth(bounds.width - bounds.height / fromInt(2) - bounds.height / fromInt(10))
        .lineToRel(new Dimension[T](bounds.height / fromInt(10), bounds.height / fromInt(10)))
        .verticalLineWithHeight(bounds.height * fromInt(8) / fromInt(10))
        .lineToRel(new Dimension[T](bounds.height / fromInt(-10), bounds.height / fromInt(10)))
        .horizontalLineWithWidth(-(bounds.width - bounds.height / fromInt(2) - bounds.height / fromInt(10)))
        .lineToRel(new Dimension[T](bounds.height / fromInt(-2), bounds.height / fromInt(-2)))
        .closePath()
        // circle
        .moveToRel(new Dimension[T](bounds.height / fromInt(4), fromInt(0)))
        .addCircle(bounds.height / fromInt(10))
        //    .cubicBezierToRel(intDim(0, -7), intDim(10, -7), intDim(-10, 0))
        //   .cubicBezierToRel(intDim(0, 3), intDim(2, 5), intDim(5, 5))
        //   .cubicBezierToRel(intDim(3, 0), intDim(5, -2), intDim(5, -5))
        .toAppSvgElement()

    }


    def buildDuckShape[T: Fractional](fitIntoBounds: Bounds[T]): AppPathSvgElement[T] = {

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
        .toAppSvgElement()

      /*
      h 95
c 11,0,20,-12,8,-21
c -2,-2,-5,-5,-3,-8
c 1,-1,3,-1,5,-1

c 2,0,4,-1,2,-3
c 2,-2,4,-5,0,-5

c -3,0,-5,0,-5,-3
c 0,-3,-5,-9,-12,-9
c -10,0,-12,10,-10,15
       */

    }

    def buildDateShape[T: Fractional](pBounds: Bounds[T]): AppPathSvgElement[T] = {
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

    def buildStringShape[T: Fractional](pBounds: Bounds[T]): AppPathSvgElement[T] = {
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


    def buildBooleanShape[T: Fractional](pBounds: Bounds[T]): AppPathSvgElement[T] = {
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

    def buildNumericShape[T: Fractional](pBounds: Bounds[T]): AppPathSvgElement[T] = {
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

    def buildStarterShape[T: Fractional](pBounds: Bounds[T]): AppPathSvgElement[T] = {
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

    def buildRectangle[T: Fractional](pBounds: Bounds[T]): AppRectangleSvgElement[T] = {
      AppRectangleSvgElement[T](pBounds)
      /* val bounds = pBounds
         SvgPathBuilder(bounds.startPoint)
           .horizontalLineWithWidth(bounds.width)
           .verticalLineWithHeight(bounds.height)
           .horizontalLineWithWidth(-bounds.width)
           .closePath()
           .toAppSvgElement()*/
    }

    def buildUnitShape[T: Fractional](pBounds: Bounds[T]): AppPathSvgElement[T] = {
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
}