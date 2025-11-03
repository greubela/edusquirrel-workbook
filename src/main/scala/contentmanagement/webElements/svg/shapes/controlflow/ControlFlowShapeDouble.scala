package contentmanagement.webElements.svg.shapes.controlflow

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import contentmanagement.webElements.svg.shapes.controlflow.overlays.ControlLineOverlay
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

abstract class ControlFlowShapeDouble() extends ControlFlowShape {

  override def widthInIntendations: Int = 2


}
