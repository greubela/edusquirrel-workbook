package it.evadid.vm.simulation

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.core.util.io.Serializer
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeFor, BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable, BeIntrinsic}
import it.evadid.vm.code.errors.BeSourcePlaceholder
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.{BeAssignVariable, BeCallTarget, BeFunctionCall, BeObjectExpression, BeUseValue}
import it.evadid.vm.conversion.snapexpression.{SnapControlFlow, SnapExpressionSupport}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.modules.{BeModule, BeModules, ModuleCommand}
import it.evadid.vm.simulation.data.{DataLibraries, DataWorld, ListCommand, SudokuCommand, SudokuGrid}
import it.evadid.vm.simulation.turtle.{TurtleCommandSites, TurtleExecutionLimits, TurtleExecutionStatus}
import it.evadid.vm.types.{BeDataValueLiteral, BeUseValueReference}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog
import upickle.default.ReadWriter

import scala.annotation.tailrec
import scala.util.control.NonFatal

/** Where execution starts: the program's startup code, or one function called with numeric arguments. */
enum ProgramEntry derives ReadWriter {
  case Startup
  case Call(function: String, arguments: Seq[Double] = Nil)
}

/**
 * Commands of one execution in order, plus the data afterwards. As for drawings, check `status`
 * before treating the commands as complete; a failure keeps the commands recorded until then.
 * `returnValue` is the numeric result of a called function (booleans are 1 and 0).
 */
final case class ProgramExecution(
    recorded: List[ModuleCommand],
    world: DataWorld,
    returnValue: Option[Double],
    status: TurtleExecutionStatus) derives ReadWriter {
  def completed: Boolean = status == TurtleExecutionStatus.Completed

  /** The calls of `module`'s functions in program order, typed as that module's commands. */
  def moduleCommands[C](module: BeModule[C]): List[C] = recorded.flatMap(module.command)

  /** Grading callers must not silently accept a partial execution. */
  def completedModuleCommands[C](module: BeModule[C]): List[C] = {
    require(completed, s"Program execution did not complete: $status.")
    moduleCommands(module)
  }
}

/**
 * The shared interpreter for the executable subset of `BeExpression`: numbers (booleans are 1 and 0)
 * and text, control flow, functions with parameters and return values, built-ins, turtle commands and
 * the `NumberList`/`Sudoku` libraries. Calls inside an expression run first, innermost and left to
 * right (`and`/`or` skip their right operand once the left one decides); functions update module
 * variables, while their parameters and locals end with the call.
 */
object BeProgramInterpreter {

  def run(expression: BeExpression, world: DataWorld = DataWorld(), entry: ProgramEntry = ProgramEntry.Startup,
      limits: TurtleExecutionLimits = TurtleExecutionLimits(), isCancelled: () => Boolean = () => false): ProgramExecution = {
    val initial = State.initial(expression, world)
    val finished = guarded(initial) {
      entry match {
        case ProgramEntry.Startup => interpret(expression, initial, limits, isCancelled)
        case ProgramEntry.Call(name, arguments) =>
          val wanted = BeEntityName.fromCodeString(name).universalInterpretation()
          val definition = initial.defs.values.find(_.functionTypeInfo.displayName.universalInterpretation() == wanted)
            .getOrElse(throw IllegalArgumentException(s"The program does not define $name."))
          if definition.inputs.size != arguments.size then
            throw IllegalArgumentException(s"$name expects ${definition.inputs.size} values, the test passes ${arguments.size}.")
          // Module variables are initialized by startup code, which a test call does not run.
          val (returned, after) = invoke(definition, arguments.map(Value.Num(_)).toList, initial, limits, isCancelled)
          after.copy(returned = Some(returned))
      }
    }
    ProgramExecution(finished.recorded.toList, finished.world, finished.returned.collect { case Value.Num(number) => number }, finished.status)
  }

  private val booleanOperators = Set("<", ">", "==", "<=", ">=", "!=", "and", "or", "not")

