package it.evadid.vm.io.stringPrinter.lisp

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.NamingStyle.SnakeCase
import it.evadid.vm.types.{BeDataValueLiteral, BeDataValueUnit, BeUseValueReference}

case class BeExpressionToLispString(language: HumanLanguage, skipUnparsable: Boolean) {
  def forExpression(expression: BeExpression): String = render(expression, 0)

  private def name(entityName: it.evadid.vm.naming.BeEntityName): String = entityName.getNameIn(language, SnakeCase)

  private def indent(level: Int): String = "    " * level

  private def render(expression: BeExpression, level: Int): String = expression match {
    case BeSequence(body, _) =>
      if (body.isEmpty) "(progn)"
      else s"(progn\n${body.map(item => indent(level + 1) + render(item, level + 1)).mkString("\n")}\n${indent(level)})"
    case BeAssignVariable(target, value) => s"(setf ${name(target.name)} ${render(value, level)})"
    case BeIfElse(condition, thenBody, elseBody) =>
      val conditionText = condition.body.map(render(_, level)).mkString("(progn    ", " ", ")")
      def branch(body: BeSequence): String =
        s"(progn\n${indent(level + 2)}(progn\n${body.body.map(item => indent(level + 3) + render(item, level + 3)).mkString("\n")}\n${indent(level + 2)})\n${indent(level + 1)})"
      s"(if $conditionText\n${indent(level + 1)}${branch(thenBody)}\n${indent(level + 1)}${branch(elseBody)}\n${indent(level)})"
    case BeWhile(condition, body) => s"(loop while ${render(condition, level)} do ${render(body, level)})"
    case BeRepeatNr(amount, body) => s"(dotimes (_ $amount) ${render(body, level)})"
    case BeDefineVariable(entityName, _, initValue) => s"(defparameter ${name(entityName)} ${initValue.map(render(_, level)).getOrElse("nil")})"
    case function: BeDefineFunction => s"(defun ${name(function.functionTypeInfo.displayName)} () ${render(function.body, level)})"
    case BeDefineClass(entityName, _, _) => s"(defclass ${name(entityName)} () ())"
    case BeFunctionCall(function, parameters) => s"(${name(function.functionTypeInfo.displayName)} ${parameters.values.map(render(_, level)).mkString(" ")})"
    case BeUseValue(BeDataValueLiteral(value), _) => value
    case BeUseValue(BeUseValueReference(variable), _) => name(variable.name)
    case BeUseValue(BeDataValueUnit(), _) => "nil"
    case BeReturn(value) => s"(return ${value.map(render(_, level)).getOrElse("nil")})"
    case BeStartProgram(body) => body.map(render(_, level)).getOrElse("(progn)")
    case BeExpressionUnsupported(source) => source
    case BeExpressionUnparsable(source, _) => if (skipUnparsable) "nil" else source
    case BeSingleLineComment(comment) => s"; $comment"
    case other => s"; ${other.getClass.getSimpleName}"
  }
}
