package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth

import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import datastructures.core.geometry.{Bounds, Dimension}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

abstract class ControlFlowShapeDoubleWidth() extends ControlFlowShape {

  override def widthInIntendations: Int = 2
  
}