  /** Replaces operator and built-in calls whose inputs are all literals by their value, as the interpreter
    * computes it; other calls, e.g. to user functions, keep their folded arguments. `3 + 5` becomes `8`,
    * `add(3 + 5, 3)` becomes `add(8, 3)` and `2 < 3` becomes `True`.
    */
  def foldConstants(expression: BeExpression): BeExpression = expression match {
    case call: BeFunctionCall =>
      val folded = call.copy(parameterValueMap = call.parameterValueMap.map((parameter, value) => parameter -> foldConstants(value)))
      val computable = folded.isOperator || folded.funcDef.intrinsic.exists(intrinsic =>
        numericIntrinsics(intrinsic) || intrinsic == BeIntrinsic.ToText || intrinsic == BeIntrinsic.ToBoolean)
      val literalInputs = folded.arguments.nonEmpty && folded.arguments.forall {
        case BeUseValue(BeDataValueLiteral(_), _) => true
        case _ => false
      }
      if !computable || !literalInputs then folded
      else scala.util.Try(eval(folded, State.initial(folded, DataWorld()))).toOption.map {
        case Value.Num(number) if folded.isOperator && booleanOperators(SnapControlFlow.operatorSymbol(folded)) =>
          BeUseValue(BeDataValueLiteral(if number != 0 then "True" else "False"), None)
        case Value.Num(number) if number.isNaN || number.isInfinite => folded
        case Value.Num(number) => BeUseValue(BeDataValueLiteral(text(Value.Num(number))), None)
        case value: Value.Text => literal(value)
      }.getOrElse(folded)
    case other => other
  }

  /** The values programs compute with: numbers (booleans are 1 and 0) and text. */
  private enum Value {
    case Num(value: Double)
    case Text(value: String)
  }

  private final case class State(
      env: Map[String, Value],
      defs: Map[String, BeDefineFunction],
      world: DataWorld,
      /** Calls of module functions in program order; the counts bound each kind separately. */
      recorded: Vector[ModuleCommand] = Vector.empty,
      turtleCount: Int = 0,
      dataCount: Int = 0,
      steps: Int = 0,
      callDepth: Int = 0,
      status: TurtleExecutionStatus = TurtleExecutionStatus.Completed,
      /** Set by `return`: the rest of the function does not run, and its call produces this value. */
      returned: Option[Value] = None,
      /** Module variables: names assigned or declared outside every function call. */
      globals: Set[String] = Set.empty) {
    def running: Boolean = status == TurtleExecutionStatus.Completed && returned.isEmpty
    /** Binds `name`; at module level (outside calls) it becomes a module variable. */
    def bind(name: String, value: Value): State =
      copy(env = env.updated(name, value), globals = if callDepth == 0 then globals + name else globals)
  }

  private object State {
    def initial(expression: BeExpression, world: DataWorld): State = {
      val defs = TurtleCommandSites.nodes(expression).collect {
        case (_, definition: BeDefineFunction) => SnapExpressionSupport.pythonNameOf(definition) -> definition
      }.toMap
      State(Map.empty, defs, world)
    }
  }

  private def guarded(state: State)(body: => State): State =
    try body catch {
      case NonFatal(error) => state.copy(status = TurtleExecutionStatus.Failed(Option(error.getMessage).getOrElse(error.getClass.getSimpleName)))
    }

  // Statements

  private def interpret(expression: BeExpression, state: State, limits: TurtleExecutionLimits, isCancelled: () => Boolean): State =
    if !state.running then state
    else if isCancelled() then state.copy(status = TurtleExecutionStatus.Cancelled)
    else if state.steps >= limits.maxSteps then state.copy(status = TurtleExecutionStatus.LimitExceeded)
    else guarded(state)(interpretNode(expression, state.copy(steps = state.steps + 1), limits, isCancelled))

