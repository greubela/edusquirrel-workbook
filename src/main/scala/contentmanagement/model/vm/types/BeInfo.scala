package contentmanagement.model.vm.types

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.datastructures.tree.{TreePosition, TreeStructureContext}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}

case class BeInfo(message: LanguageMap[HumanLanguage], infoType: BeInfo.InfoType) {

}

object BeInfo {

  sealed trait InfoType

  sealed trait ErrorType extends InfoType {
  }

  enum SyntaxError extends ErrorType {
    case UnparsableBlock, MissingValue, InvalidLiteralValue, TypeMismatch, StructureMismatch
  }

  enum RuntimeError extends ErrorType {
    case DivideByZero, InvalidReference
  }

  sealed trait WarningType extends ErrorType


}