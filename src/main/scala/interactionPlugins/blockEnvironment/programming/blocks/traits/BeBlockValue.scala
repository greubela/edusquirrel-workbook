package interactionPlugins.blockEnvironment.programming.blocks.traits

import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.connection.BeValueRole

trait BeBlockValue extends BeBlockAtomar{

  def roleInParent: BeValueRole

}
