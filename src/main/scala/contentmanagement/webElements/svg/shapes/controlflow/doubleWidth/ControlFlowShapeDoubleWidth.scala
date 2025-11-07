package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.shapes.controlflow.overlays.ControlLineOverlay
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

abstract class ControlFlowShapeDoubleWidth() extends ControlFlowShape {

  override def widthInIntendations: Int = 2


}
