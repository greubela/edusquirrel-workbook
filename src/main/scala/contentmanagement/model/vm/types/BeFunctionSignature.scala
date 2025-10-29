package contentmanagement.model.vm.types

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.*
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.{BeDefineClass, BeDefineVariable}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.BeInfo.*

case class BeFunctionSignature(methodOnObject: Option[BeDefineClass], name: LanguageMap[HumanLanguage], parameter: List[BeDefineVariable], returnValue: Option[BeDefineVariable])


object BeFunctionSignature {
  
  def apply(name: LanguageMap[HumanLanguage], parameter: List[BeDefineVariable], returnValue: Option[BeDefineVariable]): BeFunctionSignature = BeFunctionSignature(None, name, parameter, returnValue)
}