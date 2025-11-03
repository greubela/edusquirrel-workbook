package contentmanagement.webElements.svg.shapes

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, svg}
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.compositeElements.AppGroupSvgElement
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeContainerable
import contentmanagement.webElements.svg.shapes.controlflow.overlays.*
import contentmanagement.webElements.svg.shapes.datatypes.{DuckShape, RectangleShape, UnitShape}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

sealed trait BeShape {
  def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double]

  def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement

  def addSignalAmends(newAmends: Seq[Signal[L.Modifier[L.SvgElement]]]): BeShape = this match {
    case AmendedShape(base, amends, signalAmends) => AmendedShape(base, amends, signalAmends ++ newAmends)
    case _ => AmendedShape(this, List(), newAmends)
  }

  def addAmends(newAmends: Seq[L.Modifier[L.SvgElement]]): BeShape = this match {
    case AmendedShape(base, amends, signalAmends) => AmendedShape(base, amends ++ newAmends, signalAmends)
    case _ => AmendedShape(this, newAmends, List())
  }
}

trait BeShapeDecoration extends BeShape with ControlLineOverlay {
  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = Dimension[Double](rendererConfig.controlSegmentSize / 5.0 * 8.0, rendererConfig.controlSegmentSize / 5.0 * 8.0)

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    getOverlayPath(rendererConfig, bounds).toAppSvgElement()
  }
}

trait ControlFlowShape extends BeShape {

  def widthInIntendations: Int

  def background: BeShapeContainerable

  def continuesWithoutInterruption: Boolean

  def minHeightInSegments: Int

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] =
    Dimension[Double](rendererConfig.controlSegmentSize * widthInIntendations * 6, rendererConfig.controlSegmentSize * minHeightInSegments)

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement

}

case class AmendedShape(baseShape: BeShape, amends: Seq[L.Modifier[L.SvgElement]], amendsSignal: Seq[Signal[L.Modifier[L.SvgElement]]]) extends BeShape {

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = baseShape.displaySize(config)

  override def render(config: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    baseShape.render(config, bounds).addMods(amends).addSignalMods(amendsSignal)
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

    override def displaySize(config: BeRenderingConfig): Dimension[Double] = Dimension[Double](15, 15)

    def minSizeToContainChild(config: BeRenderingConfig, childDimension: Dimension[Double]): Dimension[Double] = {
      val spaceBefore = spaceBeforeChild(config, childDimension)
      val spaceAfter = spaceAfterChild(config, childDimension)
      childDimension.increaseSize(spaceBefore).increaseSize(spaceAfter)
    }

    def getRelativeChildOffset(config: BeRenderingConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
      val spaceBefore = spaceBeforeChild(config, childDimension)
      val spaceAfter = spaceAfterChild(config, childDimension)
      val availableSpace = myDimension.decreaseSize(spaceBefore).decreaseSize(spaceAfter).decreaseSize(childDimension)
      spaceBefore.asPoint //.increaseSize(Dimension(availableSpace.width / 2, availableSpace.height / 2)).asPoint
    }

    def render(config: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
      getPathBuilder(config, bounds).toAppSvgElement()
    }


  }


  trait BeShapeComposite extends BeShape {

    def atomicShapes: List[BeShapeAtomic] = children.filter(_.isInstanceOf[BeShapeAtomic]).map(_.asInstanceOf[BeShapeAtomic])

    def textShapes: List[TextShape] = children.filter(_.isInstanceOf[TextShape]).map(_.asInstanceOf[TextShape])

    def children: List[BeShape]
  }

  abstract class BeShapeBox extends BeShapeComposite {
    def render(config: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
      val childrenBounds = calcChildrenBounds(config, bounds)
      val childrenWithBounds = children.map(curChild => curChild.render(config, childrenBounds(curChild)))
      AppGroupSvgElement(childrenWithBounds)
    }

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

