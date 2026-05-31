package todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, ControlFlowShape}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground

abstract class ControlFlowShapeSingleWidth extends ControlFlowShape {

  override def widthInIntendations: Int = 1

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true)), true)


}
