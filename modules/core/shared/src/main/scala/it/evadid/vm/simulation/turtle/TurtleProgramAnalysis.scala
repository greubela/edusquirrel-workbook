package it.evadid.vm.simulation.turtle

import it.evadid.vm.conversion.snapexpression.SnapControlFlow
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.util.io.Serializer
import it.evadid.core.datastructures.vectorShapes.svg.{TurtleCommand, TurtlePathBuilder}
import it.evadid.vm.conversion.javaexpression.checked.{JavaTurtleVmPrograms, JavaTurtleResolution}
import it.evadid.vm.simulation.java.JavaTurtleRuntime
import scala.annotation.tailrec
import scala.util.control.NonFatal
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeFor, BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.types.{BeDataValueLiteral, BeUseValueReference}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{SnapTurtleCatalog}

/**
 * Analyzes authored turtle call sites and executes the supported program.
 * Checked Java uses its existing runtime; other expressions use the turtle subset
 * interpreter. Both paths retain partial commands and report completion explicitly.
 */
object TurtleProgramAnalysis {

  def apply(expression: BeExpression, limits: TurtleExecutionLimits = TurtleExecutionLimits(),
      isCancelled: () => Boolean = () => false): TurtleCommandExecutionInfo[Double] = {
    val sites = commandSites(expression)
    expression match {
      case root: JavaTurtleVmPrograms.Root => analyzeJava(root, sites, limits, isCancelled)
      case _ =>
        val result = interpret(expression, InterpState.initial(expression), limits, isCancelled)
        TurtleCommandExecutionInfo(result.commands.toList, sites, result.status)
    }
  }

  /** Static analysis can also be requested without executing the program. */
  def commandSites(expression: BeExpression): List[TurtleCommandSite[Double]] = TurtleCommandSites(expression)

  def toPathBuilder(expression: BeExpression, startPoint: Point[Double] = Point(0.0, 0.0),
      headingDeg: Double = TurtlePathBuilder.SnapHeadingDeg): TurtlePathBuilder[Double] =
    TurtlePathBuilder(startPoint, apply(expression).completedCommands, headingDeg)

  private def analyzeJava(root: JavaTurtleVmPrograms.Root, sites: List[TurtleCommandSite[Double]],
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): TurtleCommandExecutionInfo[Double] = {
    val execution = JavaTurtleRuntime.runVm(JavaTurtleVmPrograms.fromRoot(root),
      JavaTurtleRuntime.Limits(maxSteps = limits.maxSteps, maxCommands = limits.maxCommands,
        maxCallDepth = limits.maxCallDepth), isCancelled)
    val status = execution.status match {
      case JavaTurtleRuntime.Status.Completed => TurtleExecutionStatus.Completed
      case JavaTurtleRuntime.Status.LimitExceeded => TurtleExecutionStatus.LimitExceeded
      case JavaTurtleRuntime.Status.Cancelled => TurtleExecutionStatus.Cancelled
      case JavaTurtleRuntime.Status.Failed(problem) => TurtleExecutionStatus.Failed(problem.toString)
    }
    val commands = execution.commands.toList.map { command => command.command match {
      case JavaTurtleResolution.TurtleCommand.Forward => TurtleCommand.Forward(command.value)
      case JavaTurtleResolution.TurtleCommand.TurnRight => TurtleCommand.Right(command.value)
    }}
    TurtleCommandExecutionInfo(commands, sites, status)
  }

  private final case class InterpState(
      env: Map[String, Double],
      defs: Map[String, BeDefineFunction],
      commands: Vector[TurtleCommand[Double]],
      steps: Int = 0,
      callDepth: Int = 0,
      status: TurtleExecutionStatus = TurtleExecutionStatus.Completed
  )

  private object InterpState {
    def initial(expression: BeExpression): InterpState = {
      val defs = TurtleCommandSites.nodes(expression).collect {
        case (_, definition: BeDefineFunction) => SnapExpressionSupport.pythonNameOf(definition) -> definition
      }.toMap
      InterpState(Map.empty, defs, Vector.empty)
    }
  }

  private def interpret(expression: BeExpression, state: InterpState, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): InterpState = {
    if state.status != TurtleExecutionStatus.Completed then state
    else try {
      if isCancelled() then state.copy(status = TurtleExecutionStatus.Cancelled)
      else if state.steps >= limits.maxSteps then state.copy(status = TurtleExecutionStatus.LimitExceeded)
      else interpretNode(expression, state.copy(steps = state.steps + 1), limits, isCancelled)
    } catch {
      case NonFatal(error) => state.copy(status = TurtleExecutionStatus.Failed(Option(error.getMessage).getOrElse(error.getClass.getSimpleName)))
    }
  }

