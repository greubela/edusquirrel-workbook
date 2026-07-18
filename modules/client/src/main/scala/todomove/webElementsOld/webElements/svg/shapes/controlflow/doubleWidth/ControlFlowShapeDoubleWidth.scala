package todomove.webElementsOld.webElements.svg.shapes.controlflow.doubleWidth

import it.evadid.core.datastructures.geometry.{Bounds, Dimension}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilder
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, ControlFlowShape}

abstract class ControlFlowShapeDoubleWidth() extends ControlFlowShape {

  override def widthInIntendations: Int = 2
  
}
