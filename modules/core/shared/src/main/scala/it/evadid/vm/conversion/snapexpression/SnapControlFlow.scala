package it.evadid.vm.conversion.snapexpression

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog

import upickle.default.ReadWriter

import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.*
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog.SnapInputKind

/**
 * Shared helpers for Snap control-flow blocks ↔ BeProgram AST mapping.
 */
object SnapControlFlow {

  // The supported Snap language blocks. Lists are in palette order; the sets classify selectors.

  val ControlSelectorOrder: Seq[String] = List("doRepeat", "doIf", "doIfElse", "doUntil", "doFor")
  val ControlSelectors: Set[String] = ControlSelectorOrder.toSet

  /** Variable commands in palette order; reading a variable is the `reportGetVar` reporter. */
  val VariableCommandOrder: Seq[String] = List("doSetVar", "doChangeVar")
  val VariableSelectors: Set[String] = VariableCommandOrder.toSet + "reportGetVar"

  val ArithmeticSelectorOrder: Seq[String] =
    List("reportVariadicSum", "reportDifference", "reportVariadicProduct", "reportQuotient", "reportModulus")
  val ArithmeticSelectors: Set[String] = ArithmeticSelectorOrder.toSet

  private val ComparisonAndLogicOrder: Seq[String] = List(
    "reportVariadicLessThan", "reportVariadicGreaterThan", "reportVariadicEquals", "reportVariadicLessThanOrEquals",
    "reportVariadicGreaterThanOrEquals", "reportVariadicNotEquals", "reportVariadicAnd", "reportVariadicOr", "reportNot")
  val ConditionSelectors: Set[String] = (ComparisonAndLogicOrder ++ Seq("reportTrue", "reportFalse", "reportBoolean")).toSet

  /** The operator blocks the palette offers; `true`/`false` come from the boolean block's menu. */
  val OperatorPaletteOrder: Seq[String] = ("reportBoolean" +: ArithmeticSelectorOrder) ++ ComparisonAndLogicOrder

  final class VariableInterner {
    private val byName = scala.collection.mutable.LinkedHashMap.empty[String, BeDefineVariable]

