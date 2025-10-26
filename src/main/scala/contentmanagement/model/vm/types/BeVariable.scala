package contentmanagement.model.vm.types

import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.model.vm.*

/*

sealed trait BeVariable {

  def toDisplayName: LanguageMap[HumanLanguage] = this match {
    case BeVariableNamed(name,  canTakeTypes, unitForValue) => name
    case _ => LanguageMap.universalMap("[[[anonymous variable]]]")
  }
 
  def canTakeTypes: Set[BeDataType]

  def valueUnit: Option[LanguageMap[HumanLanguage]]

  def valueAsLiteral(value: String): BeValueLiteral = BeValueLiteral(value, this)

  def valueAsReference(): BeValueReference = BeValueReference(this)

  def valueAsMissing: BeValueMissing = BeValueMissing(this)

}

case class BeVariableNamed(name: LanguageMap[HumanLanguage], canTakeTypes: Set[BeDataType], valueUnit: Option[LanguageMap[HumanLanguage]]) extends BeVariable {

}

case class BeVariableNamedAnonymous(canTakeTypes: Set[BeDataType], valueUnit: Option[LanguageMap[HumanLanguage]]) extends BeVariable {

}

object BeVariable {


}*/