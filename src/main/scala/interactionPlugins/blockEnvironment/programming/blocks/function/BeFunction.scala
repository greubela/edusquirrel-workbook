package interactionPlugins.blockEnvironment.programming.blocks.function

import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.traits.BeBlockLogic
import interactionPlugins.blockEnvironment.programming.connection.{FunctionParameter, FunctionReturnValue}

import interactionPlugins.blockEnvironment.programming.blocks.*

case class BeFunction(
                       name: LanguageMap[HumanLanguage],
                       parameter: List[FunctionParameter],
                       returnValues: List[FunctionReturnValue]) {

  def toDefineBlockWithBody(): BeBlockDefineFunctionWithBody = BeBlockDefineFunctionWithBody(this)

  def toDefineBlock(): BeBlockDefineFunctionExternalFunctionality = BeBlockDefineFunctionExternalFunctionality(this)

  def toCallBlock(): BeBlockCallUnitFunction = BeBlockCallUnitFunction(this)

}
