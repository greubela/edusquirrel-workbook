package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeFor, BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.types.{BeDataValueLiteral, BeUseValueReference}
import it.evadid.workbook.elements.interactionElements.programming.{SnapControlFlow, SnapTurtleCatalog, SnapTurtlePythonBridge}

/**
 * Interprets a turtle-subset BeExpression into TurtleCommands for SVG preview.
 * Repeats / for / if / while are unrolled; user `def` calls run the definition body.
 */
object BeExpressionToTurtleCommands {

  val SnapHeadingDeg: Double = 90

  def apply(expression: BeExpression): List[TurtleCommand[Double]] =
    interpret(expression, InterpState.initial(expression)).commands

  def toPathBuilder(
      expression: BeExpression,
      startPoint: Point[Double] = Point(0.0, 0.0),
      headingDeg: Double = SnapHeadingDeg
  ): TurtlePathBuilder[Double] =
    TurtlePathBuilder(startPoint, apply(expression), headingDeg)

  private final case class InterpState(
      env: Map[String, Double],
      defs: Map[String, BeDefineFunction],
      commands: List[TurtleCommand[Double]]
  )

  private object InterpState {
    def initial(expression: BeExpression): InterpState = {
      val defs = scala.collection.mutable.LinkedHashMap.empty[String, BeDefineFunction]
      def collect(node: BeExpression): Unit = node match
        case defn: BeDefineFunction =>
          defs += SnapTurtlePythonBridge.pythonNameOf(defn) -> defn
          collect(defn.body)
        case seq: BeSequence => seq.body.foreach(collect)
        case BeStartProgram(Some(seq)) => seq.body.foreach(collect)
        case ifElse: BeIfElse =>
          ifElse.thenBody.body.foreach(collect)
          ifElse.elseBody.body.foreach(collect)
        case whileExpr: BeWhile => whileExpr.body.body.foreach(collect)
        case repeat: BeRepeatNr => repeat.body.body.foreach(collect)
        case forExpr: BeFor => forExpr.body.body.foreach(collect)
        case _ => ()
      collect(expression)
      InterpState(Map.empty, defs.toMap, Nil)
    }
  }

