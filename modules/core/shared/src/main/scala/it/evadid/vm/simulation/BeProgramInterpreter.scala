package it.evadid.vm.simulation

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.core.util.io.Serializer
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeFor, BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable, BeIntrinsic}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.{BeAssignVariable, BeCallTarget, BeFunctionCall, BeObjectExpression, BeUseValue}
import it.evadid.vm.conversion.snapexpression.{SnapControlFlow, SnapExpressionSupport}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.simulation.data.{DataLibraries, DataWorld, ListCommand, SudokuCommand}
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
 */
final case class ProgramExecution(
    turtleCommands: List[TurtleCommand[Double]],
    listCommands: List[ListCommand],
    sudokuCommands: List[SudokuCommand],
    world: DataWorld,
    returnValue: Option[Double],
    status: TurtleExecutionStatus) derives ReadWriter {
  def completed: Boolean = status == TurtleExecutionStatus.Completed

  /** Grading callers must not silently accept a partial execution. */
  def completedListCommands: List[ListCommand] = { requireCompleted(); listCommands }
  def completedSudokuCommands: List[SudokuCommand] = { requireCompleted(); sudokuCommands }
  def completedTurtleCommands: List[TurtleCommand[Double]] = { requireCompleted(); turtleCommands }
  private def requireCompleted(): Unit = require(completed, s"Program execution did not complete: $status.")
}

