package it.evadid.vm.editing

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeRepeatNr
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.BeSourcePlaceholder
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.simulation.BeProgramInterpreter
import it.evadid.vm.types.BeChildRole.{ExpressionInSequence, FunctionParameter, ValueInAssignment}
import it.evadid.vm.types.BeDataType.{BeDataTypeAtomic, BeUnionAllowedTypes}
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference}

/** Values typed by a learner for a parameter, a repeat count or an assigned value.
  *
  * The text is parsed as a Python expression into a [[BeExpression]]; calls refer to the program's own
  * function definitions where they exist, and operators on literals are computed: `3+5` becomes `8`,
  * `add(3+5, 3)` becomes the call `add(8, 3)`. The result must fit the value's expected type.
  */
object BeValueInput {

  /** The kinds of value a position expects; clients localize their names. */
  enum Kind {
    case Number, Integer, Boolean, Text, Date, AnyValue
  }

  final case class Rejected(expected: Kind)

  /** Whether the node at `path` is a value that can be replaced by a typed expression. */
  def isValuePosition(program: BeProgram, path: BeNodePath): Boolean =
    (path.parent.flatMap(BeProgramEditing.find(program, _)), path.lastRole) match {
      case (Some(_: BeFunctionCall), Some(FunctionParameter(_))) => true
      case (Some(_: BeRepeatNr), Some(ExpressionInSequence(0))) => true
      case (Some(_: BeAssignVariable), Some(ValueInAssignment)) => true
      case _ => false
    }

  /** The value's text as the learner edits it: a literal's own text, otherwise the expression as Python. */
  def textAt(program: BeProgram, path: BeNodePath): Option[String] =
    BeProgramEditing.find(program, path).map {
      case BeUseValue(BeDataValueLiteral(text), _) => text
      case other => other.structureInfo.toStringInLanguage(Python, English)
    }

  /** The parameter's type, a whole-number repeat count, the assigned variable's or the value's own type. */
  def expectedType(program: BeProgram, path: BeNodePath): BeDataType =
    (path.parent.flatMap(BeProgramEditing.find(program, _)), path.lastRole) match {
      case (Some(call: BeFunctionCall), Some(FunctionParameter(index))) =>
        call.funcDef.inputs.lift(index).map(_.variableType).getOrElse(BeDataType.AnyType)
      case (Some(_: BeRepeatNr), Some(ExpressionInSequence(0))) => BeDataType.Int
      case (Some(assignment: BeAssignVariable), Some(ValueInAssignment)) => assignment.target.variableType
      case _ => BeProgramEditing.find(program, path).collect { case BeUseValue(_, Some(context)) => context.variableType }
        .getOrElse(BeDataType.AnyType)
    }

  def kind(dataType: BeDataType): Kind = dataType match {
    case BeDataType.Int => Kind.Integer
    case BeDataType.Numeric => Kind.Number
    case BeDataType.Boolean => Kind.Boolean
    case BeDataType.String => Kind.Text
    case BeDataType.Date => Kind.Date
    case _ => Kind.AnyValue
  }

  /** Accepts `text` as a literal of a typed position (so `true` and `2024-05-01` keep their meaning), else as
    * an expression that is parsed, bound to the program's functions and folded; text and untyped positions
    * finally take the plain text.
    */
  def parse(program: BeProgram, expected: BeDataType, text: String): Either[Rejected, BeExpression] = {
    val literal = expected match {
      case BeDataType.Numeric | BeDataType.Int | BeDataType.Boolean | BeDataType.Date => literalValue(expected, text)
      case _ => None
    }
    lazy val parsed = scala.util.Try(BeBlockLibrary.statementFromPython(text)).toOption
      .filterNot(_.subtree.exists(_.isInstanceOf[BeSourcePlaceholder]))
      .filter(_.subtree.forall(knownVariable(program)))
      .map(statement => BeProgramInterpreter.foldConstants(bound(statement, definitions(program))))
    lazy val expression = parsed.flatMap {
      case BeUseValue(BeDataValueLiteral(value), _) => literalValue(expected, value)
      case other => Option.when(fits(expected, other))(other)
    }
    // Text and untyped values also take plain words, which are not Python expressions.
    lazy val words = Option.when(literal.isEmpty)(literalValue(expected, text)).flatten
    literal.orElse(expression).orElse(words).toRight(Rejected(kind(expected)))
  }

