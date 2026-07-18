package todomove.webElementsOld.webElements.svg.shapes.special.nested

import todomove.webElementsOld.webElements.svg.shapes.composite.*
import todomove.webElementsOld.webElements.svg.shapes.*
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.builder.controlFlow.ControlFlowPart
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, ControlFlowAndExpressionShape, ControlFlowShape}
import todomove.webElementsOld.webElements.svg.shapes.composite.{HBoxSameHeight, HorizontalAlignment, VerticalAlignment}

case class ShapeWithControlFlow(cf: ControlFlowShape, expr: BeShape, newCF: ControlFlowPart=null)  extends ControlFlowAndExpressionShape{
  
  def onlyControlFlowShape: Option[BeShape] = Some(cf)

  def onlyExpressionShape: Option[BeShape] = Some(expr)

  val box = HBoxSameHeight(List(cf, expr), false, HorizontalAlignment.Left, VerticalAlignment.Center)

  def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = {
    box.displaySize(rendererConfig)
  }

  def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    box.render(rendererConfig, bounds)
  }

}

object ShapeWithControlFlow {

}