package it.evadid.vm.simulation.turtle

import it.evadid.vm.conversion.snapexpression.SnapControlFlow
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.util.io.Serializer
import it.evadid.core.datastructures.vectorShapes.svg.{TurtleCommand, TurtlePathBuilder}
import scala.annotation.tailrec
import scala.util.control.NonFatal
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeFor, BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable, BeIntrinsic}
import it.evadid.vm.code.errors.BeSourcePlaceholder
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.{BeAssignVariable, BeCallTarget, BeFunctionCall, BeObjectExpression, BeUseValue}
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
    val result = interpret(expression, InterpState.initial(expression), limits, isCancelled)
    TurtleCommandExecutionInfo(result.commands.toList, sites, result.status)
  }

  /** Static analysis can also be requested without executing the program. */
  def commandSites(expression: BeExpression): List[TurtleCommandSite[Double]] = TurtleCommandSites(expression)

  def toPathBuilder(expression: BeExpression, startPoint: Point[Double] = Point(0.0, 0.0),
      headingDeg: Double = TurtlePathBuilder.SnapHeadingDeg): TurtlePathBuilder[Double] =
    TurtlePathBuilder(startPoint, apply(expression).completedCommands, headingDeg)

  private final case class InterpState(
      env: Map[String, Value],
      defs: Map[String, BeDefineFunction],
      commands: Vector[TurtleCommand[Double]],
      steps: Int = 0,
      callDepth: Int = 0,
      status: TurtleExecutionStatus = TurtleExecutionStatus.Completed,
      /** Set by `return`: the rest of the function does not run, and its call produces this value. */
      returned: Option[Value] = None
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
    if state.status != TurtleExecutionStatus.Completed || state.returned.nonEmpty then state
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
    case _: BeObjectExpression => unsupportedObjectExecution()
    case _: BeDefineFunction => state
    // A declared module variable exists before it is assigned (Java fields and Python `x: float` start at 0).
    case variable: BeDefineVariable => state.copy(env = state.env.updatedWith(SnapControlFlow.variableName(variable))(_.orElse(Some(Value.Num(0.0)))))
    case assign: BeAssignVariable =>
      val (value, evaluated) = resolveCalls(assign.value, state, limits, isCancelled)
      evaluated.copy(env = evaluated.env.updated(SnapControlFlow.variableName(assign.target), eval(value, evaluated)))
    case BeReturn(value) =>
      val (resolved, evaluated) = value.fold((None, state))(value =>
        resolveCalls(value, state, limits, isCancelled) match { case (resolved, evaluated) => (Some(resolved), evaluated) })
      evaluated.copy(returned = Some(resolved.map(eval(_, evaluated)).getOrElse(Value.Num(0.0))))
    case repeat: BeRepeatNr =>
      // Like Python's range and Snap's repeat, a fractional count rounds down and a negative one repeats nothing.
      val (amount, evaluated) = resolveCalls(repeat.amount, state, limits, isCancelled)
      repeatBody(repeat.body, math.max(0, math.floor(evalNumber(amount, evaluated)).toInt), evaluated, limits, isCancelled)
    case loop: BeFor =>
      val (first, afterStart) = resolveCalls(loop.start, state, limits, isCancelled)
      val (last, bounded) = resolveCalls(loop.end, afterStart, limits, isCancelled)
      val start = evalNumber(first, bounded).round.toInt.toLong
      val end = evalNumber(last, bounded).round.toInt.toLong
      val step = loop.direction match {
        case BeFor.Direction.Up => 1L
        case BeFor.Direction.Down => -1L
        case BeFor.Direction.Either => if start <= end then 1L else -1L
      }
      forBody(loop, start, end, step, bounded, limits, isCancelled)
    case branch: BeIfElse =>
      val (condition, evaluated) = resolveCalls(branch.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("True"), None)),
        state, limits, isCancelled)
      interpret(if evalBool(condition, evaluated) then branch.thenBody else branch.elseBody, evaluated, limits, isCancelled)
    case loop: BeWhile => whileBody(loop, 0, state, limits, isCancelled)
    case call: BeFunctionCall => interpretCall(call, state, limits, isCancelled)
    case placeholder: BeSourcePlaceholder => cannotRun(placeholder)
    case _ => state
  }

  /** Unfinished or unsupported source stops the drawing rather than being skipped. */
  private def cannotRun(placeholder: BeSourcePlaceholder): Nothing =
    throw IllegalArgumentException(s"This part of the program cannot run: ${placeholder.originalSource.trim.linesIterator.nextOption().getOrElse("")}")

  @tailrec private def repeatBody(body: BeExpression, remaining: Int, state: InterpState,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): InterpState =
    if remaining <= 0 || state.status != TurtleExecutionStatus.Completed || state.returned.nonEmpty then state
    else repeatBody(body, remaining - 1, interpret(body, state, limits, isCancelled), limits, isCancelled)

  @tailrec private def forBody(loop: BeFor, index: Long, end: Long, step: Long, state: InterpState,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): InterpState =
    if state.status != TurtleExecutionStatus.Completed || state.returned.nonEmpty || (if step > 0 then index > end else index < end) then state
    else {
      val bound = state.copy(env = state.env.updated(SnapControlFlow.variableName(loop.variable), Value.Num(index.toDouble)))
      forBody(loop, index + step, end, step, interpret(loop.body, bound, limits, isCancelled), limits, isCancelled)
    }

  @tailrec private def whileBody(loop: BeWhile, iterations: Int, state: InterpState,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): InterpState = {
    if state.status != TurtleExecutionStatus.Completed || state.returned.nonEmpty then state
    else {
      val (condition, evaluated) = resolveCalls(loop.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("False"), None)),
        state, limits, isCancelled)
      if evaluated.status != TurtleExecutionStatus.Completed || !evalBool(condition, evaluated) then evaluated
      else if iterations >= limits.maxLoopIterations then evaluated.copy(status = TurtleExecutionStatus.LimitExceeded)
      else whileBody(loop, iterations + 1, interpret(loop.body, evaluated, limits, isCancelled), limits, isCancelled)
    }
  }

  private def interpretCall(unresolved: BeFunctionCall, before: InterpState, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): InterpState = {
    if !unresolved.target.isInstanceOf[BeCallTarget.Function] && !TurtleCommand.isTurtleMethod(unresolved.funcDef) then unsupportedObjectExecution()
    else if unresolved.isOperator then before
    else {
      val (call, state) = resolveArguments(unresolved, before, limits, isCancelled)
      val python = SnapExpressionSupport.pythonName(call)
      val args = call.arguments
      userDefinition(call, state) match {
        case Some(definition) => callFunction(call, definition, state, limits, isCancelled)._2
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

  private def userDefinition(call: BeFunctionCall, state: InterpState): Option[BeDefineFunction] =
    if call.isOperator || !call.target.isInstanceOf[BeCallTarget.Function] || TurtleCommand.isTurtleMethod(call.funcDef) then None
    else state.defs.get(SnapExpressionSupport.pythonName(call))

  /** Runs a call of the program's own function, whose arguments are already values; returns what it returned. */
  private def callFunction(call: BeFunctionCall, definition: BeDefineFunction, state: InterpState, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): (Value, InterpState) =
    if state.callDepth >= limits.maxCallDepth then (Value.Num(0.0), state.copy(status = TurtleExecutionStatus.LimitExceeded))
    else {
      val bound = definition.inputs.zip(call.arguments).map { (parameter, argument) =>
        SnapControlFlow.variableName(parameter) -> eval(argument, state)
      }.toMap
      val child = state.copy(env = state.env ++ bound, callDepth = state.callDepth + 1)
      val after = interpret(definition.body, child, limits, isCancelled)
      // A function updates module variables; its parameters and own locals end with the call.
      val updated = after.env.filter((name, _) => state.env.contains(name) && !bound.contains(name))
      (after.returned.getOrElse(Value.Num(0.0)), after.copy(env = state.env ++ updated, callDepth = state.callDepth, returned = None))
    }

  /** Runs the program's own function calls inside an expression, innermost and left to right, and replaces each
    * by the value it returned; drawing they do happens before the expression's own statement.
    */
  private def resolveCalls(expression: BeExpression, state: InterpState, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): (BeExpression, InterpState) =
    if state.status != TurtleExecutionStatus.Completed then (expression, state)
    else expression match {
      case call: BeFunctionCall =>
        val (resolved, evaluated) = resolveArguments(call, state, limits, isCancelled)
        userDefinition(resolved, evaluated) match {
          case Some(definition) =>
            val (value, after) = callFunction(resolved, definition, evaluated, limits, isCancelled)
            (literal(value), after)
          case None => (resolved, evaluated)
        }
      case sequence: BeSequence =>
        val (body, evaluated) = resolveAll(sequence.body.toList, state, limits, isCancelled)
        (sequence.copy(body = body), evaluated)
      case other => (other, state)
    }

  private def resolveArguments(call: BeFunctionCall, state: InterpState, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): (BeFunctionCall, InterpState) = {
    val parameters = call.funcDef.inputs.filter(call.parameterValueMap.contains)
    val (values, evaluated) = resolveAll(parameters.map(call.parameterValueMap), state, limits, isCancelled)
    (call.copy(parameterValueMap = call.parameterValueMap ++ parameters.zip(values)), evaluated)
  }

  private def resolveAll(expressions: List[BeExpression], state: InterpState, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): (List[BeExpression], InterpState) =
    expressions.foldLeft((List.empty[BeExpression], state)) { case ((done, current), expression) =>
      val (resolved, next) = resolveCalls(expression, current, limits, isCancelled)
      (done :+ resolved, next)
    }

  private def literal(value: Value): BeExpression = value match
    case Value.Num(number) => BeUseValue(BeDataValueLiteral(number.toString), None)
    case Value.Text(text) => BeUseValue(BeDataValueLiteral(Serializer.stringLiteralIO(parseEverything = true).serialize(text)), None)

  private def unsupportedObjectExecution(): Nothing =
    throw UnsupportedOperationException("The drawing simulator does not support object operations.")

  private def numericArg(expression: BeExpression, state: InterpState): Option[Double] =
    expression match
      case _: BeObjectExpression => unsupportedObjectExecution()
      case call: BeFunctionCall if !call.target.isInstanceOf[BeCallTarget.Function] && !TurtleCommand.isTurtleMethod(call.funcDef) => unsupportedObjectExecution()
      case BeUseValue(BeDataValueLiteral(value), _) => numericLiteral(value)
      case BeUseValue(BeUseValueReference(_), _) =>
        Some(evalNumber(expression, state))
      case call: BeFunctionCall if call.isOperator || call.funcDef.intrinsic.exists(numericIntrinsics) =>
        Some(evalNumber(call, state))
      case placeholder: BeSourcePlaceholder => cannotRun(placeholder)
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

  /** The values the drawing simulation computes with: numbers (booleans are 1 and 0) and text. */
  private enum Value {
    case Num(value: Double)
    case Text(value: String)
  }

  private def eval(expression: BeExpression, state: InterpState): Value =
    expression match
      case _: BeObjectExpression => unsupportedObjectExecution()
      case call: BeFunctionCall if !call.target.isInstanceOf[BeCallTarget.Function] && !TurtleCommand.isTurtleMethod(call.funcDef) => unsupportedObjectExecution()
      case BeUseValue(BeDataValueLiteral(value), _) =>
        value.trim.toDoubleOption.map(Value.Num(_))
          .orElse(stringArg(expression).filterNot(_ => isTruthy(value) || value.trim.equalsIgnoreCase("false")).map(Value.Text(_)))
          .getOrElse(Value.Num(if isTruthy(value) then 1.0 else 0.0))
      case BeUseValue(BeUseValueReference(variable), _) =>
        state.env.getOrElse(SnapControlFlow.variableName(variable), Value.Num(0.0))
      case seq: BeSequence =>
        seq.body.lastOption.map(eval(_, state)).getOrElse(Value.Num(0.0))
      case call: BeFunctionCall if call.isOperator =>
        evalOperator(call, state)
      case placeholder: BeSourcePlaceholder => cannotRun(placeholder)
      case call: BeFunctionCall if call.funcDef.intrinsic.contains(BeIntrinsic.ToText) =>
        Value.Text(call.arguments.headOption.map(argument => text(eval(argument, state))).getOrElse(""))
      case call: BeFunctionCall if call.funcDef.intrinsic.contains(BeIntrinsic.ToBoolean) =>
        Value.Num(if call.arguments.headOption.exists(evalBool(_, state)) then 1.0 else 0.0)
      case call: BeFunctionCall if call.funcDef.intrinsic.exists(numericIntrinsics) =>
        val values = call.arguments.map(evalNumber(_, state))
        val value = values.headOption.getOrElse(0.0)
        Value.Num(call.funcDef.intrinsic.get match
          case BeIntrinsic.ToInteger => if value < 0 then math.ceil(value) else math.floor(value)
          case BeIntrinsic.Absolute => math.abs(value)
          case BeIntrinsic.Maximum => values.max
          case BeIntrinsic.Minimum => values.min
          case _ => value)
      case _ =>
        Value.Num(0.0)

  /** Text that spells a number counts as that number, as in Snap; other text cannot be drawn with. */
  private def number(value: Value): Double = value match
    case Value.Num(number) => number
    case Value.Text(text) => text.trim.toDoubleOption.getOrElse(
      throw IllegalArgumentException(s"The text \"$text\" is not a number."))

  private def text(value: Value): String = value match
    case Value.Text(text) => text
    case Value.Num(number) => if number == math.rint(number) && !number.isInfinite then number.toLong.toString else number.toString

  private def evalNumber(expression: BeExpression, state: InterpState): Double = number(eval(expression, state))

  /** Built-ins whose numeric result the drawing simulation computes; `int` truncates towards zero. */
  private val numericIntrinsics: Set[BeIntrinsic] =
    Set(BeIntrinsic.ToInteger, BeIntrinsic.ToNumber, BeIntrinsic.Absolute, BeIntrinsic.Maximum, BeIntrinsic.Minimum)

  private def evalOperator(call: BeFunctionCall, state: InterpState): Value = {
    val op = SnapControlFlow.operatorSymbol(call)
    val values = call.arguments.map(eval(_, state))
    def truth(condition: Boolean) = Value.Num(if condition then 1.0 else 0.0)
    def isText = values.exists(_.isInstanceOf[Value.Text])
    op match
      case "+" if isText => Value.Text(values.map(text).mkString)
      case "==" if isText => truth(values.sliding(2).forall(pair => pair.size < 2 || pair.head == pair(1)))
      case "!=" if isText => truth(values.size >= 2 && values.head != values(1))
      case "and" => truth(values.forall(truthy))
      case "or" => truth(values.exists(truthy))
      case "not" => truth(!values.headOption.exists(truthy))
      case _ => Value.Num(arithmetic(op, values.map(number)))
  }

  private def truthy(value: Value): Boolean = value match
    case Value.Num(number) => number != 0
    case Value.Text(text) => text.nonEmpty

  private def arithmetic(op: String, args: List[Double]): Double =
    op match
      case "+" => args.sum
      case "-" if args.size == 1 => -args.head
      case "-" => args.headOption.getOrElse(0.0) - args.drop(1).sum
      case "*" => args.product
      case "/" =>
        val denom = args.drop(1).headOption.getOrElse(1.0)
        if denom == 0 then throw ArithmeticException("Division by zero.") else args.headOption.getOrElse(0.0) / denom
      case "%" =>
        val dividend = args.headOption.getOrElse(0.0)
        val divisor = args.drop(1).headOption.getOrElse(1.0)
        if divisor == 0 then throw ArithmeticException("Remainder by zero.") else dividend - divisor * math.floor(dividend / divisor)
      case "//" =>
        val divisor = args.drop(1).headOption.getOrElse(1.0)
        if divisor == 0 then throw ArithmeticException("Division by zero.") else math.floor(args.headOption.getOrElse(0.0) / divisor)
      case "**" => math.pow(args.headOption.getOrElse(0.0), args.drop(1).headOption.getOrElse(1.0))
      case "<" => if compare(args, _ < _) then 1.0 else 0.0
      case ">" => if compare(args, _ > _) then 1.0 else 0.0
      case "==" => if args.sliding(2).forall(pair => pair.size < 2 || pair.head == pair(1)) then 1.0 else 0.0
      case "<=" => if compare(args, _ <= _) then 1.0 else 0.0
      case ">=" => if compare(args, _ >= _) then 1.0 else 0.0
      case "!=" => if args.size >= 2 && args.head != args(1) then 1.0 else 0.0
      case _ => 0.0

  private def compare(args: List[Double], op: (Double, Double) => Boolean): Boolean =
    args.sliding(2).forall(pair => pair.size < 2 || op(pair.head, pair(1)))

  private def evalBool(expression: BeExpression, state: InterpState): Boolean = truthy(eval(expression, state))

  private def isTruthy(value: String): Boolean = {
    val trimmed = value.trim
    trimmed.equalsIgnoreCase("True") || trimmed.equalsIgnoreCase("true") || trimmed == "1"
  }
}