  /** Variables used by an input must exist in the program. */
  private def knownVariable(program: BeProgram)(expression: BeExpression): Boolean = expression match {
    case BeUseValue(BeUseValueReference(variable), _) =>
      program.fullProgram.subtree.exists {
        case defined: BeDefineVariable => defined.name == variable.name
        case BeAssignVariable(target, _) => target.name == variable.name
        case function: BeDefineFunction => function.inputs.exists(_.name == variable.name)
        case _ => false
      }
    case _ => true
  }

  /** Replaces the value at `path` with the parsed `text`; literals keep their variable context. */
  def update(program: BeProgram, path: BeNodePath, text: String): Either[Rejected, BeProgram] = {
    val expected = expectedType(program, path)
    for {
      current <- BeProgramEditing.find(program, path).toRight(Rejected(kind(expected)))
      value <- parse(program, expected, text)
      replacement = (current, value) match {
        case (BeUseValue(_, context), BeUseValue(literal: BeDataValueLiteral, None)) => BeUseValue(literal, context)
        case _ => value
      }
      edited <- BeProgramEditing.replace(program, path, replacement).left.map(_ => Rejected(kind(expected)))
    } yield edited
  }

  private def definitions(program: BeProgram): Seq[BeDefineFunction] =
    program.fullProgram.subtree.collect { case definition: BeDefineFunction => definition }

  /** Calls to functions the program defines use those definitions, matched by name and number of inputs. */
  private def bound(expression: BeExpression, defined: Seq[BeDefineFunction]): BeExpression = expression match {
    case call: BeFunctionCall =>
      val arguments = call.arguments.map(bound(_, defined))
      val name = call.funcDef.functionTypeInfo.displayName.universalInterpretation()
      defined.find(definition => definition.functionTypeInfo.displayName.universalInterpretation() == name &&
          definition.inputs.size == arguments.size && !call.isOperator)
        .map(definition => BeFunctionCall.fromDefinition(definition, definition.inputs.zip(arguments).toMap))
        .getOrElse(call.copy(parameterValueMap = call.funcDef.inputs.zip(arguments).toMap))
    case other => other
  }

  /** A literal of the expected type; booleans in lower case for typed positions, as Python spells them otherwise. */
  private def literalValue(expected: BeDataType, text: String): Option[BeExpression] = {
    val trimmed = text.trim
    val valid = expected match {
      case BeDataType.String => true
      case atomic: BeDataTypeAtomic => trimmed.nonEmpty && atomic.isValidLiteral(trimmed)
      case BeUnionAllowedTypes(types) => types.exists(literalValue(_, text).nonEmpty)
      case _ => trimmed.nonEmpty && BeDataValueLiteral(trimmed).currentType != BeDataType.Error
    }
    val normalized = expected match {
      case BeDataType.Boolean => trimmed.toLowerCase
      case BeDataType.String => text
      case _ => trimmed
    }
    Option.when(valid)(BeUseValue(BeDataValueLiteral(normalized), None))
  }

  /** Expressions of unknown type (calls to functions without declared results) are accepted. */
  private def fits(expected: BeDataType, expression: BeExpression): Boolean = expression match {
    case call: BeFunctionCall if call.funcDef.outputs.isEmpty && !call.isOperator => true
    case _ => expected match {
      case BeDataType.AnyType => true
      case _ => expected.canTakeValuesFrom(expression.staticInformationExpression.staticType).possibleWithoutSyntaxErrors
    }
  }
}
