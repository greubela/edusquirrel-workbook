package it.evadid.vm.editing

import it.evadid.vm.BeProgram
import it.evadid.vm.code.controlStructures.BeRepeatNr
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.types.BeChildRole.{ExpressionInSequence, FunctionParameter}
import it.evadid.vm.types.BeDataType.{BeDataTypeAtomic, BeUnionAllowedTypes}
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}

/** Typed entry of literal values, e.g. a function parameter edited in the block editor. */
object BeLiteralInput {

  /** The literal kinds a learner can type; clients localize their names. */
  enum Kind {
    case Number, Integer, Boolean, Text, Date, AnyValue
  }

  final case class Rejected(expected: Kind)

  /** The literal at `path`, if it can be edited as text. */
  def literalAt(program: BeProgram, path: BeNodePath): Option[BeUseValue] =
    BeProgramEditing.find(program, path).collect { case value @ BeUseValue(_: BeDataValueLiteral, _) => value }

  /** The type the literal at `path` must have: its parameter's type, a repeat count, or its own context. */
  def expectedType(program: BeProgram, path: BeNodePath): BeDataType =
    (path.parent.flatMap(BeProgramEditing.find(program, _)), path.lastRole) match {
      case (Some(call: BeFunctionCall), Some(FunctionParameter(index))) =>
        call.funcDef.inputs.lift(index).map(_.variableType).getOrElse(BeDataType.AnyType)
      case (Some(_: BeRepeatNr), Some(ExpressionInSequence(0))) => BeDataType.Int
      case _ => literalAt(program, path).flatMap(_.contextIfKnown).map(_.variableType).getOrElse(BeDataType.AnyType)
    }

  def kind(dataType: BeDataType): Kind = dataType match {
    case BeDataType.Int => Kind.Integer
    case BeDataType.Numeric => Kind.Number
    case BeDataType.Boolean => Kind.Boolean
    case BeDataType.String => Kind.Text
    case BeDataType.Date => Kind.Date
    case _ => Kind.AnyValue
  }

  /** Accepts `text` when it is a valid literal of `expected`; booleans are normalized to lower case. */
  def parse(expected: BeDataType, text: String): Either[Rejected, BeDataValueLiteral] = {
    val trimmed = text.trim
    val valid = expected match {
      case BeDataType.String => true
      case atomic: BeDataTypeAtomic => trimmed.nonEmpty && atomic.isValidLiteral(trimmed)
      case BeUnionAllowedTypes(types) => types.exists(parse(_, text).isRight)
      case _ => trimmed.nonEmpty && BeDataValueLiteral(trimmed).currentType != BeDataType.Error
    }
    val literal = expected match {
      case BeDataType.String => text
      case BeDataType.Boolean => trimmed.toLowerCase
      case _ => trimmed
    }
    Either.cond(valid, BeDataValueLiteral(literal), Rejected(kind(expected)))
  }

  /** Replaces the literal at `path` with the parsed `text`, keeping its variable context. */
  def update(program: BeProgram, path: BeNodePath, text: String): Either[Rejected, BeProgram] = {
    val expected = expectedType(program, path)
    for {
      current <- literalAt(program, path).toRight(Rejected(kind(expected)))
      literal <- parse(expected, text)
      edited <- BeProgramEditing.replace(program, path, current.copy(value = literal)).left.map(_ => Rejected(kind(expected)))
    } yield edited
  }
}
