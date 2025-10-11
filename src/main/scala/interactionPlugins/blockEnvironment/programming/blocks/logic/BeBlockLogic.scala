package interactionPlugins.blockEnvironment.programming.blocks.logic

import contentmanagement.model.language.ProgrammingLanguage
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.BeProgram.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.connection.{BeConnection, BeConnectionRole}
import interactionPlugins.blockEnvironment.programming.rendering.BeBlockDisplayManager

trait BeBlockLogic extends BeBlock{

  def evaluatesTo: BeDataType

  def toCode(language: ProgrammingLanguage, context: BeLogicContext[String]): String

}

