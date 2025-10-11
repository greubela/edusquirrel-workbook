package interactionPlugins.blockEnvironment.programming.blocks.logic.parent

import interactionPlugins.blockEnvironment.programming.blocks.logic.BeBlockLogic
import interactionPlugins.blockEnvironment.programming.connection.BeConnection
import interactionPlugins.blockEnvironment.programming.rendering.BeBlockParentDisplayManager

trait BeBlockParent extends BeBlockLogic{

  def getConnections: List[BeConnection]

  def parentDisplayManager: BeBlockParentDisplayManager

}