  private def interpretNode(expression: BeExpression, state: State, limits: TurtleExecutionLimits, isCancelled: () => Boolean): State =
    expression match {
      case BeStartProgram(Some(seq)) => interpret(seq, state, limits, isCancelled)
      case BeStartProgram(None) => state
      case seq: BeSequence => seq.body.foldLeft(state)((current, child) => interpret(child, current, limits, isCancelled))
      case _: BeObjectExpression => unsupportedObjectExecution()
      case _: BeDefineFunction => state
      // A declared variable exists before it is assigned (Java fields and Python `x: float` start at 0); an initializer sets it.
      case variable: BeDefineVariable =>
        variable.initValue match {
          case Some(value) =>
            val (resolved, evaluated) = resolveCalls(value, state, limits, isCancelled)
            evaluated.bind(SnapControlFlow.variableName(variable), eval(resolved, evaluated))
          case None =>
            val name = SnapControlFlow.variableName(variable)
            state.bind(name, state.env.getOrElse(name, Value.Num(0.0)))
        }
      case assign: BeAssignVariable =>
        val (value, evaluated) = resolveCalls(assign.value, state, limits, isCancelled)
        evaluated.bind(SnapControlFlow.variableName(assign.target), eval(value, evaluated))
      case BeReturn(value) =>
        val (resolved, evaluated) = value.fold((Option.empty[BeExpression], state))(value =>
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

  /** Unfinished or unsupported source stops the run rather than being skipped. */
  private def cannotRun(placeholder: BeSourcePlaceholder): Nothing =
    throw IllegalArgumentException(s"This part of the program cannot run: ${placeholder.originalSource.trim.linesIterator.nextOption().getOrElse("")}")

  @tailrec private def repeatBody(body: BeExpression, remaining: Int, state: State,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): State =
    if remaining <= 0 || !state.running then state
    else repeatBody(body, remaining - 1, interpret(body, state, limits, isCancelled), limits, isCancelled)

  @tailrec private def forBody(loop: BeFor, index: Long, end: Long, step: Long, state: State,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): State =
    if !state.running || (if step > 0 then index > end else index < end) then state
    else {
      val bound = state.bind(SnapControlFlow.variableName(loop.variable), Value.Num(index.toDouble))
      forBody(loop, index + step, end, step, interpret(loop.body, bound, limits, isCancelled), limits, isCancelled)
    }

  @tailrec private def whileBody(loop: BeWhile, iterations: Int, state: State,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): State =
    if !state.running then state
    else {
      val (condition, evaluated) = resolveCalls(loop.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("False"), None)),
        state, limits, isCancelled)
      if !evaluated.running || !evalBool(condition, evaluated) then evaluated
      else if iterations >= limits.maxLoopIterations then evaluated.copy(status = TurtleExecutionStatus.LimitExceeded)
      else whileBody(loop, iterations + 1, interpret(loop.body, evaluated, limits, isCancelled), limits, isCancelled)
    }

  // Calls

  private def interpretCall(unresolved: BeFunctionCall, before: State, limits: TurtleExecutionLimits, isCancelled: () => Boolean): State = {
    val library = isDataModule(unresolved.funcDef)
    if !unresolved.target.isInstanceOf[BeCallTarget.Function] && !TurtleCommand.isTurtleMethod(unresolved.funcDef) && !library then
      unsupportedObjectExecution()
    else if unresolved.isOperator then before
    else {
      val (call, state) = resolveArguments(unresolved, before, limits, isCancelled)
      if library then dataOperation(call.funcDef, call.arguments.map(evalNumber(_, state)), state, limits)._2
      else userDefinition(call, state) match {
        case Some(definition) => invoke(definition, call.arguments.map(eval(_, state)), state, limits, isCancelled)._2
        case None => turtleCommand(call, state, limits)
      }
    }
  }

  private def turtleCommand(call: BeFunctionCall, state: State, limits: TurtleExecutionLimits): State = {
    val python = SnapExpressionSupport.pythonName(call)
    val name = SnapTurtleCatalog.turtleCommandByPythonName.getOrElse(python, python)
    val command = TurtleCommand.fromCall(BeEntityName.fromCodeString(name),
      call.arguments.flatMap(numericArg(_, state)), call.arguments.flatMap(stringArg))
    if !command.args.forall(_.isFinite) then state.copy(status = TurtleExecutionStatus.Failed("A drawing command needs finite numbers."))
    else if state.turtleCount >= limits.maxCommands then state.copy(status = TurtleExecutionStatus.LimitExceeded)
    else state.copy(recorded = state.recorded :+ ModuleCommand.Turtle(command), turtleCount = state.turtleCount + 1)
  }

