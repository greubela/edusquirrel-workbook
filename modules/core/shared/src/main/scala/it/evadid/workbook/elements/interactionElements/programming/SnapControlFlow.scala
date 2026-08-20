package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference}

/**
 * Shared helpers for Snap control-flow blocks ↔ BeProgram AST mapping.
 */
object SnapControlFlow {

  val ControlSelectors: Set[String] = Set("doRepeat", "doIf", "doIfElse", "doUntil")

  val VariableSelectors: Set[String] = Set("doSetVar", "doChangeVar", "reportGetVar")

  val ConditionSelectors: Set[String] = Set(
    "reportTrue",
    "reportFalse",
    "reportBoolean",
    "reportNot",
    "reportVariadicLessThan",
    "reportVariadicGreaterThan",
    "reportVariadicEquals",
    "reportVariadicAnd",
    "reportVariadicOr"
  )

  final class VariableInterner {
    private val byName = scala.collection.mutable.LinkedHashMap.empty[String, BeDefineVariable]

    def intern(rawName: String): BeDefineVariable = {
      val name = rawName.trim
      if name.isEmpty then intern("x")
      else byName.getOrElseUpdate(name, BeDefineVariable(BeEntityName.fromLiteral(name), BeDataType.AnyType))
    }

    def internAll(names: Iterable[String]): Unit =
      names.foreach { name =>
        if name.trim.nonEmpty then intern(name)
      }

    def names: List[String] = byName.keys.toList
  }

  object VariableInterner {
    def fromNames(names: Iterable[String]): VariableInterner = {
      val interner = new VariableInterner
      interner.internAll(names)
      interner
    }
  }

  private val SupportedOperators: Set[String] = Set("<", ">", "==", "and", "or", "not")

  private val OperatorToSnapReporter: Map[String, String] = Map(
    "<" -> "reportVariadicLessThan",
    ">" -> "reportVariadicGreaterThan",
    "==" -> "reportVariadicEquals",
    "and" -> "reportVariadicAnd",
    "or" -> "reportVariadicOr",
    "not" -> "reportNot"
  )

  private val SnapReporterToOperator: Map[String, String] =
    OperatorToSnapReporter.map { case (op, snap) => snap -> op } ++ Map(
      "reportTrue" -> "true",
      "reportFalse" -> "false",
      "reportBoolean" -> "boolean"
    )

  def topLevelStatements(expression: BeExpression): List[BeExpression] =
    expression match
      case BeStartProgram(Some(sequence)) => sequence.body.toList
      case BeStartProgram(None) => Nil
      case seq: BeSequence => seq.body.toList
      case other => List(other)

  def hasSupportedStatements(expression: BeExpression): Boolean =
    topLevelStatements(expression).exists(containsSupportedStatement)

  def containsSupportedStatement(expression: BeExpression): Boolean =
    expression match
      case call: BeFunctionCall =>
        SnapTurtlePythonBridge.AllowedPythonNames.contains(SnapTurtlePythonBridge.pythonName(call)) &&
          orderedArgs(call).forall(isSupportedValue)
      case assign: BeAssignVariable =>
        isSupportedAssignment(assign)
      case ifElse: BeIfElse =>
        isSupportedConditionSequence(ifElse.condition) &&
          bodyHasSupported(ifElse.thenBody) &&
          bodyHasSupported(ifElse.elseBody)
      case whileExpr: BeWhile =>
        isSupportedConditionSequence(whileExpr.condition) &&
          bodyHasSupported(whileExpr.body)
      case repeat: BeRepeatNr =>
        repeat.amount >= 0 && bodyHasSupported(repeat.body)
      case _ => false

  def validateStatements(expressions: List[BeExpression]): Either[String, List[BeExpression]] =
    if expressions.isEmpty then Right(Nil)
    else
      val problems = expressions.flatMap(describeUnsupportedStatement)
      if problems.nonEmpty then Left("Unsupported for blocks: " + problems.take(3).mkString(" | "))
      else Right(expressions)

