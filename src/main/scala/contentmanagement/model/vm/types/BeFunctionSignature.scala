package contentmanagement.model.vm.types

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.*
import contentmanagement.model.vm.expressions.BeExpression
import contentmanagement.model.vm.expressions.defining.BeDefineVariable
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.BeInfo.*

case class BeFunctionSignature(name: LanguageMap[HumanLanguage], parameter: List[BeDefineVariable], returnValue: Option[BeDefineVariable])