/**
 * The shared interpreter for the executable subset of `BeExpression`: numbers and booleans (as 1/0),
 * control flow, functions with parameters and return values, turtle commands and the data libraries.
 * Functions see their parameters and the caller's variables; their assignments stay local.
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
          callFunction(definition, arguments.toList, initial, limits, isCancelled)
      }
    }
    ProgramExecution(finished.commands.toList, finished.listCommands.toList, finished.sudokuCommands.toList,
      finished.world, finished.returned, finished.status)
  }

  private final case class State(
      env: Map[String, Double],
      defs: Map[String, BeDefineFunction],
      world: DataWorld,
      commands: Vector[TurtleCommand[Double]] = Vector.empty,
      listCommands: Vector[ListCommand] = Vector.empty,
      sudokuCommands: Vector[SudokuCommand] = Vector.empty,
      returned: Option[Double] = None,
      steps: Int = 0,
      callDepth: Int = 0,
      status: TurtleExecutionStatus = TurtleExecutionStatus.Completed) {
    def running: Boolean = status == TurtleExecutionStatus.Completed && returned.isEmpty
    def dataCommandCount: Int = listCommands.size + sudokuCommands.size
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
    else guarded(state)(statement(expression, state.copy(steps = state.steps + 1), limits, isCancelled))

  private def statement(expression: BeExpression, state: State, limits: TurtleExecutionLimits, isCancelled: () => Boolean): State =
    expression match {
      case BeStartProgram(Some(seq)) => interpret(seq, state, limits, isCancelled)
      case BeStartProgram(None) => state
      case seq: BeSequence => seq.body.foldLeft(state)((current, child) => interpret(child, current, limits, isCancelled))
      case _: BeObjectExpression => unsupportedObjectExecution()
      case _: BeDefineFunction => state
      case declaration: BeDefineVariable =>
        declaration.initValue.fold(state) { value =>
          val (result, next) = evaluate(value, state, limits, isCancelled)
          next.copy(env = next.env.updated(SnapControlFlow.variableName(declaration), result))
        }
      case assign: BeAssignVariable =>
        val (result, next) = evaluate(assign.value, state, limits, isCancelled)
        next.copy(env = next.env.updated(SnapControlFlow.variableName(assign.target), result))
      case BeReturn(value) =>
        val (result, next) = value.fold((0.0, state))(evaluate(_, state, limits, isCancelled))
        next.copy(returned = Some(result))
      case repeat: BeRepeatNr => repeatBody(repeat.body, repeat.amount, state, limits, isCancelled)
      case loop: BeFor =>
        val (startValue, afterStart) = evaluate(loop.start, state, limits, isCancelled)
        val (endValue, afterEnd) = evaluate(loop.end, afterStart, limits, isCancelled)
        // Ascending only (see BeFor): a start beyond the end runs the body zero times.
        forBody(loop, startValue.round, endValue.round, afterEnd, limits, isCancelled)
      case branch: BeIfElse =>
        val (condition, next) = truth(branch.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("True"), None)), state, limits, isCancelled)
        interpret(if condition then branch.thenBody else branch.elseBody, next, limits, isCancelled)
      case loop: BeWhile => whileBody(loop, 0, state, limits, isCancelled)
      case call: BeFunctionCall => evaluate(call, state, limits, isCancelled)._2
      case _ => state
    }

  @tailrec private def repeatBody(body: BeExpression, remaining: Int, state: State,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): State =
    if remaining <= 0 || !state.running then state
    else repeatBody(body, remaining - 1, interpret(body, state, limits, isCancelled), limits, isCancelled)

  @tailrec private def forBody(loop: BeFor, index: Long, end: Long, state: State,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): State =
    if !state.running || index > end then state
    else {
      val bound = state.copy(env = state.env.updated(SnapControlFlow.variableName(loop.variable), index.toDouble))
      forBody(loop, index + 1, end, interpret(loop.body, bound, limits, isCancelled), limits, isCancelled)
    }

  @tailrec private def whileBody(loop: BeWhile, iterations: Int, state: State,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): State = {
    val condition = loop.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("False"), None))
    val (holds, next) = if state.running then truth(condition, state, limits, isCancelled) else (false, state)
    if !holds || !next.running then next
    else if iterations >= limits.maxLoopIterations then next.copy(status = TurtleExecutionStatus.LimitExceeded)
    else whileBody(loop, iterations + 1, interpret(loop.body, next, limits, isCancelled), limits, isCancelled)
  }

  // Expressions: every evaluation threads the state, because calls can draw, change data or fail.

  private def evaluate(expression: BeExpression, state: State, limits: TurtleExecutionLimits, isCancelled: () => Boolean): (Double, State) =
    if state.status != TurtleExecutionStatus.Completed then (0.0, state)
    else expression match {
      case _: BeObjectExpression => unsupportedObjectExecution()
      case BeUseValue(BeDataValueLiteral(value), _) =>
        (value.trim.toDoubleOption.getOrElse(if isTruthy(value) then 1.0 else 0.0), state)
      case BeUseValue(BeUseValueReference(variable), _) => (state.env.getOrElse(SnapControlFlow.variableName(variable), 0.0), state)
      case seq: BeSequence => seq.body.lastOption.fold((0.0, state))(evaluate(_, state, limits, isCancelled))
      case call: BeFunctionCall if SnapControlFlow.isOperatorCall(call) => operator(call, state, limits, isCancelled)
      case call: BeFunctionCall => functionCall(call, state, limits, isCancelled)
      case _ => (0.0, state)
    }

  private def evaluateAll(arguments: List[BeExpression], state: State, limits: TurtleExecutionLimits,
      isCancelled: () => Boolean): (List[Double], State) =
    arguments.foldLeft((List.empty[Double], state)) { case ((values, current), argument) =>
      val (value, next) = evaluate(argument, current, limits, isCancelled)
      (values :+ value, next)
    }

  private def truth(expression: BeExpression, state: State, limits: TurtleExecutionLimits, isCancelled: () => Boolean): (Boolean, State) = {
    val (value, next) = evaluate(expression, state, limits, isCancelled)
    (value != 0.0, next)
  }

  private def operator(call: BeFunctionCall, state: State, limits: TurtleExecutionLimits, isCancelled: () => Boolean): (Double, State) = {
    val arguments = SnapControlFlow.orderedArgs(call)
    def bool(value: Boolean) = if value then 1.0 else 0.0
    SnapControlFlow.operatorSymbol(call) match {
      // Short-circuit like Java and Python: the right operand may call functions.
      case "and" | "or" if arguments.size == 2 =>
        val isAnd = SnapControlFlow.operatorSymbol(call) == "and"
        val (left, afterLeft) = truth(arguments.head, state, limits, isCancelled)
        if left != isAnd then (bool(left), afterLeft)
        else { val (right, afterRight) = truth(arguments(1), afterLeft, limits, isCancelled); (bool(right), afterRight) }
      case symbol =>
        val (args, next) = evaluateAll(arguments, state, limits, isCancelled)
        val result = symbol match {
          case "+" => args.sum
          case "-" if args.size == 1 => -args.head
          case "-" => args.headOption.getOrElse(0.0) - args.drop(1).sum
          case "*" => args.product
          case "/" =>
            val denominator = args.drop(1).headOption.getOrElse(1.0)
            if denominator == 0 then throw ArithmeticException("Division by zero.") else args.headOption.getOrElse(0.0) / denominator
          case "//" =>
            val denominator = args.drop(1).headOption.getOrElse(1.0)
            if denominator == 0 then throw ArithmeticException("Division by zero.") else math.floor(args.headOption.getOrElse(0.0) / denominator)
          case "%" =>
            val dividend = args.headOption.getOrElse(0.0)
            val divisor = args.drop(1).headOption.getOrElse(1.0)
            if divisor == 0 then throw ArithmeticException("Remainder by zero.") else dividend - divisor * math.floor(dividend / divisor)
          case "<" => bool(compare(args, _ < _))
          case ">" => bool(compare(args, _ > _))
          case "==" => bool(compare(args, _ == _))
          case "<=" => bool(compare(args, _ <= _))
          case ">=" => bool(compare(args, _ >= _))
          case "!=" => bool(args.size >= 2 && args.head != args(1))
          case "and" => bool(args.forall(_ != 0))
          case "or" => bool(args.exists(_ != 0))
          case "not" => bool(args.headOption.getOrElse(0.0) == 0)
          case _ => 0.0
        }
        (result, next)
    }
  }

  private def compare(args: List[Double], op: (Double, Double) => Boolean): Boolean =
    args.sliding(2).forall(pair => pair.size < 2 || op(pair.head, pair(1)))

  private def functionCall(call: BeFunctionCall, state: State, limits: TurtleExecutionLimits, isCancelled: () => Boolean): (Double, State) = {
    val turtle = TurtleCommand.isTurtleMethod(call.funcDef)
    val data = DataLibraries.isDataMethod(call.funcDef)
    if !call.target.isInstanceOf[BeCallTarget.Function] && !turtle && !data then unsupportedObjectExecution()
    val arguments = SnapControlFlow.orderedArgs(call)
    val python = SnapExpressionSupport.pythonName(call)
    if data then {
      val (values, next) = evaluateAll(arguments, state, limits, isCancelled)
      dataOperation(call.funcDef, values, next, limits)
    } else state.defs.get(python).filterNot(_ => turtle) match {
      case Some(definition) =>
        val (values, next) = evaluateAll(arguments, state, limits, isCancelled)
        val returned = callFunction(definition, values, next, limits, isCancelled)
        (returned.returned.getOrElse(0.0), returned.copy(returned = None))
      case None => (0.0, turtleCommand(python, arguments, state, limits, isCancelled))
    }
  }

  /** Runs a definition; the result keeps the callee's `returned` value and the caller's variables. */
  private def callFunction(definition: BeDefineFunction, values: List[Double], state: State,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): State =
    if state.callDepth >= limits.maxCallDepth then state.copy(status = TurtleExecutionStatus.LimitExceeded)
    else {
      val bound = definition.inputs.zip(values).map((parameter, value) => SnapControlFlow.variableName(parameter) -> value)
      val child = state.copy(env = state.env ++ bound, callDepth = state.callDepth + 1, returned = None)
      interpret(definition.body, child, limits, isCancelled).copy(env = state.env, callDepth = state.callDepth)
    }

  private def turtleCommand(python: String, arguments: List[BeExpression], state: State,
      limits: TurtleExecutionLimits, isCancelled: () => Boolean): State = {
    val name = SnapTurtleCatalog.turtleCommandByPythonName.getOrElse(python, python)
    // Non-numeric literals (colors, stitch flags) stay text, as before; every other argument is evaluated.
    val (numbers, next) = arguments.foldLeft((List.empty[Double], state)) { case ((values, current), argument) =>
      stringArg(argument) match {
        case Some(_) => (values, current)
        case None =>
          val (value, after) = evaluate(argument, current, limits, isCancelled)
          (values :+ value, after)
      }
    }
    val command = TurtleCommand.fromCall(BeEntityName.fromCodeString(name), numbers, arguments.flatMap(stringArg))
    if !next.running then next
    else if !command.args.forall(_.isFinite) then next.copy(status = TurtleExecutionStatus.Failed("A drawing command needs finite numbers."))
    else if next.commands.size >= limits.maxCommands then next.copy(status = TurtleExecutionStatus.LimitExceeded)
    else next.copy(commands = next.commands :+ command)
  }

  private def stringArg(expression: BeExpression): Option[String] = expression match {
    case BeUseValue(BeDataValueLiteral(value), _) if numericLiteral(value).isEmpty =>
      Some(Serializer.stringLiteralIO(parseEverything = true).deserialize(value))
    case _ => None
  }

  private def numericLiteral(value: String): Option[Double] =
    if value.exists(ch => ch.isDigit || ch == '.' || ch == '-') then value.trim.toDoubleOption else None

  private def isTruthy(value: String): Boolean = {
    val trimmed = value.trim
    trimmed.equalsIgnoreCase("true") || trimmed == "1"
  }

  // Data libraries

  private def whole(value: Double, what: String): Int =
    if value.isWhole && value.abs <= Int.MaxValue then value.toInt
    else throw IllegalArgumentException(s"$what must be a whole number, not $value.")

  private def dataOperation(definition: BeDefineFunction, values: List[Double], state: State, limits: TurtleExecutionLimits): (Double, State) =
    if state.dataCommandCount >= limits.maxDataCommands then (0.0, state.copy(status = TurtleExecutionStatus.LimitExceeded))
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
    def record(command: ListCommand, changed: Vector[Int] = list) =
      state.copy(listCommands = state.listCommands :+ command, world = state.world.copy(list = changed))
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
    def record(command: SudokuCommand, changed: it.evadid.vm.simulation.data.SudokuGrid = grid) =
      state.copy(sudokuCommands = state.sudokuCommands :+ command, world = state.world.copy(sudoku = changed))
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

  private def unsupportedObjectExecution(): Nothing =
    throw UnsupportedOperationException("The program simulator does not support object operations.")
}
