package interactionPlugins.blockEnvironment.programming.blocks

import com.raquo.laminar.api.L
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.atomarElements.AppTextSvgElement
import interactionPlugins.blockEnvironment.programming.blocks.traits.BeBlockValue
import interactionPlugins.blockEnvironment.programming.connection.BeValueRole
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeDimensionTree}

sealed trait BeBlock {
  def getColorlessDisplayElement(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement

  def calcDisplaySize(config: BeRendererConfig, minSizeTree: BeDimensionTree, context: BeBlockContext[Dimension[Double]]): Dimension[Double]

  def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double]
}

abstract class BeBlockParent extends BeBlock {

  def displayShape: BeShape

  override def getColorlessDisplayElement(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement = {
    displayShape.getAssociatedSvgElement(bounds)
  }

  def parentDisplay: BeParentDisplay

  def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = parentDisplay.calcMinSize(config, context)

  override def calcDisplaySize(config: BeRendererConfig, minSizeTree: BeDimensionTree, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = parentDisplay.calcDisplaySize(config, minSizeTree, context)

  def calcRelativeChildOffsets(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double] = parentDisplay.calcRelativeChildOffsets(config, childrenBefore, curChild)

  def nodeInsertionsForDisplay(existingChildren: List[BeBlock], existingValueChildrenWithPosition: Map[BeValueRole, (BeBlockValue, Int)]): List[(Int, BeBlock)] = List()

}

abstract class BeBlockAtomar extends BeBlock {

  def displayShape: BeShape

  def displayedText: Option[LanguageMap[HumanLanguage]]

  override def getColorlessDisplayElement(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement = {
    if (displayedText.nonEmpty) displayShape.getAssociatedSvgWithTextChild(config, bounds, displayedText.get)
    else displayShape.getAssociatedSvgElement(bounds)
  }

  override def calcDisplaySize(config: BeRendererConfig, minSizeTree: BeDimensionTree, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = {
    val maxHeight = context.traversalInfoForSiblingsInParent.map(curSibling => minSizeTree.getData(curSibling.curPosition).get).maxBy(_.height)
    minSizeTree.getData(context.curPosition).get.copy(height = maxHeight.height)
  }

  def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = {
    //val curStr = displayedText.map(_.getInLanguage(config.language)).getOrElse("[...]")
    val curMap = displayedText.getOrElse(LanguageMap.universalMap("[...]"))
    val strDim = config.appFont.measureText(curMap.getInLanguage(config.language))
    displayShape.minSizeToContainChild(config, strDim)
  }
}

case class BeBlockDisplayText(displayedText: LanguageMap[HumanLanguage]) extends BeBlock {

  override def getColorlessDisplayElement(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement = {
    val str = displayedText.getInLanguage(config.language)
    AppTextSvgElement(str, bounds, config.appFont)
  }

  override def calcDisplaySize(config: BeRendererConfig, minSizeTree: BeDimensionTree, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = {
    val maxHeight = context.traversalInfoForSiblingsInParent.map(curSibling => minSizeTree.getData(curSibling.curPosition).get).maxBy(_.height)
    minSizeTree.getData(context.curPosition).get.copy(height = maxHeight.height)
  }

  override def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = {

    val curStr = displayedText.getInLanguage(config.language)
    config.appFont.measureText(curStr).increaseSize(config.paddingSmall)

  }
}


