package it.evadid.vm.types

import upickle.default.ReadWriter

import it.evadid.core.datastructures.language.LanguageMap

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*

case class BeInfo(message: LanguageMap[HumanLanguage], infoType: BeInfo.InfoType) derives ReadWriter {

}

object BeInfo {

  sealed trait InfoType

  object InfoType {
    given ReadWriter[InfoType] = upickle.default.readwriter[(String, String)].bimap(
      {
        case value: SyntaxError => "syntax" -> value.toString
        case value: RuntimeError => "runtime" -> value.toString
        case value: WarningType => "warning" -> value.toString
      },
      {
        case ("syntax", value) => SyntaxError.valueOf(value)
        case ("runtime", value) => RuntimeError.valueOf(value)
        case ("warning", value) => WarningType.valueOf(value)
        case (kind, _) => throw new IllegalArgumentException(s"Unknown diagnostic category: $kind")
      }
    )
  }

  enum SyntaxError extends InfoType {
    case UnparsableBlock, UnsupportedBlock, MissingValue, InvalidLiteralValue, TypeMismatch, StructureMismatch
  }

  enum RuntimeError extends InfoType {
    case DivideByZero, InvalidReference
  }

  enum WarningType extends InfoType {
    case ImplicitTypeCast
  }

  object SyntaxError {
    given ReadWriter[SyntaxError] = upickle.default.readwriter[String].bimap(_.toString, SyntaxError.valueOf)
  }

  object RuntimeError {
    given ReadWriter[RuntimeError] = upickle.default.readwriter[String].bimap(_.toString, RuntimeError.valueOf)
  }

  object WarningType {
    given ReadWriter[WarningType] = upickle.default.readwriter[String].bimap(_.toString, WarningType.valueOf)
  }

  def typeMismatchInfo(contextStrBegin: String, expectedType: BeDataType, actualType: BeDataType): Option[BeInfo] = {
    expectedType.canTakeValuesFrom(actualType) match {
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