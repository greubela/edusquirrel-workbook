package contentmanagement.model.vm.code

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock

trait BeDefineStructure extends BeExpression{

  def definedClasses: List[BeDefineClass] = List()

  def definedFunctions: List[BeDefineFunction] = List()

  def definedVariables: List[BeDefineVariable] = List()

  def allDefinedStructures: List[BeDefineStructure] = definedClasses ++ definedFunctions ++ definedVariables
  

}
