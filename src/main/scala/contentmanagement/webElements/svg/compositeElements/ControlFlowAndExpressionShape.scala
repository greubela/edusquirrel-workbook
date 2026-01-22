package contentmanagement.webElements.svg.compositeElements

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.shapes.*
import contentmanagement.webElements.svg.shapes.datatypes.UnitShape
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
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