  private def userDefinition(call: BeFunctionCall, state: State): Option[BeDefineFunction] =
    if call.isOperator || !call.target.isInstanceOf[BeCallTarget.Function] || TurtleCommand.isTurtleMethod(call.funcDef) ||
      isDataModule(call.funcDef) then None
    else state.defs.get(SnapExpressionSupport.pythonName(call))

  /** Runs one of the program's own functions on argument values; returns what it returned. */
  private def invoke(definition: BeDefineFunction, arguments: List[Value], state: State, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): (Value, State) =
    if state.callDepth >= limits.maxCallDepth then (Value.Num(0.0), state.copy(status = TurtleExecutionStatus.LimitExceeded))
    else {
      val bound = definition.inputs.zip(arguments).map((parameter, value) => SnapControlFlow.variableName(parameter) -> value).toMap
      val child = state.copy(env = state.env ++ bound, callDepth = state.callDepth + 1)
      val after = interpret(definition.body, child, limits, isCancelled)
      // A function updates module variables; its parameters and own locals end with the call, so a recursive
      // call cannot change its caller's locals (such as loop counters).
      val updated = after.env.filter((name, _) => state.globals.contains(name) && !bound.contains(name))
      (after.returned.getOrElse(Value.Num(0.0)), after.copy(env = state.env ++ updated, callDepth = state.callDepth, returned = None))
    }

  /** Runs the calls inside an expression, innermost and left to right, and replaces each by its value; drawing and
    * data operations they perform happen before the expression's own statement. `and`/`or` stop once decided.
    */
  private def resolveCalls(expression: BeExpression, state: State, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): (BeExpression, State) =
    if state.status != TurtleExecutionStatus.Completed then (expression, state)
    else expression match {
      case call: BeFunctionCall if call.isOperator && call.arguments.size == 2 &&
          Set("and", "or")(SnapControlFlow.operatorSymbol(call)) =>
        val isAnd = SnapControlFlow.operatorSymbol(call) == "and"
        val (left, afterLeft) = resolveCalls(call.arguments.head, state, limits, isCancelled)
        val leftTrue = evalBool(left, afterLeft)
        if leftTrue != isAnd then (literal(Value.Num(if leftTrue then 1.0 else 0.0)), afterLeft)
        else {
          val (right, afterRight) = resolveCalls(call.arguments(1), afterLeft, limits, isCancelled)
          (literal(Value.Num(if evalBool(right, afterRight) then 1.0 else 0.0)), afterRight)
        }
      case call: BeFunctionCall =>
        val (resolved, evaluated) = resolveArguments(call, state, limits, isCancelled)
        if isDataModule(resolved.funcDef) then {
          val (value, after) = dataOperation(resolved.funcDef, resolved.arguments.map(evalNumber(_, evaluated)), evaluated, limits)
          (literal(Value.Num(value)), after)
        } else userDefinition(resolved, evaluated) match {
          case Some(definition) =>
            val (value, after) = invoke(definition, resolved.arguments.map(eval(_, evaluated)), evaluated, limits, isCancelled)
            (literal(value), after)
          case None => (resolved, evaluated)
        }
      case sequence: BeSequence =>
        val (body, evaluated) = resolveAll(sequence.body.toList, state, limits, isCancelled)
        (sequence.copy(body = body), evaluated)
      case other => (other, state)
    }

  private def resolveArguments(call: BeFunctionCall, state: State, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): (BeFunctionCall, State) = {
    val parameters = call.funcDef.inputs.filter(call.parameterValueMap.contains)
    val (values, evaluated) = resolveAll(parameters.map(call.parameterValueMap), state, limits, isCancelled)
    (call.copy(parameterValueMap = call.parameterValueMap ++ parameters.zip(values)), evaluated)
  }

  private def resolveAll(expressions: List[BeExpression], state: State, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): (List[BeExpression], State) =
    expressions.foldLeft((List.empty[BeExpression], state)) { case ((done, current), expression) =>
      val (resolved, next) = resolveCalls(expression, current, limits, isCancelled)
      (done :+ resolved, next)
    }

