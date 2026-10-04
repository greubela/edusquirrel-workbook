package it.evadid.vm.simulation.java

import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E}
import scala.collection.mutable

object JavaTurtleRuntime {
  enum Failure {
    case InvalidInvocation, InvalidLimits
    case Evaluation(problem: E.Failure)
  }

  enum Status {
    case Completed, LimitExceeded, Cancelled
    case Failed(problem: Failure)
  }

  case class Command(command: R.TurtleCommand, value: Int)
  case class Execution(status: Status, commands: Vector[Command], steps: Int)

  object Limits {
    val MaxSteps = 100000
    val MaxCommands = 10000
    val MaxCallDepth = 64
    val MaxBlockDepth = 64
  }

  case class Limits(maxSteps: Int = Limits.MaxSteps, maxCommands: Int = Limits.MaxCommands,
      maxCallDepth: Int = Limits.MaxCallDepth, maxBlockDepth: Int = Limits.MaxBlockDepth) {
    private[JavaTurtleRuntime] def valid: Boolean =
      maxSteps > 0 && maxSteps <= Limits.MaxSteps && maxCommands >= 0 && maxCommands <= Limits.MaxCommands &&
        maxCallDepth > 0 && maxCallDepth <= Limits.MaxCallDepth &&
        maxBlockDepth > 0 && maxBlockDepth <= Limits.MaxBlockDepth
  }

  def run(source: R.ResolvedSource, limits: Limits = Limits(),
      isCancelled: () => Boolean = () => false): Execution =
    execute(source.methods, source.entryPoint, source.entryPoint, Vector.empty, true, limits, isCancelled)

  def invoke(source: R.ResolvedSource, method: R.MethodId, arguments: Vector[E.Value],
      limits: Limits = Limits(), isCancelled: () => Boolean = () => false): Execution =
    execute(source.methods, source.entryPoint, method, arguments, false, limits, isCancelled)

  def runVm(program: P.Program, limits: Limits = Limits(),
      isCancelled: () => Boolean = () => false): Execution =
    execute(program.resolvedMethods, program.root.entryPoint.binding.id, program.root.entryPoint.binding.id,
      Vector.empty, true, limits, isCancelled)

  def invokeVm(program: P.Program, method: R.MethodId, arguments: Vector[E.Value],
      limits: Limits = Limits(), isCancelled: () => Boolean = () => false): Execution =
    execute(program.resolvedMethods, program.root.entryPoint.binding.id, method, arguments, false, limits, isCancelled)

  private def execute(methods: Vector[R.Method], entryPoint: R.MethodId, method: R.MethodId, arguments: Vector[E.Value],
      main: Boolean, limits: Limits, isCancelled: () => Boolean): Execution =
    if !limits.valid then Execution(Status.Failed(Failure.InvalidLimits), Vector.empty, 0)
    else new Runner(methods, entryPoint, limits, isCancelled).execute(method, arguments, main)

  private type Result[A] = Either[Status, A]
  private type Scope = mutable.Set[R.VariableId]

  private enum Action {
    case EnterBlock(block: R.Block, depth: Int)
    case Sequence(statements: Vector[R.Statement], index: Int, depth: Int)
    case Statement(statement: R.Statement, depth: Int)
    case ExitScope(scope: Scope)
    case WhileTest(loop: R.While, depth: Int)
    case ForTest(loop: R.For, depth: Int)
  }

  private class Frame(arguments: Map[R.VariableId, E.Value], body: R.Block) {
    val values: mutable.Map[R.VariableId, E.Value] = mutable.Map.from(arguments)
    var scopes: List[Scope] = Nil
    var pending: List[Action] = List(Action.EnterBlock(body, 1))

    val read: E.Reader = variable => values.get(variable.id).toRight(E.Failure.MissingValue(variable.id))

    def schedule(actions: Action*): Unit = pending = actions.toList ::: pending
  }

