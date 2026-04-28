package contentmanagement.webElements.svg.shapes

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Signal
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.compositeElements.AppGroupSvgElement
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeContainerable
import contentmanagement.webElements.svg.shapes.datatypes.{DuckShape, RectangleShape, UnitShape}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.*
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer.*
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}

case class ShapeAmends(baseAmends: Seq[L.Modifier[L.SvgElement]], signalAmends: Seq[Signal[L.Modifier[L.SvgElement]]], doOnRendering: Seq[(Bounds[Double], BeShape) => Any])


sealed trait BeShape {
  def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double]

  def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement

  def addAmends(newAmends: ShapeAmends): AmendedShape = this match {
    case AmendedShape(base: BeShape, existingAmends: ShapeAmends) => {
      AmendedShape(base, ShapeAmends(existingAmends.baseAmends ++ newAmends.baseAmends, existingAmends.signalAmends ++ newAmends.signalAmends, existingAmends.doOnRendering ++ newAmends.doOnRendering))
    }
    case _ => AmendedShape(this, newAmends)
  }

  def addSignalAmend(newAmend: Signal[L.Modifier[L.SvgElement]]): BeShape = addAmends(ShapeAmends(List(), newAmend :: Nil, List()))

  def addSignalAmends(newAmends: Seq[Signal[L.Modifier[L.SvgElement]]]): BeShape = addAmends(ShapeAmends(List(), newAmends, List()))

  def addAmend(newAmend: L.Modifier[L.SvgElement]): BeShape = addAmends(ShapeAmends(newAmend :: Nil, List(), List()))

  def addAmends(newAmends: Seq[L.Modifier[L.SvgElement]]): BeShape = addAmends(ShapeAmends(newAmends, List(), List()))

  def addOnRendering(newHandle: (Bounds[Double], BeShape) => Any): BeShape = addAmends(ShapeAmends(List(), List(), newHandle :: Nil))
}

trait ControlFlowAndExpressionShape extends BeShape {

  def onlyControlFlowShape: Option[BeShape]
  def onlyExpressionShape: Option[BeShape]

  def expressionShapeOrEverything: BeShape = onlyExpressionShape.getOrElse(this)
  def controlFlowShapeOrEverything: BeShape = onlyControlFlowShape.getOrElse(this)

}
trait ControlFlowShape extends BeShape {

  def widthInIntendations: Int

  def minHeightInSegments: Int

  def background: BeShapeContainerable

  def shapeRenderBackground(renderingConfig: BeRenderingConfig): BeShape = {
    background.addAmends(renderingConfig.amendFactory.defaultControlFlowBackgroundAmend)
  }

  override def displaySize(renderingConfig: BeRenderingConfig): Dimension[Double] =
    Dimension[Double](renderingConfig.controlSegmentSize * widthInIntendations * 6, renderingConfig.controlSegmentSize * minHeightInSegments)

  override def render(renderingConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = shapeRenderBackground(renderingConfig).render(renderingConfig, bounds)

  def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder
}


trait BeShapeDecoration extends BeShape {

  def getAmends(renderingConfig: BeRenderingConfig): Seq[L.Modifier[L.SvgElement]]

  def getOverlayPath(rendererConfig: BeRenderingConfig, centeredAt: Point[Double]): SvgPathBuilder[Double]

  def render(rendererConfig: BeRenderingConfig, centerPoint: Point[Double]): AppSvgElement = {
    getOverlayPath(rendererConfig, centerPoint).toFixedDimensionShape.addAmends(getAmends(rendererConfig)).render(rendererConfig, Bounds(centerPoint, displaySize(rendererConfig)))
  }

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    render(rendererConfig, bounds.centerPoint)
  }

}

case class AmendedShape(amendedShape: BeShape, amends: ShapeAmends) extends BeShape {

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = amendedShape.displaySize(config)

  override def render(config: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    amends.doOnRendering.foreach(_.apply(bounds, this))
    amendedShape.render(config, bounds).addMods(amends.baseAmends).addSignalMods(amends.signalAmends)
  }

}

object BeShape {

  trait BeShapeAtomic extends BeShape {

  }


  val allAtomicShapes: List[BeShapeAtomic] = List(DuckShape, RectangleShape, UnitShape)

  abstract class BeShapePathBased extends BeShapeAtomic with BeShapeContainerable {

    protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double]

    protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double]

    protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double]

    override def displaySize(config: BeRenderingConfig): Dimension[Double] = Dimension[Double](8, 8)

    def minSizeToContainChild(config: BeRenderingConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      val spaceBefore = spaceBeforeChild(config, childDimension)
      val spaceAfter = spaceAfterChild(config, childDimension)
      childDimension.increaseSize(spaceBefore).increaseSize(spaceAfter)
    }

    def getRelativeChildOffset(config: BeRenderingConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val spaceBefore = spaceBeforeChild(config, childDimension)
      val spaceAfter = spaceAfterChild(config, childDimension)
      val availableSpace: Dimension[Double] = myDimension.decreaseSize(spaceBefore).decreaseSize(spaceAfter).decreaseSize(childDimension)
      spaceBefore.increaseSize(Dimension(availableSpace.width / 2, availableSpace.height / 2)).asPoint
    }

    def render(config: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
      getPathBuilder(config, bounds).toFixedDimensionShape.render(config, bounds)
    }


  }

  trait BeShapeComposite extends BeShape {

  }

  abstract class BeShapeBox extends BeShapeComposite {
    def render(config: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
      val childrenBounds = calcChildrenBounds(config, bounds)
      val childrenWithBounds = children.map(curChild => curChild.render(config, childrenBounds(curChild)))
      AppGroupSvgElement(childrenWithBounds)
    }

    def children: List[BeShape]

    def calcChildrenBounds(config: BeRenderingConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]]
  }


  trait BeShapeContainerable extends BeShape {
    def minSizeToContainChild(config: BeRenderingConfig, childDimension: Dimension[Double]): Dimension[Double]

    def getRelativeChildOffset(config: BeRenderingConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double]

    def getChildBounds(config: BeRenderingConfig, myBounds: Bounds[Double], childDimension: Dimension[Double]): Bounds[Double] = {
      val childRelOffset = getRelativeChildOffset(config, childDimension, myBounds.dimension)
      myBounds.startPoint.moveWithDimension(childRelOffset.asDimension).withDimension(childDimension)

    }

  }


}

