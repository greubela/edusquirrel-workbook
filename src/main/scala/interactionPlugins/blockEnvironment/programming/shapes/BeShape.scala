package interactionPlugins.blockEnvironment.programming.shapes

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, svg}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.compositeElements.AppGroupSvgElement
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import com.raquo.laminar.api.L.seqToModifier
import com.raquo.laminar.api.seqToModifier
import com.raquo.laminar.api.L.componentSeqToInserter
import com.raquo.laminar.api.L.textToTextNode
import com.raquo.laminar.api.componentSeqToInserter
import com.raquo.laminar.api.componentToNode
import com.raquo.laminar.api.L.componentToNode
import com.raquo.laminar.api.textToTextNode
import com.raquo.laminar.api.L.componentToInserter
import com.raquo.laminar.api.L.textToInserter
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.BeShapeContainerable
import interactionPlugins.blockEnvironment.programming.shapes.controlflow.ControlFlowConnectorBackground
import interactionPlugins.blockEnvironment.programming.shapes.datatypes.{DuckShape, RectangleShape, UnitShape}

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

trait ControlFlowShape extends BeShape{
  def background: BeShapeContainerable
  def controlFlowShape: BeShape
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

  val allAtomicShapes: List[BeShapeAtomic] = List( DuckShape, RectangleShape, UnitShape)

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
      val children = childrenBounds.toList.map((child, childBounds) => child.render(config, childBounds))
      AppGroupSvgElement(children)
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