  private class Runner(definitions: Vector[R.Method], entryPoint: R.MethodId, limits: Limits, isCancelled: () => Boolean) {
    private val methods = definitions.map(method => method.id -> method).toMap
    private val commands = mutable.ArrayBuffer.empty[Command]
    private var frames = List.empty[Frame]
    private var used = 0

    def execute(method: R.MethodId, arguments: Vector[E.Value], main: Boolean): Execution = {
      var result = start(method, arguments, main)
      while result.isRight && frames.nonEmpty do {
        val frame = frames.head
        frame.pending match {
          case Nil => frames = frames.tail
          case action :: rest =>
            frame.pending = rest
            result = dispatch(frame, action)
        }
      }
      Execution(result.fold(identity, _ => Status.Completed), commands.toVector, used)
    }

    private def gate(): Either[E.Failure, Unit] =
      if isCancelled() then Left(E.Failure.Cancelled)
      else if used >= limits.maxSteps then Left(E.Failure.LimitExceeded)
      else { used += 1; Right(()) }

    private def status(problem: E.Failure): Status = problem match {
      case E.Failure.LimitExceeded => Status.LimitExceeded
      case E.Failure.Cancelled => Status.Cancelled
      case _ => Status.Failed(Failure.Evaluation(problem))
    }

    private def step(): Result[Unit] = gate().left.map(status)

    private def evaluate(frame: Frame, expression: R.Expression): Result[E.Value] =
      E.evaluateWithGate(expression, frame.read, E.Limits(), () => gate()).left.map(status)

    private def condition(frame: Frame, expression: R.Expression): Result[Boolean] =
      evaluate(frame, expression).flatMap {
        case E.Value.BooleanValue(value) => Right(value)
        case _ => Left(status(E.Failure.TypeMismatch))
      }

    private def start(id: R.MethodId, arguments: Vector[E.Value], main: Boolean = false): Result[Unit] =
      methods.get(id).toRight(Status.Failed(Failure.InvalidInvocation)).flatMap { method =>
        val valid = if main then id == entryPoint && arguments.isEmpty
          else id != entryPoint && method.parameters.size == arguments.size &&
            method.parameters.zip(arguments).forall((parameter, value) => matches(parameter, value))
        if !valid then Left(Status.Failed(Failure.InvalidInvocation))
        else step().flatMap { _ =>
          if frames.size >= limits.maxCallDepth then Left(Status.LimitExceeded)
          else {
            val bindings = if main then Map.empty[R.VariableId, E.Value]
              else method.parameters.zip(arguments).map((parameter, value) => parameter.id -> value).toMap
            frames = new Frame(bindings, method.body) :: frames
            Right(())
          }
        }
      }

    private def matches(variable: R.Variable, value: E.Value): Boolean = (variable.valueType, value) match {
      case (R.ValueType.IntValue, _: E.Value.IntValue) => true
      case (R.ValueType.BooleanValue, _: E.Value.BooleanValue) => true
      case _ => false
    }

    private def write(frame: Frame, variable: R.Variable, value: E.Value): Result[Unit] =
      if !matches(variable, value) then Left(status(E.Failure.TypeMismatch))
      else { frame.values.update(variable.id, value); Right(()) }

    private def scope(frame: Frame, depth: Int): Result[Scope] = step().flatMap { _ =>
      if depth > limits.maxBlockDepth then Left(Status.LimitExceeded)
      else {
        val opened = mutable.Set.empty[R.VariableId]
        frame.scopes = opened :: frame.scopes
        Right(opened)
      }
    }

