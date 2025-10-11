package interactionPlugins.blockEnvironment.programming.connection

import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.display.BeBlockDisplay


trait BeConnection {
  def connectionRole: BeConnectionRole

  def connectionMayEvaluateTo: Set[BeDataType]

  def connectionCardinality: BeConnectionCardinality

}





