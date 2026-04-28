package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth

import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}

abstract class ControlFlowShapeDoubleWidth() extends ControlFlowShape {

  override def widthInIntendations: Int = 2
  
}