  private def literal(value: Value): BeExpression = value match
    case Value.Num(number) => BeUseValue(BeDataValueLiteral(number.toString), None)
    case Value.Text(text) => BeUseValue(BeDataValueLiteral(Serializer.stringLiteralIO(parseEverything = true).serialize(text)), None)

  private def unsupportedObjectExecution(): Nothing =
    throw UnsupportedOperationException("The program simulator does not support object operations.")

  // Expressions (calls are already resolved to values)

  private def numericArg(expression: BeExpression, state: State): Option[Double] =
    expression match
      case _: BeObjectExpression => unsupportedObjectExecution()
      case call: BeFunctionCall if !call.target.isInstanceOf[BeCallTarget.Function] && !TurtleCommand.isTurtleMethod(call.funcDef) => unsupportedObjectExecution()
      case BeUseValue(BeDataValueLiteral(value), _) => numericLiteral(value)
      case BeUseValue(BeUseValueReference(_), _) => Some(evalNumber(expression, state))
      case call: BeFunctionCall if call.isOperator || call.funcDef.intrinsic.exists(numericIntrinsics) => Some(evalNumber(call, state))
      case placeholder: BeSourcePlaceholder => cannotRun(placeholder)
      case _ => None

  private def stringArg(expression: BeExpression): Option[String] =
    expression match
      case BeUseValue(BeDataValueLiteral(value), _) if numericLiteral(value).isEmpty =>
        Some(Serializer.stringLiteralIO(parseEverything = true).deserialize(value))
      case _ => None

  private def numericLiteral(value: String): Option[Double] =
    if value.exists(ch => ch.isDigit || ch == '.' || ch == '-') then value.trim.toDoubleOption else None

  private def eval(expression: BeExpression, state: State): Value =
    expression match
      case _: BeObjectExpression => unsupportedObjectExecution()
      case call: BeFunctionCall if !call.target.isInstanceOf[BeCallTarget.Function] && !TurtleCommand.isTurtleMethod(call.funcDef) => unsupportedObjectExecution()
      case BeUseValue(BeDataValueLiteral(value), _) =>
        value.trim.toDoubleOption.map(Value.Num(_))
          .orElse(stringArg(expression).filterNot(_ => isTruthy(value) || value.trim.equalsIgnoreCase("false")).map(Value.Text(_)))
          .getOrElse(Value.Num(if isTruthy(value) then 1.0 else 0.0))
      case BeUseValue(BeUseValueReference(variable), _) => state.env.getOrElse(SnapControlFlow.variableName(variable), Value.Num(0.0))
      case seq: BeSequence => seq.body.lastOption.map(eval(_, state)).getOrElse(Value.Num(0.0))
      case call: BeFunctionCall if call.isOperator => evalOperator(call, state)
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
      case _ => Value.Num(0.0)

  /** Text that spells a number counts as that number, as in Snap; other text cannot be computed with. */
  private def number(value: Value): Double = value match
    case Value.Num(number) => number
    case Value.Text(text) => text.trim.toDoubleOption.getOrElse(throw IllegalArgumentException(s"The text \"$text\" is not a number."))

  private def text(value: Value): String = value match
    case Value.Text(text) => text
    case Value.Num(number) => if number == math.rint(number) && !number.isInfinite then number.toLong.toString else number.toString

  private def evalNumber(expression: BeExpression, state: State): Double = number(eval(expression, state))

  /** Built-ins whose numeric result the interpreter computes; `int` truncates towards zero. */
  private val numericIntrinsics: Set[BeIntrinsic] =
    Set(BeIntrinsic.ToInteger, BeIntrinsic.ToNumber, BeIntrinsic.Absolute, BeIntrinsic.Maximum, BeIntrinsic.Minimum)

