package contentmanagement.model.vm.types

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.datastructures.tree.{TreePosition, TreeStructureContext}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}

case class BeInfo(message: LanguageMap[HumanLanguage], infoType: BeInfo.InfoType) {

}

object BeInfo {

  sealed trait InfoType

  enum SyntaxError extends InfoType {
    case UnparsableBlock, MissingValue, InvalidLiteralValue, TypeMismatch, StructureMismatch
  }

  enum RuntimeError extends InfoType {
    case DivideByZero, InvalidReference
  }

  enum WarningType extends InfoType{
    case ImplicitTypeCast
  }

  def typeMismatchInfo(contextStrBegin: String, expectedType: BeDataType, actualType: BeDataType): Option[BeInfo] = {
    expectedType.canTakeValuesFrom(actualType) match{
      case AssigningNotPossible() => Some(
        BeInfo(LanguageMap.universalMap(contextStrBegin.trim + " must be able to evaluate to " + expectedType + "!"), BeInfo.SyntaxError.TypeMismatch)
      )
      case AssigningPossibleWithImplicitCast(resultingType) => Some(
        BeInfo(LanguageMap.universalMap("Implicit Cast: " + actualType + " -> " + expectedType), BeInfo.WarningType.ImplicitTypeCast)
      )
      case AssigningPossibleWithSameType(resultingType) => None
    }
  }

        
    
  }