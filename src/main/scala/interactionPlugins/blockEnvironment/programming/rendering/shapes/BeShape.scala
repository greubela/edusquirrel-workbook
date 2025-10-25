package interactionPlugins.blockEnvironment.programming.rendering.shapes

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.atomarElements.AppTextSvgElement
import contentmanagement.webElements.svg.compositeElements.AppGroupSvgElement
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.programming.rendering.BeRendererConfig
import interactionPlugins.blockEnvironment.programming.rendering.shapes.atomic.TextShape

sealed trait BeShape {
  def displaySize(config: BeRendererConfig): Dimension[Double]

  def renderColorless(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement

  def renderDefaultColoring(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement
}

object BeShape {

  trait BeShapeAtomic extends BeShape {

  }

  abstract class BeShapePathBased extends BeShape with BeShapeContainerable {

    protected def getPathBuilder(config: BeRendererConfig, bounds: Bounds[Double]): SvgPathBuilder[Double]

    protected def spaceBeforeChild(config: BeRendererConfig, childDim: Dimension[Double]): Dimension[Double]

    protected def spaceAfterChild(config: BeRendererConfig, childDim: Dimension[Double]): Dimension[Double]

    override def displaySize(config: BeRendererConfig): Dimension[Double] = Dimension[Double](15, 15)

    protected def getDefaultColorAmends(config: BeRendererConfig): Seq[L.Modifier[L.SvgElement]] =
      List(
        svg.fill := config.colorPalette.grayscale(4).toWebStyleString,
        svg.stroke := config.colorPalette.grayscale(1).toWebStyleString
      )

    def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      val spaceBefore = spaceBeforeChild(config, childDimension)
      val spaceAfter = spaceAfterChild(config, childDimension)
      childDimension.increaseSize(spaceBefore).increaseSize(spaceAfter)
    }

    def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val spaceBefore = spaceBeforeChild(config, childDimension)
      val spaceAfter = spaceAfterChild(config, childDimension)
      val availableSpace = myDimension.decreaseSize(spaceBefore).decreaseSize(spaceAfter).decreaseSize(childDimension)
      spaceBefore.increaseSize(new Dimension(availableSpace.width / 2, availableSpace.height / 2)).asPoint
    }

    def renderColorless(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement = {
      getPathBuilder(config, bounds).toAppSvgElement()
    }

    def renderDefaultColoring(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement = {
      getPathBuilder(config, bounds).toAppSvgElement().addMods(getDefaultColorAmends(config))
    }

  }


  trait BeShapeComposite extends BeShape {

    def atomicShapes: List[BeShapeAtomic] = children.filter(_.isInstanceOf[BeShapeAtomic]).map(_.asInstanceOf[BeShapeAtomic])

    def textShapes: List[TextShape] = children.filter(_.isInstanceOf[TextShape]).map(_.asInstanceOf[TextShape])

    def children: List[BeShape]

  }

  abstract class BeShapeBox extends BeShapeComposite {
    def renderColorless(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement = {
      val childrenBounds = calcChildrenBounds(config, bounds)
      val children = childrenBounds.toList.map((child, childBounds) => child.renderColorless(config, childBounds))
      AppGroupSvgElement(children)
    }

    def renderDefaultColoring(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement = {
      val childrenBounds = calcChildrenBounds(config, bounds)
      val children = childrenBounds.toList.map((child, childBounds) => child.renderDefaultColoring(config, childBounds))
      AppGroupSvgElement(children)
    }

    def calcChildrenBounds(config: BeRendererConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]]
  }


  trait BeShapeContainerable extends BeShape {
    def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double]

    def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double]

    def getChildBounds(config: BeRendererConfig, myBounds: Bounds[Double], childDimension: Dimension[Double]): Bounds[Double] = {
      val childRelOffset = getRelativeChildOffset(config, childDimension, myBounds.dimension)
      myBounds.startPoint.moveWithDimension(childRelOffset.asDimension).withDimension(childDimension)
    }

  }


}

