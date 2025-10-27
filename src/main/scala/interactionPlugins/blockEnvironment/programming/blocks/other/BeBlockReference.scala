package interactionPlugins.blockEnvironment.programming.blocks.other

import interactionPlugins.blockEnvironment.programming.BeBlockContext
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockAtomar}

enum BeBlockReference {
  case ReferenceExistingBlock(structure: BeBlockContext, nrInChildList: Int, block: BeBlock)
  case NewBlock(valueChild: BeBlockAtomar)
}

