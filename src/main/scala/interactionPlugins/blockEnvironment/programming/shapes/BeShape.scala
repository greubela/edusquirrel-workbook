package interactionPlugins.blockEnvironment.programming.shapes

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, svg}
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.compositeElements.AppGroupSvgElement
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.shapes.atomic.TextShape

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

case class BeShapeAmendFactory(rendererConfig: BeRenderingConfig) {

  def muteOnTreeDragged(signal: Signal[BeControllerState], regularColors: Seq[L.Modifier[L.SvgElement]]): Seq[Signal[L.Modifier[L.SvgElement]]] = {
    signalBasedAmendChooser(signal.map(_.draggingEvent.nonEmpty), mutedColorsAmend, regularColors)
  }

  def signalBasedAmendChooser(firstOne: Signal[Boolean], firstAmends: Seq[L.Modifier[L.SvgElement]], secondAmends: Seq[L.Modifier[L.SvgElement]]): Seq[Signal[L.Modifier[L.SvgElement]]] = {
    firstAmends.zip(secondAmends).map { (first, second) => firstOne.signal.map(if (_) first else second) }
  }

  def defaultFunctionColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.yellows(3).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.yellows(0).toWebStyleString,
  )

  def darkVariableColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.stroke := rendererConfig.colorPalette.grayscale(3).toWebStyleString,
    svg.fill := rendererConfig.colorPalette.grayscale(4).toWebStyleString,
  )

  def lightVariableColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.stroke := rendererConfig.colorPalette.grayscale(1).toWebStyleString,
    svg.fill := rendererConfig.colorPalette.grayscale(4).toWebStyleString,
  )

  def mutedColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.grayscale(4).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.grayscale(4).toWebStyleString
  )

  def errorColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.reds(3).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.reds(1).toWebStyleString
  )

  def acceptingColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.greens(3).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.greens(1).toWebStyleString
  )

  def literalColorsAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := "white",
    svg.stroke := rendererConfig.colorPalette.grayscale(1).toWebStyleString
  )

  def defaultStartBlockAmend: Seq[L.Modifier[L.SvgElement]] = List(
    svg.fill := rendererConfig.colorPalette.yellows(1).toWebStyleString,
    svg.stroke := rendererConfig.colorPalette.yellows(0).toWebStyleString,
  )

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

  abstract class BeShapePathBased extends BeShape with BeShapeContainerable {

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
      spaceBefore.increaseSize(new Dimension(availableSpace.width / 2, availableSpace.height / 2)).asPoint
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