  def describeUnsupportedStatement(expression: BeExpression): Option[String] =
    expression match
      case call: BeFunctionCall =>
        val name = SnapTurtlePythonBridge.pythonName(call)
        if SnapTurtlePythonBridge.AllowedPythonNames.contains(name) &&
            orderedArgs(call).forall(isSupportedValue) then None
        else Some(s"$name(...)")
      case assign: BeAssignVariable =>
        if isSupportedAssignment(assign) then None
        else Some(s"${variableName(assign.target)} = ...")
      case ifElse: BeIfElse =>
        conditionProblems(ifElse.condition)
          .orElse(bodyProblems(ifElse.thenBody))
          .orElse(bodyProblems(ifElse.elseBody))
      case whileExpr: BeWhile =>
        conditionProblems(whileExpr.condition).orElse(bodyProblems(whileExpr.body))
      case repeat: BeRepeatNr =>
        if repeat.amount < 0 then Some("negative repeat count")
        else bodyProblems(repeat.body)
      case _: BeSingleLineComment =>
        Some("comments are not mapped to Snap blocks")
      case u: BeExpressionUnsupported =>
        Some(preview(u.originalSource))
      case u: BeExpressionUnparsable =>
        Some(preview(u.originalSource))
      case other =>
        Some(preview(other.getClass.getSimpleName.replace("Be", "")))

  def invertCondition(expression: BeExpression): BeExpression =
    expression match
      case BeUseValue(BeDataValueLiteral("True"), _) =>
        BeUseValue(BeDataValueLiteral("False"), None)
      case BeUseValue(BeDataValueLiteral("False"), _) =>
        BeUseValue(BeDataValueLiteral("True"), None)
      case call: BeFunctionCall if isOperatorCall(call, "not") =>
        call.parameterValueMap.values.headOption.getOrElse(BeExpression.pass)
      case other =>
        operatorCall("not", List(other))

  def isSnapConditionReporter(selector: String): Boolean =
    ConditionSelectors.contains(selector)

  def conditionFromSnapReporter(call: BeFunctionCall): BeExpression = {
    val selector = functionSelector(call)
    selector match
      case "reportTrue" =>
        BeUseValue(BeDataValueLiteral("True"), None)
      case "reportFalse" =>
        BeUseValue(BeDataValueLiteral("False"), None)
      case "reportBoolean" =>
        val value = orderedArgs(call).headOption.collect {
          case BeUseValue(BeDataValueLiteral(v), _) => v
        }.getOrElse("True")
        BeUseValue(BeDataValueLiteral(if value.equalsIgnoreCase("false") then "False" else "True"), None)
      case "reportNot" =>
        val inner = orderedArgs(call).headOption.getOrElse(BeExpression.pass)
        val parsedInner = inner match
          case innerCall: BeFunctionCall => conditionFromSnapReporter(innerCall)
          case other => other
        operatorCall("not", List(parsedInner))
      case snap if SnapReporterToOperator.contains(snap) && snap.startsWith("reportVariadic") =>
        val op = SnapReporterToOperator(snap)
        val args = orderedArgs(call)
        operatorCall(op, args)
      case _ =>
        call
  }

  def isSupportedCondition(expression: BeExpression): Boolean =
    expression match
      case BeUseValue(BeUseValueReference(_), _) => true
      case BeUseValue(BeDataValueLiteral(value), _) =>
        value == "True" || value == "False" || value.forall(c => c.isDigit || c == '.' || c == '-')
      case call: BeFunctionCall =>
        call.funcDef.functionTypeInfo.funcType match
          case BeDefineFunction.Operator(_) =>
            val op = operatorSymbol(call)
            SupportedOperators.contains(op) &&
              orderedArgs(call).forall(isSupportedCondition)
          case _ =>
            false
      case _ => false

  def isSupportedValue(expression: BeExpression): Boolean =
    expression match
      case BeUseValue(BeDataValueLiteral(_), _) => true
      case BeUseValue(BeUseValueReference(_), _) => true
      case _ => false

  def isSupportedAssignment(assign: BeAssignVariable): Boolean =
    variableName(assign.target).nonEmpty &&
      (isSupportedValue(assign.value) || changeVarAmount(assign).isDefined)

  def variableName(variable: BeDefineVariable): String =
    variable.name.universalInterpretation()

