package interactionPlugins.blockEnvironment.programming.blocks.function

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.ProgrammingLanguage
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.traits.*
import interactionPlugins.blockEnvironment.programming.connection.BeValueRole
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.rendering.BeShape.FunctionDefineShape

case class BeBlockDefineFunctionExternalFunctionality(
                                                       function: BeFunction
                                                     )
  extends BeBlockParent with BeBlockLogic  with BeBlockStructureDefinition {


  override def displayShape: BeShape = FunctionDefineShape

  override def parentDisplay: BeParentDisplay = VBoxParent(true, new Dimension[Double](50, 25))
}
