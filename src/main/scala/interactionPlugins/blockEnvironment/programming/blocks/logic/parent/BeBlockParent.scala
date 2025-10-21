package interactionPlugins.blockEnvironment.programming.blocks.call.parent

import interactionPlugins.blockEnvironment.programming.blocks.call.BeBlockLogic
import interactionPlugins.blockEnvironment.programming.connection.BeConnection
import interactionPlugins.blockEnvironment.programming.rendering.BeBlockParentDisplayManager

trait BeBlockParent extends BeBlockLogic{

  def getConnections: List[BeConnection]

  def parentDisplayManager: BeBlockParentDisplayManager

  // def expandChildren(progLanguage: ProgrammingLanguage, humanLanguage: Applanguage, logicChildren: List[BeBlockLogic]): List[BeBlockLogic]
  
  //   def expandConnection(connection: BeConnection, existingChildren: List[BeBlockLogic]): List[BeBlock] = {


}