  private def interpret(expression: BeExpression, state: InterpState): InterpState =
    expression match
      case BeStartProgram(Some(seq)) => interpret(seq, state)
      case BeStartProgram(None) => state
      case seq: BeSequence => seq.body.foldLeft(state)((s, expr) => interpret(expr, s))
      case _: BeDefineFunction => state
      case assign: BeAssignVariable =>
        val value = evalNumber(assign.value, state)
        state.copy(env = state.env + (SnapControlFlow.variableName(assign.target) -> value))
      case repeat: BeRepeatNr =>
        (0 until repeat.amount).foldLeft(state)((s, _) => interpret(repeat.body, s))
      case forExpr: BeFor =>
        val start = evalNumber(forExpr.start, state).round.toInt
        val end = evalNumber(forExpr.end, state).round.toInt
        val name = SnapControlFlow.variableName(forExpr.variable)
        val range = if start <= end then start to end else start to end by -1
        range.foldLeft(state) { (s, i) =>
          interpret(forExpr.body, s.copy(env = s.env + (name -> i.toDouble)))
        }
      case ifElse: BeIfElse =>
        val cond = evalBool(ifElse.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("True"), None)), state)
        if cond then interpret(ifElse.thenBody, state) else interpret(ifElse.elseBody, state)
      case whileExpr: BeWhile =>
        var current = state
        var guard = 0
        while guard < 10000 && evalBool(whileExpr.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("False"), None)), current) do
          current = interpret(whileExpr.body, current)
          guard += 1
        current
      case call: BeFunctionCall =>
        interpretCall(call, state)
      case _ =>
        state

  private def interpretCall(call: BeFunctionCall, state: InterpState): InterpState = {
    if SnapControlFlow.isOperatorCall(call) then state
    else
      val python = SnapTurtlePythonBridge.pythonName(call)
      val args = SnapControlFlow.orderedArgs(call)
      state.defs.get(python) match
        case Some(defn) =>
          val bound = defn.inputs.zip(args).map { (param, arg) =>
            SnapControlFlow.variableName(param) -> evalNumber(arg, state)
          }.toMap
          interpret(defn.body, state.copy(env = state.env ++ bound))
        case None =>
          val turtleName = SnapTurtleCatalog.turtleCommandByPythonName.getOrElse(python, python)
          val numeric = args.flatMap(arg => numericArg(arg, state))
          val strings = args.flatMap(stringArg)
          state.copy(commands = state.commands :+ TurtleCommand(turtleName, numeric, strings))
  }

  private def numericArg(expression: BeExpression, state: InterpState): Option[Double] =
    expression match
      case BeUseValue(BeDataValueLiteral(value), _) if value.exists(ch => ch.isDigit || ch == '.' || ch == '-') =>
        scala.util.Try(value.trim.toDouble).toOption
      case BeUseValue(BeUseValueReference(_), _) =>
        Some(evalNumber(expression, state))
      case call: BeFunctionCall if SnapControlFlow.isOperatorCall(call) =>
        Some(evalNumber(call, state))
      case _ =>
        None

  private def stringArg(expression: BeExpression): Option[String] =
    expression match
      case BeUseValue(BeDataValueLiteral(value), _) if !value.exists(ch => ch.isDigit) || value.exists(ch => ch.isLetter || ch == ',') =>
        Some(value.replace("\"", "").replace("'", ""))
      case _ => None

  private def evalNumber(expression: BeExpression, state: InterpState): Double =
    expression match
      case BeUseValue(BeDataValueLiteral(value), _) =>
        scala.util.Try(value.trim.toDouble).toOption.getOrElse(if isTruthy(value) then 1.0 else 0.0)
      case BeUseValue(BeUseValueReference(variable), _) =>
        state.env.getOrElse(SnapControlFlow.variableName(variable), 0.0)
      case seq: BeSequence =>
        seq.body.lastOption.map(evalNumber(_, state)).getOrElse(0.0)
      case call: BeFunctionCall if SnapControlFlow.isOperatorCall(call) =>
        evalOperator(call, state)
      case _ =>
        0.0

  private def evalOperator(call: BeFunctionCall, state: InterpState): Double = {
    val op = SnapControlFlow.operatorSymbol(call)
    val args = SnapControlFlow.orderedArgs(call).map(evalNumber(_, state))
    op match
      case "+" => args.sum
      case "-" if args.size == 1 => -args.head
      case "-" => args.headOption.getOrElse(0.0) - args.drop(1).sum
      case "*" => args.product
      case "/" =>
        val denom = args.drop(1).headOption.getOrElse(1.0)
        if denom == 0 then 0.0 else args.headOption.getOrElse(0.0) / denom
      case "<" => if compare(args, _ < _) then 1.0 else 0.0
      case ">" => if compare(args, _ > _) then 1.0 else 0.0
      case "==" => if args.sliding(2).forall(pair => pair.size < 2 || pair.head == pair(1)) then 1.0 else 0.0
      case "<=" => if compare(args, _ <= _) then 1.0 else 0.0
      case ">=" => if compare(args, _ >= _) then 1.0 else 0.0
      case "!=" => if args.size >= 2 && args.head != args(1) then 1.0 else 0.0
      case "and" => if args.forall(_ != 0) then 1.0 else 0.0
      case "or" => if args.exists(_ != 0) then 1.0 else 0.0
      case "not" => if args.headOption.getOrElse(0.0) == 0 then 1.0 else 0.0
      case _ => 0.0
  }

  private def compare(args: List[Double], op: (Double, Double) => Boolean): Boolean =
    args.sliding(2).forall(pair => pair.size < 2 || op(pair.head, pair(1)))

  private def evalBool(expression: BeExpression, state: InterpState): Boolean =
    evalNumber(expression, state) != 0.0 || (expression match
      case BeUseValue(BeDataValueLiteral(value), _) => isTruthy(value)
      case _ => false)

  private def isTruthy(value: String): Boolean = {
    val trimmed = value.trim
    trimmed.equalsIgnoreCase("True") || trimmed.equalsIgnoreCase("true") || trimmed == "1"
  }
}
