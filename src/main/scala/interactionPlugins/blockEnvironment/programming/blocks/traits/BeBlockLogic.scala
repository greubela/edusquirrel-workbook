package interactionPlugins.blockEnvironment.programming.blocks.traits

import contentmanagement.model.language.ProgrammingLanguage
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.traits.*

trait BeBlockLogic  {

   def toCode(language: ProgrammingLanguage, context: BeLogicContext[String]): String = ""

}