    private def dispatch(frame: Frame, action: Action): Result[Unit] = action match {
      case Action.EnterBlock(block, depth) => scope(frame, depth).map { opened =>
        frame.schedule(Action.Sequence(block.statements, 0, depth), Action.ExitScope(opened))
      }
      case Action.Sequence(statements, index, depth) =>
        if index < statements.size then
          frame.schedule(Action.Statement(statements(index), depth), Action.Sequence(statements, index + 1, depth))
        Right(())
      case Action.ExitScope(opened) =>
        opened.foreach(frame.values.remove)
        frame.scopes = frame.scopes.tail
        Right(())
      case Action.Statement(statement, depth) => step().flatMap(_ => executeStatement(frame, statement, depth))
      case Action.WhileTest(loop, depth) => step().flatMap(_ => condition(frame, loop.condition)).map { repeat =>
        if repeat then frame.schedule(Action.EnterBlock(loop.body, depth + 1), Action.WhileTest(loop, depth))
      }
      case Action.ForTest(loop, depth) =>
        step().flatMap { _ => loop.condition.fold[Result[Boolean]](Right(true))(condition(frame, _)) }.map { repeat =>
          if repeat then frame.schedule(Action.EnterBlock(loop.body, depth + 1),
            Action.Sequence(loop.update.statements, 0, depth), Action.ForTest(loop, depth))
        }
    }

    private def executeStatement(frame: Frame, statement: R.Statement, depth: Int): Result[Unit] = statement match {
      case R.Empty => Right(())
      case R.Return => frames = frames.tail; Right(())
      case R.Declare(variable, initial) =>
        frame.values.remove(variable.id)
        frame.scopes.head += variable.id
        initial.fold[Result[Unit]](Right(()))(value => evaluate(frame, value).flatMap(write(frame, variable, _)))
      case R.Assign(variable, R.AssignmentOperator.Set, expression) =>
        evaluate(frame, expression).flatMap(write(frame, variable, _))
      case R.Assign(variable, operator, expression) =>
        for {
          old <- frame.read(variable).left.map(status)
          value <- evaluate(frame, expression)
          result <- assigned(operator, old, value)
          _ <- write(frame, variable, result)
        } yield ()
      case R.Call(target, expressions) => arguments(frame, expressions).flatMap { values =>
        target match {
          case R.CallTarget.Helper(id) => start(id, values)
          case R.CallTarget.Turtle(command) => values match {
            case Vector(E.Value.IntValue(value)) => step().flatMap { _ =>
              if commands.size >= limits.maxCommands then Left(Status.LimitExceeded)
              else { commands += Command(command, value); Right(()) }
            }
            case _ => Left(status(E.Failure.TypeMismatch))
          }
        }
      }
      case R.If(test, positive, negative) => condition(frame, test).map { value =>
        (if value then Some(positive) else negative).foreach(body => frame.schedule(Action.EnterBlock(body, depth + 1)))
      }
      case loop: R.While => frame.schedule(Action.WhileTest(loop, depth)); Right(())
      case loop: R.For => scope(frame, depth + 1).map { opened =>
        frame.schedule(Action.Sequence(loop.init.statements, 0, depth + 1),
          Action.ForTest(loop, depth + 1), Action.ExitScope(opened))
      }
    }

    private def arguments(frame: Frame, expressions: Vector[R.Expression]): Result[Vector[E.Value]] = {
      var values: Result[Vector[E.Value]] = Right(Vector.empty)
      var index = 0
      while values.isRight && index < expressions.size do {
        values = values.flatMap(previous => evaluate(frame, expressions(index)).map(previous :+ _))
        index += 1
      }
      values
    }

    private def assigned(operator: R.AssignmentOperator, left: E.Value, right: E.Value): Result[E.Value] =
      (left, right) match {
        case (E.Value.IntValue(a), E.Value.IntValue(b)) =>
          val result = operator match {
            case R.AssignmentOperator.Add => Right(JavaInt32.add(a, b))
            case R.AssignmentOperator.Subtract => Right(JavaInt32.subtract(a, b))
            case R.AssignmentOperator.Multiply => Right(JavaInt32.multiply(a, b))
            case R.AssignmentOperator.Divide => JavaInt32.divide(a, b)
            case R.AssignmentOperator.Remainder => JavaInt32.remainder(a, b)
            case R.AssignmentOperator.Set => Right(b)
          }
          result.left.map {
            case JavaInt32.Error.DivisionByZero => status(E.Failure.DivisionByZero)
          }.map(E.Value.IntValue(_))
        case _ => Left(status(E.Failure.TypeMismatch))
      }
  }
}
