package interactionPlugins.blockEnvironment.programming.rendering

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.model.color.AppColor
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.atomarElements.AppTextSvgElement
import contentmanagement.webElements.svg.compositeElements.AppGroupSvgElement
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeDataType, BeDimensionTree}

sealed trait BeParentDisplay {
  def calcDisplaySize(config: BeRendererConfig, minSizeTree: BeDimensionTree, context: BeBlockContext[Dimension[Double]]): Dimension[Double]

  def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double]

  def calcRelativeChildOffsets(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double]
}

case class VBoxParent(withPadding: Boolean, paddingBeforeFirstElement: Dimension[Double]) extends BeParentDisplay {
  override def calcDisplaySize(config: BeRendererConfig, minSizeTree: BeDimensionTree, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = minSizeTree.getData(context.curPosition).get
  
  def calcRelativeChildOffsets(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double] =
    childrenBefore.lastOption.map((lastChildBlock, lastChildPos, lastChildSize) => {
      val newPosY = lastChildPos.y + lastChildSize.height +
        (if (withPadding) config.paddingSmall.height else 0)
      Point[Double](lastChildPos.x, newPosY)
    }).getOrElse({
      if (withPadding) paddingBeforeFirstElement.increaseSize(config.paddingSmall).asPoint else paddingBeforeFirstElement.asPoint
    })

  def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double] =
    if (context.accessChildrenResults.nonEmpty) {
      val childrenHeight = context.accessChildrenResults.map(_.height).sum
      val heightWithPadding = if (withPadding) childrenHeight + (context.childrenValues.size + 1) * config.paddingSmall.height else childrenHeight
      val maxChildrenWidth = context.accessChildrenResults.map(_.width).max
      val widthWithPadding = if (withPadding) maxChildrenWidth + 2 * config.paddingSmall.width else maxChildrenWidth
      Dimension[Double](widthWithPadding, heightWithPadding).increaseSize(paddingBeforeFirstElement).increaseSize(paddingBeforeFirstElement)
    } else {
      paddingBeforeFirstElement.increaseSize(paddingBeforeFirstElement)
    }
}

case class HBoxParent(withPadding: Boolean, paddingBeforeFirstElement: Dimension[Double]) extends BeParentDisplay {
  override def calcDisplaySize(config: BeRendererConfig, minSizeTree: BeDimensionTree, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = minSizeTree.getData(context.curPosition).get

  override def calcRelativeChildOffsets(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double] =
    childrenBefore.lastOption.map((lastChildBlock, lastChildPos, lastChildSize) => {
      val newPosX = lastChildPos.x + lastChildSize.width +
        (if (withPadding) config.paddingSmall.width else 0)
      Point[Double](newPosX, lastChildPos.y)
    }).getOrElse({
      if (withPadding) paddingBeforeFirstElement.increaseSize(config.paddingSmall).asPoint else paddingBeforeFirstElement.asPoint
    })

  override def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double] =
    if (context.accessChildrenResults.nonEmpty) {
      val childrenWidth = context.accessChildrenResults.map(_.width).sum
      val widthWithPadding = if (withPadding) childrenWidth + (context.childrenValues.size + 1) * config.paddingSmall.width else childrenWidth
      val maxChildrenHeight = context.accessChildrenResults.map(_.height).max
      val heightWithPadding = if (withPadding) maxChildrenHeight + 2 * config.paddingSmall.height else maxChildrenHeight
      Dimension[Double](widthWithPadding, heightWithPadding).increaseSize(paddingBeforeFirstElement).increaseSize(paddingBeforeFirstElement)
    } else {
      paddingBeforeFirstElement.increaseSize(paddingBeforeFirstElement)
    }
}


/*
object BeBlockDisplayInfo {

  def addDefaultColoring(dataType: BeDataType, block: BeBlock, shape: AppSvgElement): AppSvgElement = {
    ???
  }



  def shapeDisplay(bounds: Bounds[Double], shapeFactory: Bounds[Double] => AppSvgElement, pStroke: AppColor, pFill: AppColor): AppSvgElement =
    shapeFactory(bounds).addMods(List(
      svg.stroke := pStroke.toWebStyleString,
      svg.fill := pFill.toWebStyleString
    ))


  def shapeAndTextDisplay(config: BeRendererConfig, bounds: Bounds[Double], shapeFactory: Bounds[Double] => AppSvgElement, pStroke: AppColor, pFill: AppColor, displayedText: String): AppSvgElement = {
    val shapeElement = shapeFactory(bounds).addMods(List(
      svg.stroke := pStroke.toWebStyleString,
      svg.fill := pFill.toWebStyleString
    ))
    val textElement = AppTextSvgElement(displayedText, bounds, config.appFont)
    AppGroupSvgElement(List(shapeElement, textElement))
  }

  def duckAndTextDisplay(config: BeRendererConfig, displayedText: LanguageMap[HumanLanguage]): AppSvgElement = {
    val str = displayedText.getInLanguage(config.language)
    val textBounds = config.appFont.measureText(str)
    val duckShape = ShapeFactory.buildDuckShape(textBounds)
    duckShape
  }

  def shapeAndTextDisplay(config: BeRendererConfig, bounds: Bounds[Double], shapeFactory: Bounds[Double] => AppSvgElement, pStroke: AppColor, pFill: AppColor, displayedText: LanguageMap[HumanLanguage]): AppSvgElement = {
    val str = displayedText.getInLanguage(config.language)
    shapeAndTextDisplay(config, bounds, shapeFactory, pStroke, pFill, str)
  }



}
*/