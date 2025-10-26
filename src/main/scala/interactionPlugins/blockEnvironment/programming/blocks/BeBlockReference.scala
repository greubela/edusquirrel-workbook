package interactionPlugins.blockEnvironment.programming.blocks

import interactionPlugins.blockEnvironment.programming.BeBlockContext

protected enum BeBlockReference {
  case ReferenceExistingBlock(structure: BeBlockContext, nrInChildList: Int, block: BeBlock)
  case NewBlock(valueChild: BeBlockAtomar)
}