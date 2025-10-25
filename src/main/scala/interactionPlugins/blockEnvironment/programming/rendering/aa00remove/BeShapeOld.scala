package interactionPlugins.blockEnvironment.programming.rendering.aa00remove

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.model.color.RGBColor
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.atomarElements.{AppPathSvgElement, AppRectangleSvgElement, AppTextSvgElement}
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.programming.BeDataType
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.rendering.shapes.*
import interactionPlugins.blockEnvironment.programming.rendering.shapes.atomic.DuckShape
/*
sealed trait BeShapeOld {
  def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement
}

object BeShapeStructureCreation extends BeShapeOld {
  def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = ShapeFactory.buildCreateStructureShape(bounds)

  def minSizeStandalone: Dimension[Double] = new Dimension[Double](30, 30)
}

sealed trait BeShapeParent extends BeShapeOld {

  def minSizeStandalone: Dimension[Double] = new Dimension[Double](10, 10)

  def getAssociatedSvgWithTextChild(config: BeRendererConfig, bounds: Bounds[Double], languageMap: LanguageMap[HumanLanguage]): AppSvgElement = {
    val str = languageMap.getInLanguage(config.language)
    val textDim = config.appFont.measureText(str)
    val textBounds = bounds.startPoint.moveWithDimension(getRelativeChildOffset(config, textDim, bounds.dimension).asDimension).withDimension(textDim)

    val shape = getAssociatedSvgElement(bounds)
    val text = AppTextSvgElement[Double](str, textBounds, config.appFont)
    val textRect = RectangleShape.getAssociatedSvgElement(textBounds).addMods(List(svg.fill := "transparent", svg.stroke := RGBColor.red.toWebStyleString))
    AppDecoratedSvgElement(shape, List(text), List())
  }

  def getAssociatedSvgWithShapeChild(config: BeRendererConfig, bounds: Bounds[Double], child: BeShape, childDim: Dimension[Double]): AppSvgElement = {
    val childBounds = bounds.startPoint.moveWithDimension(getRelativeChildOffset(config, childDim, bounds.dimension).asDimension).withDimension(childDim)

    val myShape = getAssociatedSvgElement(bounds)
    val childSvg = child.getAssociatedSvgElement(childBounds)

    val childRect = RectangleShape.getAssociatedSvgElement(childBounds).addMods(List(svg.fill := "transparent", svg.stroke := RGBColor.red.toWebStyleString))
    AppDecoratedSvgElement(myShape, List(childSvg), List())
  }

  def getAssociatedSvgWithElementChild(config: BeRendererConfig, bounds: Bounds[Double], child: AppSvgElement): AppSvgElement = {
    val myShape = getAssociatedSvgElement(bounds)
    AppDecoratedSvgElement(myShape, List(child), List())
  }

  def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double]

  def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double]
}


object BeShapeParent {
  def apply(dataTypes: Set[BeDataType]): BeShapeParent = {
    if (dataTypes.isEmpty) {
      DuckShape
    } else if (dataTypes.size == 1) {
      dataTypes.head.associatedShape
    } else {
      DuckShape
    }
  }
}

case class ShapeWithInnerLiteralShape(outerShape: BeShapeParent) extends BeShapeParent {
  def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
    outerShape.getAssociatedSvgElement(bounds)
  }

  def minSizeToContainChild(config: BeRendererConfig, smallestChildDimension: Dimension[Double]): Dimension[Double] = {
    val afterLiteral = LiteralShape.minSizeToContainChild(config, smallestChildDimension)
    outerShape.minSizeToContainChild(config, afterLiteral)
  }

  def getRelativeChildOffset(config: BeRendererConfig, smallestChildDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
    val literalDim = LiteralShape.minSizeToContainChild(config, smallestChildDimension)
    val literalOffset = outerShape.getRelativeChildOffset(config, literalDim, myDimension)
    val smallestOffset = LiteralShape.getRelativeChildOffset(config, smallestChildDimension, literalDim)
    literalOffset.moveWithDimension(smallestOffset.asDimension)
  }

  override def getAssociatedSvgWithTextChild(config: BeRendererConfig, bounds: Bounds[Double], languageMap: LanguageMap[HumanLanguage]): AppSvgElement = {
    val str = languageMap.getInLanguage(config.language)
    val textDim = config.appFont.measureText(str)
    val textBounds = bounds.startPoint.moveWithDimension(getRelativeChildOffset(config, textDim, bounds.dimension).asDimension).withDimension(textDim)

    val literalMinDim = LiteralShape.minSizeToContainChild(config, textDim)
    val literalOffset = outerShape.getRelativeChildOffset(config, literalMinDim, bounds.dimension)
    val literalBounds = bounds.startPoint.moveWithDimension(literalOffset.asDimension).withDimension(literalMinDim)
    val literalShape = LiteralShape.getAssociatedSvgWithTextChild(config, literalBounds, languageMap).addMods(List(svg.fill := "transparent", svg.stroke := "black"))

    outerShape.getAssociatedSvgWithElementChild(config, bounds, literalShape)
  }

  override def getAssociatedSvgWithShapeChild(config: BeRendererConfig, bounds: Bounds[Double], child: BeShape, childDim: Dimension[Double]): AppSvgElement = {
    val childBounds = bounds.startPoint.moveWithDimension(getRelativeChildOffset(config, childDim, bounds.dimension).asDimension).withDimension(childDim)
    val literalMinDim = LiteralShape.minSizeToContainChild(config, childDim)
    val literalOffset = outerShape.getRelativeChildOffset(config, literalMinDim, bounds.dimension)
    val literalBounds = bounds.startPoint.moveWithDimension(literalOffset.asDimension).withDimension(literalMinDim)

    val literalShape = LiteralShape.getAssociatedSvgWithShapeChild(config, literalBounds, child, childDim).addMods(List(svg.fill := "transparent", svg.stroke := "black"))
    outerShape.getAssociatedSvgWithElementChild(config, bounds, literalShape)
  }
}


*/