  def useVariable(variable: BeDefineVariable): BeUseValue =
    BeUseValue(BeUseValueReference(variable), Some(variable))

  def assignVariable(target: BeDefineVariable, value: BeExpression): BeAssignVariable =
    BeAssignVariable(target, value)

  def changeVariable(target: BeDefineVariable, amount: BeExpression): BeAssignVariable =
    BeAssignVariable(target, operatorCall("+", List(useVariable(target), amount)))

  def changeVarAmount(assign: BeAssignVariable): Option[BeExpression] =
    assign.value match
      case call: BeFunctionCall if isOperatorCall(call, "+") =>
        orderedArgs(call) match
          case List(BeUseValue(BeUseValueReference(referenced), _), amount)
            if variableName(referenced) == variableName(assign.target) && isSupportedValue(amount) =>
            Some(amount)
          case _ => None
      case _ => None

  def collectVariableNames(expression: BeExpression): List[String] = {
    val names = scala.collection.mutable.LinkedHashSet.empty[String]
    def walk(node: BeExpression): Unit = node match
      case BeAssignVariable(target, value) =>
        names += variableName(target)
        walk(value)
      case BeUseValue(BeUseValueReference(variable), _) =>
        names += variableName(variable)
      case call: BeFunctionCall =>
        orderedArgs(call).foreach(walk)
      case ifElse: BeIfElse =>
        ifElse.condition.body.foreach(walk)
        ifElse.thenBody.body.foreach(walk)
        ifElse.elseBody.body.foreach(walk)
      case whileExpr: BeWhile =>
        whileExpr.condition.body.foreach(walk)
        whileExpr.body.body.foreach(walk)
      case repeat: BeRepeatNr =>
        repeat.body.body.foreach(walk)
      case seq: BeSequence =>
        seq.body.foreach(walk)
      case BeStartProgram(Some(seq)) =>
        seq.body.foreach(walk)
      case _ => ()
    walk(expression)
    names.toList.filter(_.nonEmpty)
  }

  def functionSelector(call: BeFunctionCall): String =
    call.funcDef.functionTypeInfo.displayName.getNameIn(English, NamingStyle.CamelCase)

  def operatorSymbol(call: BeFunctionCall): String =
    call.funcDef.functionTypeInfo.displayName.getNameIn(English, NamingStyle.SnakeCase).trim

  def operatorCall(symbol: String, args: List[BeExpression]): BeFunctionCall = {
    val params = args.indices.toList.map { idx =>
      BeDefineVariable(BeEntityName.fromCodeString(s"arg$idx"), BeDataType.AnyType)
    }
    val define = BeDefineFunction(
      params,
      None,
      BeExpression.pass,
      BeDefineFunction.operatorInfo(symbol, if args.size <= 1 then 0 else 1)
    )
    BeFunctionCall(define, params.zip(args).toMap)
  }

  def orderedArgs(call: BeFunctionCall): List[BeExpression] =
    call.funcDef.inputs.flatMap(variable => call.parameterValueMap.get(variable))

  def literalInt(value: String): Option[Int] =
    scala.util.Try(value.trim.toDouble.round.toInt).toOption.filter(_ >= 0)

  private def bodyHasSupported(sequence: BeSequence): Boolean =
    sequence.body.forall(containsSupportedStatement)

  private def bodyProblems(sequence: BeSequence): Option[String] =
    sequence.body.flatMap(describeUnsupportedStatement).headOption

  private def isSupportedConditionSequence(sequence: BeSequence): Boolean =
    sequence.body.forall(isSupportedCondition)

  private def conditionProblems(sequence: BeSequence): Option[String] =
    sequence.body.find(!isSupportedCondition(_)).map(expr => preview(expr.getClass.getSimpleName))

  private def isOperatorCall(call: BeFunctionCall, symbol: String): Boolean =
    call.funcDef.functionTypeInfo.funcType match
      case BeDefineFunction.Operator(_) => operatorSymbol(call) == symbol
      case _ => false

  private def preview(text: String): String =
    val trimmed = text.trim
    if trimmed.length <= 40 then trimmed
    else trimmed.take(37) + "..."
}
