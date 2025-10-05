package interactionPlugins.blockEnvironment.programming.rendering

import contentmanagement.model.AppFont
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.BeProgram.BeProgramTreeContext

trait BeBlockLayoutManager() {

  def useDifferentShape: Option[Bounds[Double] => AppSvgElement]

  def getNiceSize(config: BeRendererConfig, contextNiceSizes: BeProgramTreeContext[Dimension[Double]], minDimensions: BeDimensionTree): Dimension[Double]

  def getMinSize(config: BeRendererConfig, context: BeProgramTreeContext[Dimension[Double]]): Dimension[Double]

  def calculateRelativeChildOffset(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double]

}

object BeBlockLayoutManager {
  def simpleStringNodeLayoutManager(string: String): BeBlockLayoutManager = new BeBlockLayoutManager {
    override def getNiceSize(config: BeRendererConfig, contextNiceSizes: BeProgramTreeContext[Dimension[Double]], minDimensions: BeDimensionTree): Dimension[Double] = {
      println("[WARN] called getNiceSize: not implemented yet") // todo
      Dimension[Double](0, 0)
    }

    override def getMinSize(config: BeRendererConfig, context: BeProgramTreeContext[Dimension[Double]]): Dimension[Double] = {
      config.appFont.measureText(string).increaseSize(config.paddingSmall)
    }

    override def calculateRelativeChildOffset(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double] = {
      println("[WARN] called calculateRelativeChildOffset on simpleStringNodeLayoutManager: not defined!")
      Point[Double](0, 0)
    }

    override val useDifferentShape: Option[Bounds[Double] => AppSvgElement] = None

  }

  def SimpleHBoxChildrenLayoutManager(initialOffset: Point[Double], minSize: Dimension[Double], pUseDifferentShape: Option[Bounds[Double] => AppSvgElement]): BeBlockLayoutManager = new BeBlockLayoutManager {
    override def getNiceSize(config: BeRendererConfig, contextNiceSizes: BeProgramTreeContext[Dimension[Double]], minDimensions: BeDimensionTree): Dimension[Double] = {
      println("[WARN] called getNiceSize: not implemented yet") // todo
      Dimension[Double](0, 0)
    }

    override def getMinSize(config: BeRendererConfig, context: BeProgramTreeContext[Dimension[Double]]): Dimension[Double] = {
      val childrenDims: List[Dimension[Double]] = context.accessChildrenResults
      val childrenWidthSum = childrenDims.map(_.width).sum
      val childrenHeightMax = childrenDims.map(_.height).max
      Dimension[Double](childrenWidthSum, childrenHeightMax).increaseSize(config.paddingBig).ensureAtLeastAsBigAs(minSize)
    }

    override def calculateRelativeChildOffset(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double] = {
      childrenBefore.lastOption.map( (lastChildBlock, lastChildPos, lastChildSize) => {
        val newPosX = lastChildPos.x + lastChildSize.width + config.paddingSmall.width
        Point[Double](newPosX, lastChildPos.y)
      }).getOrElse(initialOffset)
    }

    override val useDifferentShape: Option[Bounds[Double] => AppSvgElement] = pUseDifferentShape

  }

  def SimpleVBoxChildrenLayoutManager(initialOffset: Point[Double], minSize: Dimension[Double], pUseDifferentShape: Option[Bounds[Double] => AppSvgElement]): BeBlockLayoutManager = new BeBlockLayoutManager {
    override def getNiceSize(config: BeRendererConfig, contextNiceSizes: BeProgramTreeContext[Dimension[Double]], minDimensions: BeDimensionTree): Dimension[Double] = {
      println("[WARN] called getNiceSize: not implemented yet") // todo
      Dimension[Double](0, 0)
    }

    override def getMinSize(config: BeRendererConfig, context: BeProgramTreeContext[Dimension[Double]]): Dimension[Double] = {
      val maxChildrenWidth = context.accessChildrenResults.map(_.width).toList.max
      Dimension[Double](maxChildrenWidth, minSize.height).ensureAtLeastAsBigAs(minSize)
    }

    override def calculateRelativeChildOffset(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double] = {
      childrenBefore.lastOption.map( (lastChildBlock, lastChildPos, lastChildSize) => {
        val newPosY = lastChildPos.y + lastChildSize.height + config.paddingSmall.height
        Point[Double](lastChildPos.x, newPosY)
      }).getOrElse(initialOffset)
    }


    override val useDifferentShape: Option[Bounds[Double] => AppSvgElement] = pUseDifferentShape
  }

}



