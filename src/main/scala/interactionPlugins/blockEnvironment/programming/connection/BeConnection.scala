package interactionPlugins.blockEnvironment.programming.connection

import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.display.BeBlockDisplay
import interactionPlugins.blockEnvironment.programming.blocks.call.BeBlockLogic


trait BeConnection {
  def connectionRole: BeConnectionRole

  def connectionMayEvaluateTo: Set[BeDataType]

  def connectionCardinality: BeConnectionCardinality
}

case class BeConnectionForBlocks() {
  
}

case class BeConnectionForText() {
  
}


object BeConnection {


}





