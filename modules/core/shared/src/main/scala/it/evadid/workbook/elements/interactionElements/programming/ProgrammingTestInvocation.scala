package it.evadid.workbook.elements.interactionElements.programming

import scala.util.Try
import upickle.default.{ReadWriter, readwriter}

case class ProgrammingTestInvocation(target: Option[String], arguments: Vector[ProgrammingTestInvocation.Scalar]) derives ReadWriter {
  require(target != null && target.forall(ProgrammingTestInvocation.validTarget), "Invalid invocation target.")
  require(arguments != null && arguments.size <= 8 && arguments.forall(ProgrammingTestInvocation.validScalar),
    "Invalid invocation arguments.")
  require(target.nonEmpty || arguments.isEmpty, "A whole-program invocation has no arguments.")
}

object ProgrammingTestInvocation {
  enum Scalar {
    case Bool(value: Boolean)
    case Integer(value: String)
    case Float64(bits: String)
  }

  object Scalar {
    given ReadWriter[Scalar] = readwriter[ujson.Value].bimap(
      value => {
        require(validScalar(value), "Invalid invocation scalar.")
        value match {
          case Scalar.Bool(flag) => ujson.Obj("kind" -> "bool", "value" -> flag)
          case Scalar.Integer(text) => ujson.Obj("kind" -> "int", "value" -> text)
          case Scalar.Float64(bits) => ujson.Obj("kind" -> "double", "value" -> bits)
        }
      },
      value => scalar(value).getOrElse(throw IllegalArgumentException("Invalid invocation scalar.")))
  }

  private def validTarget(value: String): Boolean =
    value != null && value.nonEmpty && value.length <= 256 && value == value.trim

  private def validScalar(value: Scalar): Boolean = value match {
    case Scalar.Bool(_) => true
    case Scalar.Integer(text) => text != null && text.length <= 79 &&
      text.matches("0|-?[1-9][0-9]*") && BigInt(text).abs.bitLength <= 256
    case Scalar.Float64(bits) => bits != null && bits.matches("[0-9a-f]{16}") &&
      java.lang.Double.longBitsToDouble(java.lang.Long.parseUnsignedLong(bits, 16)).isFinite
    case null => false
  }

  private def scalar(value: ujson.Value): Option[Scalar] = for {
    fields <- Option(value).flatMap(_.objOpt).filter(fields => fields != null && fields.keySet.toSet == Set("kind", "value"))
    parsed <- (fields("kind"), fields("value")) match {
      case (ujson.Str("bool"), ujson.Bool(value)) => Some(Scalar.Bool(value))
      case (ujson.Str("int"), ujson.Str(value)) => Some(Scalar.Integer(value))
      case (ujson.Str("double"), ujson.Str(value)) => Some(Scalar.Float64(value))
      case _ => None
    }
    valid <- Option.when(validScalar(parsed))(parsed)
  } yield valid

  private[evadid] def fromReport(value: ujson.Value): Option[ProgrammingTestInvocation] = Try {
    for {
      fields <- Option(value).flatMap(_.objOpt).filter(fields => fields != null && fields.keySet.toSet == Set("target", "arguments"))
      target <- fields("target") match {
        case ujson.Null => Some(None)
        case ujson.Str(name) if validTarget(name) => Some(Some(name))
        case _ => None
      }
      values <- Option(fields("arguments")).flatMap(_.arrOpt).filter(values => values != null && values.size <= 8)
      arguments <- values.foldLeft(Option(Vector.empty[Scalar])) { (previous, value) =>
        for { accumulated <- previous; next <- scalar(value) } yield accumulated :+ next
      }
      invocation <- Try(ProgrammingTestInvocation(target, arguments)).toOption
      bounded <- Option.when(value.render().length <= 4096)(invocation)
    } yield bounded
  }.toOption.flatten
}