  private def evalOperator(call: BeFunctionCall, state: State): Value = {
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

  private def evalBool(expression: BeExpression, state: State): Boolean = truthy(eval(expression, state))

  private def isTruthy(value: String): Boolean = {
    val trimmed = value.trim
    trimmed.equalsIgnoreCase("true") || trimmed == "1"
  }

  // Data libraries

  private def isDataModule(definition: BeDefineFunction): Boolean =
    BeModules.of(definition).exists(module => module == BeModules.NumberList || module == BeModules.Sudoku)

  private def whole(value: Double, what: String): Int =
    if value.isWhole && value.abs <= Int.MaxValue then value.toInt
    else throw IllegalArgumentException(s"$what must be a whole number, not $value.")

  private def dataOperation(definition: BeDefineFunction, values: List[Double], state: State, limits: TurtleExecutionLimits): (Double, State) =
    if state.dataCount >= limits.maxDataCommands then (0.0, state.copy(status = TurtleExecutionStatus.LimitExceeded))
    else definition.intrinsic match {
      case Some(BeIntrinsic.NumberList) => listOperation(DataLibraries.methodName(definition), values, state)
      case Some(BeIntrinsic.Sudoku) => sudokuOperation(DataLibraries.methodName(definition), values, state)
      case other => throw IllegalStateException(s"Not a data operation: $other")
    }

  private def listOperation(method: String, values: List[Double], state: State): (Double, State) = {
    val list = state.world.list
    def index(value: Double) = {
      val position = whole(value, "A list position")
      if position < 0 || position >= list.size then
        throw IllegalArgumentException(s"Position $position is outside the list (positions 0 to ${list.size - 1}).")
      position
    }
    def record(command: ListCommand, changed: Vector[Int] = list) = state.copy(recorded = state.recorded :+ ModuleCommand.NumberList(command),
      dataCount = state.dataCount + 1, world = state.world.copy(list = changed))
    (method, values) match {
      case ("get", List(position)) =>
        val i = index(position)
        (list(i).toDouble, record(ListCommand.Get(i, list(i))))
      case ("set", List(position, value)) =>
        val i = index(position)
        val v = whole(value, "A list value")
        (0.0, record(ListCommand.Set(i, v), list.updated(i, v)))
      case ("swap", List(first, second)) =>
        val (i, j) = (index(first), index(second))
        (0.0, record(ListCommand.Swap(i, j), list.updated(i, list(j)).updated(j, list(i))))
      case ("length", Nil) => (list.size.toDouble, record(ListCommand.Length(list.size)))
      case _ => throw IllegalArgumentException(s"NumberList.$method does not take ${values.size} values.")
    }
  }

  private def sudokuOperation(method: String, values: List[Double], state: State): (Double, State) = {
    val grid = state.world.sudoku
    def coordinate(value: Double, what: String) = {
      val result = whole(value, what)
      if result < 0 || result > 8 then throw IllegalArgumentException(s"$what $result is outside the sudoku (0 to 8).")
      result
    }
    def changeable(row: Int, column: Int): Unit =
      if grid.givens(row * 9 + column) then
        throw IllegalArgumentException(s"Row $row, column $column holds a given number and cannot be changed.")
    def record(command: SudokuCommand, changed: SudokuGrid = grid) = state.copy(recorded = state.recorded :+ ModuleCommand.Sudoku(command),
      dataCount = state.dataCount + 1, world = state.world.copy(sudoku = changed))
    (method, values) match {
      case ("get", List(r, c)) =>
        val (row, column) = (coordinate(r, "Row"), coordinate(c, "Column"))
        (grid(row, column).toDouble, record(SudokuCommand.Get(row, column, grid(row, column))))
      case ("set", List(r, c, v)) =>
        val (row, column) = (coordinate(r, "Row"), coordinate(c, "Column"))
        val value = whole(v, "A sudoku value")
        if value < 1 || value > 9 then throw IllegalArgumentException(s"A sudoku cell holds 1 to 9, not $value.")
        changeable(row, column)
        (0.0, record(SudokuCommand.Set(row, column, value), grid.updated(row, column, value)))
      case ("remove", List(r, c)) =>
        val (row, column) = (coordinate(r, "Row"), coordinate(c, "Column"))
        changeable(row, column)
        (0.0, record(SudokuCommand.Remove(row, column), grid.updated(row, column, 0)))
      case _ => throw IllegalArgumentException(s"Sudoku.$method does not take ${values.size} values.")
    }
  }
}