    def intern(rawName: String): BeDefineVariable = {
      val name = rawName.trim
      if name.isEmpty then intern("x")
      // Snap names may contain spaces (`my size`); they read as code names, as saved variables do.
      else byName.getOrElseUpdate(name, BeDefineVariable(BeEntityName.fromCodeString(name), BeDataType.AnyType))
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


  enum SnapReporterKind derives ReadWriter:
    case Variadic, Binary, Unary, Literal

  final case class SnapReporter(selector: String, kind: SnapReporterKind) derives ReadWriter

  val OperatorToSnapReporter: Map[String, SnapReporter] = Map(
    "<" -> SnapReporter("reportVariadicLessThan", SnapReporterKind.Variadic),
    ">" -> SnapReporter("reportVariadicGreaterThan", SnapReporterKind.Variadic),
    "==" -> SnapReporter("reportVariadicEquals", SnapReporterKind.Variadic),
    "<=" -> SnapReporter("reportVariadicLessThanOrEquals", SnapReporterKind.Variadic),
    ">=" -> SnapReporter("reportVariadicGreaterThanOrEquals", SnapReporterKind.Variadic),
    "!=" -> SnapReporter("reportVariadicNotEquals", SnapReporterKind.Variadic),
    "and" -> SnapReporter("reportVariadicAnd", SnapReporterKind.Variadic),
    "or" -> SnapReporter("reportVariadicOr", SnapReporterKind.Variadic),
    "not" -> SnapReporter("reportNot", SnapReporterKind.Unary),
    "+" -> SnapReporter("reportVariadicSum", SnapReporterKind.Variadic),
    "-" -> SnapReporter("reportDifference", SnapReporterKind.Binary),
    "*" -> SnapReporter("reportVariadicProduct", SnapReporterKind.Variadic),
    "/" -> SnapReporter("reportQuotient", SnapReporterKind.Binary),
    "%" -> SnapReporter("reportModulus", SnapReporterKind.Binary)
  )

  private val SupportedOperators: Set[String] = OperatorToSnapReporter.keySet

  private val SnapReporterToOperator: Map[String, String] =
    OperatorToSnapReporter.map { case (op, snap) => snap.selector -> op } ++ Map(
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
    hasSupportedStatements(expression, Map.empty)

  def hasSupportedStatements(expression: BeExpression, userFunctions: Map[String, Int]): Boolean =
    topLevelStatements(expression).exists(containsSupportedStatement(_, userFunctions))

  def containsSupportedStatement(expression: BeExpression): Boolean =
    containsSupportedStatement(expression, Map.empty)

  def containsSupportedStatement(expression: BeExpression, userFunctions: Map[String, Int]): Boolean =
    expression match
      case call: BeFunctionCall =>
        callProblems(call, userFunctions).isEmpty
      case assign: BeAssignVariable =>
        isSupportedAssignment(assign)
      case ifElse: BeIfElse =>
        isSupportedConditionSequence(ifElse.condition) &&
          bodyHasSupported(ifElse.thenBody, userFunctions) &&
          bodyHasSupported(ifElse.elseBody, userFunctions)
      case whileExpr: BeWhile =>
        isSupportedConditionSequence(whileExpr.condition) &&
          bodyHasSupported(whileExpr.body, userFunctions)
      case repeat: BeRepeatNr =>
        repeat.fixedAmount.forall(_ >= 0) && isSupportedValue(repeat.amount) && bodyHasSupported(repeat.body, userFunctions)
      case forExpr: BeFor =>
        isSupportedValue(forExpr.start) &&
          isSupportedValue(forExpr.end) &&
          bodyHasSupported(forExpr.body, userFunctions)
      case defn: BeDefineFunction =>
        bodyHasSupported(defn.body, userFunctions)
      case seq: BeSequence =>
        isPassSequence(seq) || seq.body.forall(containsSupportedStatement(_, userFunctions))
      case _ => false

  def validateStatements(expressions: List[BeExpression]): Either[String, List[BeExpression]] =
    validateStatements(expressions, Map.empty)

  def validateStatements(expressions: List[BeExpression], userFunctions: Map[String, Int]): Either[String, List[BeExpression]] =
    if expressions.isEmpty then Right(Nil)
    else
      val problems = expressions.flatMap(describeUnsupportedStatement(_, userFunctions))
      if problems.nonEmpty then Left("Unsupported for blocks: " + problems.take(3).mkString(" | "))
      else Right(expressions)

  def describeUnsupportedStatement(expression: BeExpression): Option[String] =
    describeUnsupportedStatement(expression, Map.empty)

  def describeUnsupportedStatement(expression: BeExpression, userFunctions: Map[String, Int]): Option[String] =
    expression match
      case call: BeFunctionCall =>
        callProblems(call, userFunctions)
      case assign: BeAssignVariable =>
        if isSupportedAssignment(assign) then None
        else Some(s"${variableName(assign.target)} = ...")
      case variable: BeDefineVariable =>
        variable.initValue.flatMap(value => describeUnsupportedStatement(BeAssignVariable(variable, value), userFunctions))
      case ifElse: BeIfElse =>
        conditionProblems(ifElse.condition)
          .orElse(bodyProblems(ifElse.thenBody, userFunctions))
          .orElse(bodyProblems(ifElse.elseBody, userFunctions))
      case whileExpr: BeWhile =>
        conditionProblems(whileExpr.condition).orElse(bodyProblems(whileExpr.body, userFunctions))
      case repeat: BeRepeatNr =>
        if repeat.fixedAmount.exists(_ < 0) then Some("negative repeat count")
        else if !isSupportedValue(repeat.amount) then Some("repeat count")
        else bodyProblems(repeat.body, userFunctions)
      case forExpr: BeFor =>
        if !isSupportedValue(forExpr.start) || !isSupportedValue(forExpr.end) then Some("for range")
        else bodyProblems(forExpr.body, userFunctions)
      case defn: BeDefineFunction =>
        if it.evadid.vm.BeProgram.isUnusedCode(defn) && (defn.inputs.nonEmpty || defn.outputs.exists(_.variableType != BeDataType.Unit)) then
          Some("unusedCode must have no parameters or return value")
        else bodyProblems(defn.body, userFunctions)
      case seq: BeSequence if isPassSequence(seq) =>
        None
      case seq: BeSequence =>
        seq.body.flatMap(describeUnsupportedStatement(_, userFunctions)).headOption
      case _: BeSingleLineComment => None
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
      case call: BeFunctionCall if isOperatorNamed(call, "not") =>
        call.parameterValueMap.values.headOption.getOrElse(BeExpression.pass)
      case other =>
        operatorCall("not", List(other))

  def isSnapValueReporter(selector: String): Boolean =
    ConditionSelectors.contains(selector) || ArithmeticSelectors.contains(selector)

  def conditionFromSnapReporter(call: BeFunctionCall): BeExpression = {
    val selector = functionSelector(call)
    selector match
      case "reportTrue" =>
        BeUseValue(BeDataValueLiteral("True"), None)
      case "reportFalse" =>
        BeUseValue(BeDataValueLiteral("False"), None)
      case "reportBoolean" =>
        val value = call.arguments.headOption.collect {
          case BeUseValue(BeDataValueLiteral(v), _) => v
        }.getOrElse("True")
        BeUseValue(BeDataValueLiteral(if value.equalsIgnoreCase("false") then "False" else "True"), None)
      case "reportNot" =>
        val inner = call.arguments.headOption.getOrElse(BeExpression.pass)
        val parsedInner = inner match
          case innerCall: BeFunctionCall => conditionFromSnapReporter(innerCall)
          case other => other
        operatorCall("not", List(parsedInner))
      case snap if SnapReporterToOperator.contains(snap) =>
        val op = SnapReporterToOperator(snap)
        val args = call.arguments
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
              call.arguments.forall(arg => isSupportedCondition(arg) || isSupportedValue(arg))
          case _ =>
            false
      case _ => false

  def isSupportedValue(expression: BeExpression): Boolean =
    expression match
      case BeUseValue(BeDataValueLiteral(_), _) => true
      case BeUseValue(BeUseValueReference(_), _) => true
      case call: BeFunctionCall =>
        call.funcDef.functionTypeInfo.funcType match
          case BeDefineFunction.Operator(_) =>
            val op = operatorSymbol(call)
            SupportedOperators.contains(op) &&
              call.arguments.forall(isSupportedValue)
          // Library reporters such as `url` produce a value in Snap as well.
          case _ =>
            call.funcDef.outputs.nonEmpty &&
              SnapTurtleCatalog.LibraryPrimitives.exists(_.pythonName == SnapExpressionSupport.pythonName(call)) &&
              call.arguments.forall(isSupportedValue)
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
      case call: BeFunctionCall if isOperatorNamed(call, "+") =>
        call.arguments match
          case List(BeUseValue(BeUseValueReference(referenced), _), amount)
            if variableName(referenced) == variableName(assign.target) && isSupportedValue(amount) =>
            Some(amount)
          case _ => None
      case _ => None

  def collectVariableNames(expression: BeExpression): List[String] =
    declaredVariables(expression).map(_._1)

  /**
   * Scene variables in walk order, with the first literal assignment when one exists.
   * A later read does not replace that literal; a name that is only read has none.
   */
  def declaredVariables(expression: BeExpression): List[(String, Option[String])] = {
    val order = scala.collection.mutable.LinkedHashSet.empty[String]
    val literals = scala.collection.mutable.LinkedHashMap.empty[String, String]
    def note(name: String): Unit =
      if name.nonEmpty then order += name
    def noteLiteral(name: String, literal: String): Unit =
      note(name)
      if name.nonEmpty && !literals.contains(name) then literals += name -> literal
    def walk(node: BeExpression): Unit = node match
      case variable: BeDefineVariable =>
        variable.initValue match
          case Some(value) => walk(BeAssignVariable(variable, value))
          case None => note(variableName(variable))
      case BeAssignVariable(target, value) =>
        val name = variableName(target)
        value match
          case BeUseValue(BeDataValueLiteral(literal), _) => noteLiteral(name, literal)
          case _ => note(name)
        walk(value)
      case BeUseValue(BeUseValueReference(variable), _) =>
        note(variableName(variable))
      case call: BeFunctionCall =>
        call.arguments.foreach(walk)
      case ifElse: BeIfElse =>
        ifElse.condition.body.foreach(walk)
        ifElse.thenBody.body.foreach(walk)
        ifElse.elseBody.body.foreach(walk)
      case whileExpr: BeWhile =>
        whileExpr.condition.body.foreach(walk)
        whileExpr.body.body.foreach(walk)
      case repeat: BeRepeatNr =>
        repeat.body.body.foreach(walk)
      case forExpr: BeFor =>
        note(variableName(forExpr.variable))
        walk(forExpr.start)
        walk(forExpr.end)
        forExpr.body.body.foreach(walk)
      case defn: BeDefineFunction =>
        // Parameters belong to the definition's own scope, not to the scene variables.
        walk(defn.body)
      case seq: BeSequence =>
        seq.body.foreach(walk)
      case BeStartProgram(Some(seq)) =>
        seq.body.foreach(walk)
      case _ => ()
    walk(expression)
    order.toList.map(name => name -> literals.get(name))
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
    BeFunctionCall.fromDefinition(define, params.zip(args).toMap)
  }

  def literalInt(value: String): Option[Int] =
    scala.util.Try(value.trim.toDouble.round.toInt).toOption.filter(_ >= 0)

  private def callProblems(call: BeFunctionCall, userFunctions: Map[String, Int]): Option[String] = {
    if SnapExpressionSupport.pythonName(call) == it.evadid.vm.BeProgram.unusedCodeName.universalInterpretation() then
      Some("unusedCode is reserved for detached scripts and cannot be called")
    else if !call.target.isInstanceOf[it.evadid.vm.code.usage.BeCallTarget.Function] &&
        !it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.isTurtleMethod(call.funcDef) then
      Some("object methods and constructors have no Snap block representation")
    else if call.isOperator then Some(s"${SnapExpressionSupport.pythonName(call)}(...)")
    else
      val name = SnapExpressionSupport.pythonName(call)
      val args = call.arguments
      SnapTurtleCatalog.primitiveByPythonName.get(name) match
        case Some(primitive) =>
          SnapTurtleCatalog.validateCallArity(name, args.size).orElse {
            val invalidSlot = args.zip(primitive.inputKinds).find { (arg, kind) =>
              !SnapInputCodec.isValidForKind(kind, arg)
            }
            invalidSlot match
              case Some((_, SnapInputKind.Color)) => Some(s"$name has an unsupported color")
              case Some(_) => Some(s"$name(...)")
              case None => None
          }
        case None =>
          userFunctions.get(name) match
            case Some(expected) =>
              // Extra call args are dropped on write-back; missing ones become empty slots.
              if args.take(expected).forall(isSupportedValue) then None
              else Some(s"$name(...)")
            case None =>
              Some(s"$name(...)")
  }

  private def bodyHasSupported(sequence: BeSequence, userFunctions: Map[String, Int] = Map.empty): Boolean =
    isPassSequence(sequence) || sequence.body.forall(containsSupportedStatement(_, userFunctions))

  private def bodyProblems(sequence: BeSequence, userFunctions: Map[String, Int] = Map.empty): Option[String] =
    if isPassSequence(sequence) then None
    else sequence.body.flatMap(describeUnsupportedStatement(_, userFunctions)).headOption

  private def isPassSequence(sequence: BeSequence): Boolean =
    sequence.body.isEmpty || sequence.body.forall {
      case nested: BeSequence => isPassSequence(nested)
      case _ => false
    }

  private def isSupportedConditionSequence(sequence: BeSequence): Boolean =
    sequence.body.forall(isSupportedCondition)

  private def conditionProblems(sequence: BeSequence): Option[String] =
    sequence.body.find(!isSupportedCondition(_)).map(expr => preview(expr.getClass.getSimpleName))

  private def isOperatorNamed(call: BeFunctionCall, symbol: String): Boolean =
    call.funcDef.functionTypeInfo.funcType match
      case BeDefineFunction.Operator(_) => operatorSymbol(call) == symbol
      case _ => false

  private def preview(text: String): String =
    val trimmed = text.trim
    if trimmed.length <= 40 then trimmed
    else trimmed.take(37) + "..."
}
