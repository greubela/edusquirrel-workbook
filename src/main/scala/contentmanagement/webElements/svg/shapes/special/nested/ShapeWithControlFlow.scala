package contentmanagement.webElements.svg.shapes.special.nested

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.controlFlow.ControlFlowPart
import contentmanagement.webElements.svg.shapes.composite.*
import contentmanagement.webElements.svg.shapes.*
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

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