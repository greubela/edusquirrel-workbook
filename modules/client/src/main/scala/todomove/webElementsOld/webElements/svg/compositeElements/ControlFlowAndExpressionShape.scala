package todomove.webElementsOld.webElements.svg.compositeElements

import todomove.webElementsOld.webElements.svg.shapes.*
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.shapes.datatypes.UnitShape


/*
enum RenderOption {
  case RENDER_CONTROL_FLOW, RENDER_EXPRESSION, RENDER_BOTH
}

case class ControlFlowAndExpressionShape(controlFlowShape: Option[ControlFlowPart], ExpressionShape: Option[BeShape], combinedShape: Option[BeShape], renderOption: RenderOption = RENDER_BOTH) {

  def getActiveShapeOrUnit: BeShape = renderOption match {
    case RENDER_CONTROL_FLOW => controlFlowShape.getOrElse(UnitShape)
    case RENDER_EXPRESSION => ExpressionShape.getOrElse(UnitShape)
    case RENDER_BOTH => combinedShape.getOrElse(UnitShape)
  }

  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = getActiveShapeOrUnit.displaySize(rendererConfig)

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = getActiveShapeOrUnit.render(rendererConfig, bounds)
  
 
}
*/