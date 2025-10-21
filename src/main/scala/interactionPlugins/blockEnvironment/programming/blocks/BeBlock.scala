package interactionPlugins.blockEnvironment.programming.blocks

import interactionPlugins.blockEnvironment.programming.blocks.call.*
import contentmanagement.model.AppFont
import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.model.language.*
import interactionPlugins.blockEnvironment.programming.BeDataType
import interactionPlugins.blockEnvironment.programming.BeProgram.*
import interactionPlugins.blockEnvironment.programming.connection.*
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.BeProgram.*

trait BeBlock {
  

  def roleInParent: BeConnectionRole

  def layoutManager: BeBlockDisplayManager
  
  

}

