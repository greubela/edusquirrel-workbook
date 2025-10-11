package interactionPlugins.blockEnvironment.programming.rendering

import contentmanagement.model.geometry.{Dimension, Point}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

trait BeBlockParentDisplayManager() {
  def calcRelativeChildOffsets(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double]
}

object BeBlockParentDisplayManager {


  // HBOX
  def calculateMinSizeForHBox[B <: BeBlock](config: BeRendererConfig, context: BeBlockContext[Dimension[Double]], minSize: Dimension[Double], paddingBeforeFirstElement: Dimension[Double], withPadding: Boolean): Dimension[Double] = {
    val childrenWidth = context.accessChildrenResults.map(_.width).sum
    val widthWithPadding = if (withPadding) childrenWidth + (context.childrenValues.size + 1) * config.paddingSmall.width else childrenWidth
    val maxChildrenHeight = context.accessChildrenResults.map(_.height).max
    val heightWithPadding = if (withPadding) maxChildrenHeight + 2 * config.paddingSmall.height else maxChildrenHeight
    Dimension[Double](widthWithPadding, heightWithPadding).increaseSize(paddingBeforeFirstElement).increaseSize(paddingBeforeFirstElement)
  }

  def calculateRelativeOffsetsAsHBox(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock, paddingBeforeFirstElement: Dimension[Double], withPadding: Boolean): Point[Double] =
    childrenBefore.lastOption.map((lastChildBlock, lastChildPos, lastChildSize) => {
      val newPosX = lastChildPos.x + lastChildSize.width +
        (if (withPadding) config.paddingSmall.width else 0)
      Point[Double](newPosX, lastChildPos.y)
    }).getOrElse({
      if (withPadding) paddingBeforeFirstElement.increaseSize(config.paddingSmall).asPoint else paddingBeforeFirstElement.asPoint
    })

  // VBOX
  def calculateMinSizeForVBox[B <: BeBlock](config: BeRendererConfig, context: BeBlockContext[Dimension[Double]], paddingBeforeFirstElement: Dimension[Double], withPadding: Boolean): Dimension[Double] = {
    val childrenHeight = context.accessChildrenResults.map(_.height).sum
    val heightWithPadding = if (withPadding) childrenHeight + (context.childrenValues.size + 1) * config.paddingSmall.height else childrenHeight
    val maxChildrenWidth = context.accessChildrenResults.map(_.width).max
    val widthWithPadding = if (withPadding) maxChildrenWidth + 2 * config.paddingSmall.width else maxChildrenWidth
    Dimension[Double](widthWithPadding, heightWithPadding).increaseSize(paddingBeforeFirstElement).increaseSize(paddingBeforeFirstElement)
  }

  def calculateRelativeOffsetsAsVBox(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock, paddingBeforeFirstElement: Dimension[Double], withPadding: Boolean): Point[Double] =
    childrenBefore.lastOption.map((lastChildBlock, lastChildPos, lastChildSize) => {
      val newPosY = lastChildPos.y + lastChildSize.height +
        (if (withPadding) config.paddingSmall.height else 0)
      Point[Double](lastChildPos.x, newPosY)
    }).getOrElse({
      if (withPadding) paddingBeforeFirstElement.increaseSize(config.paddingSmall).asPoint else paddingBeforeFirstElement.asPoint
    })


}