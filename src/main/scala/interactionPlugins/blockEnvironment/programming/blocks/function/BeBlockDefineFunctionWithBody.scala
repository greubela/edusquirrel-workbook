package interactionPlugins.blockEnvironment.programming.blocks.function

import contentmanagement.model.geometry.Dimension
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.traits.*
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.rendering.shapes.*
import interactionPlugins.blockEnvironment.programming.rendering.shapes.atomic.*

case class BeBlockDefineFunctionWithBody(
                                          function: BeFunction
                                        )
  extends BeBlockParent with BeBlockLogic with BeBlockStructureDefinition {


  def displayShape: BeShape = FunctionDefineShape

  def parentDisplay: BeParentDisplay = VBoxParent(true, new Dimension[Double](20, 5))


}