  private def interpretNode(expression: BeExpression, state: InterpState, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): InterpState = expression match {
    case BeStartProgram(Some(seq)) => interpret(seq, state, limits, isCancelled)
    case BeStartProgram(None) => state
    case seq: BeSequence =>
      seq.body.foldLeft(state)((current, child) => interpret(child, current, limits, isCancelled))
    case _: BeDefineFunction => state
    case assign: BeAssignVariable =>
      state.copy(env = state.env.updated(SnapControlFlow.variableName(assign.target), evalNumber(assign.value, state)))
    case repeat: BeRepeatNr =>
      repeatBody(repeat.body, repeat.amount, state, limits, isCancelled)
    case loop: BeFor =>
      val start = evalNumber(loop.start, state).round.toInt.toLong
      val end = evalNumber(loop.end, state).round.toInt.toLong
      forBody(loop, start, end, if start <= end then 1L else -1L, state, limits, isCancelled)
    case branch: BeIfElse =>
      val condition = branch.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("True"), None))
      interpret(if evalBool(condition, state) then branch.thenBody else branch.elseBody, state, limits, isCancelled)
    case loop: BeWhile => whileBody(loop, 0, state, limits, isCancelled)
    case call: BeFunctionCall => interpretCall(call, state, limits, isCancelled)
    case _ => state
  }

  @tailrec private def repeatBody(body: BeExpression, remaining: Int, state: InterpState,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): InterpState =
    if remaining <= 0 || state.status != TurtleExecutionStatus.Completed then state
    else repeatBody(body, remaining - 1, interpret(body, state, limits, isCancelled), limits, isCancelled)

  @tailrec private def forBody(loop: BeFor, index: Long, end: Long, step: Long, state: InterpState,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): InterpState =
    if state.status != TurtleExecutionStatus.Completed || (if step > 0 then index > end else index < end) then state
    else {
      val bound = state.copy(env = state.env.updated(SnapControlFlow.variableName(loop.variable), index.toDouble))
      forBody(loop, index + step, end, step, interpret(loop.body, bound, limits, isCancelled), limits, isCancelled)
    }

  @tailrec private def whileBody(loop: BeWhile, iterations: Int, state: InterpState,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): InterpState = {
    val condition = loop.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("False"), None))
    if state.status != TurtleExecutionStatus.Completed || !evalBool(condition, state) then state
    else if iterations >= limits.maxLoopIterations then state.copy(status = TurtleExecutionStatus.LimitExceeded)
    else whileBody(loop, iterations + 1, interpret(loop.body, state, limits, isCancelled), limits, isCancelled)
  }

  private def interpretCall(call: BeFunctionCall, state: InterpState, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): InterpState = {
    if SnapControlFlow.isOperatorCall(call) then state
    else {
      val python = SnapExpressionSupport.pythonName(call)
      val args = SnapControlFlow.orderedArgs(call)
      state.defs.get(python).filterNot(_ => TurtleCommandSites.isBoundTurtleMethod(call)) match {
        case Some(definition) =>
          if state.callDepth >= limits.maxCallDepth then state.copy(status = TurtleExecutionStatus.LimitExceeded)
          else {
            val bound = definition.inputs.zip(args).map { (parameter, argument) =>
              SnapControlFlow.variableName(parameter) -> evalNumber(argument, state)
            }.toMap
            val child = state.copy(env = state.env ++ bound, callDepth = state.callDepth + 1)
            interpret(definition.body, child, limits, isCancelled).copy(env = state.env, callDepth = state.callDepth)
          }
        case None =>
          val name = SnapTurtleCatalog.turtleCommandByPythonName.getOrElse(python, python)
          val command = TurtleCommand.fromCall(it.evadid.vm.naming.BeEntityName.fromCodeString(name),
            args.flatMap(numericArg(_, state)), args.flatMap(stringArg))
          if !command.args.forall(_.isFinite) then state.copy(status = TurtleExecutionStatus.Failed("A drawing command needs finite numbers."))
          else if state.commands.size >= limits.maxCommands then state.copy(status = TurtleExecutionStatus.LimitExceeded)
          else state.copy(commands = state.commands :+ command)
      }
    }
  }

  private def numericArg(expression: BeExpression, state: InterpState): Option[Double] =
    expression match
      case BeUseValue(BeDataValueLiteral(value), _) => numericLiteral(value)
      case BeUseValue(BeUseValueReference(_), _) =>
        Some(evalNumber(expression, state))
      case call: BeFunctionCall if SnapControlFlow.isOperatorCall(call) =>
        Some(evalNumber(call, state))
      case _ =>
        None

  private def stringArg(expression: BeExpression): Option[String] =
    expression match
      case BeUseValue(BeDataValueLiteral(value), _) if numericLiteral(value).isEmpty =>
        Some(Serializer.stringLiteralIO(parseEverything = true).deserialize(value))
      case _ => None

  private def numericLiteral(value: String): Option[Double] =
    if value.exists(ch => ch.isDigit || ch == '.' || ch == '-') then value.trim.toDoubleOption
    else None

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
      case "%" =>
        val dividend = args.headOption.getOrElse(0.0)
        val divisor = args.drop(1).headOption.getOrElse(1.0)
        if divisor == 0 then 0.0 else dividend - divisor * math.floor(dividend / divisor)
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
