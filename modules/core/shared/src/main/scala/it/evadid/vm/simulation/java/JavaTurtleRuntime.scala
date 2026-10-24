package it.evadid.vm.simulation.java

import it.evadid.core.datastructures.state.State
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleTrace as J}
import scala.collection.mutable

object JavaTurtleRuntime {
  enum Failure {
    case InvalidInvocation, InvalidLimits, NonFiniteCommand
    case Evaluation(problem: E.Failure)
  }

  enum Status {
    case Completed, LimitExceeded, Cancelled
    case Failed(problem: Failure)
  }

  case class Command(command: R.TurtleCommand, value: Double)
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

  def runVmTraced(program: P.Program, limits: Limits = Limits(),
      isCancelled: () => Boolean = () => false, budget: J.Budget = J.Budget()): J.RecordedExecution =
    recorded(budget)(recorder => execute(program.resolvedMethods, program.root.entryPoint.binding.id,
      program.root.entryPoint.binding.id, Vector.empty, true, limits, isCancelled, Some(recorder)))

  def invokeVmTraced(program: P.Program, method: R.MethodId, arguments: Vector[E.Value],
      limits: Limits = Limits(), isCancelled: () => Boolean = () => false,
      budget: J.Budget = J.Budget()): J.RecordedExecution =
    recorded(budget)(recorder => execute(program.resolvedMethods, program.root.entryPoint.binding.id,
      method, arguments, false, limits, isCancelled, Some(recorder)))

  private def recorded(budget: J.Budget)(run: J.Recorder => Execution): J.RecordedExecution = {
    val recorder = new J.Recorder(budget)
    val execution = run(recorder)
    J.RecordedExecution(execution, recorder.capture)
  }

  private def execute(methods: Vector[R.Method], entryPoint: R.MethodId, method: R.MethodId, arguments: Vector[E.Value],
      main: Boolean, limits: Limits, isCancelled: () => Boolean, recorder: Option[J.Recorder] = None): Execution =
    if !limits.valid then Execution(Status.Failed(Failure.InvalidLimits), Vector.empty, 0)
    else new Runner(methods, entryPoint, limits, isCancelled, recorder).execute(method, arguments, main)

  private type Result[A] = Either[Status, A]
  private type Scope = mutable.Set[R.VariableId]
  private val untracedNode = J.SourceNode(R.MethodId(-1), Vector.empty)

  private enum Action {
    case EnterBlock(block: R.Block, depth: Int, node: J.SourceNode)
    case Sequence(statements: Vector[R.Statement], index: Int, depth: Int, node: J.SourceNode)
    case Statement(statement: R.Statement, depth: Int, node: J.SourceNode)
    case ExitScope(scope: Scope)
    case WhileTest(loop: R.While, depth: Int, node: J.SourceNode)
    case ForTest(loop: R.For, depth: Int, node: J.SourceNode)
  }

  private case class TraceFrameState(writes: Map[R.VariableId, Int] = Map.empty,
      condition: Option[Int] = None, lastReturn: Option[Int] = None, lastEffect: Option[Int] = None)

  private class FrameTrace(val recorder: J.Recorder, val invocation: Int, val call: Option[Int]) {
    private val state = State(TraceFrameState())

    def isRecording: Boolean = recorder.isRecording
    def controls: Vector[Int] = state.now().condition.toVector
    def order: Vector[Int] = state.now().lastReturn.toVector

    private def record(node: => J.SourceNode, kind: => J.Kind, data: => Vector[Int] = Vector.empty,
        ordered: => Vector[Int] = Vector.empty): Option[Int] =
      if isRecording then recorder.record(invocation, node, kind, data, controls, call.toVector, order ++ ordered)
      else None

    def clear(variables: => Set[R.VariableId]): Unit =
      if isRecording then state.update(value => value.copy(writes = value.writes -- variables))

    def read(node: J.SourceNode, variable: R.Variable, value: E.Value): Option[Int] =
      record(node, J.Kind.Read(variable, value), state.now().writes.get(variable.id).toVector)

    def write(node: J.SourceNode, variable: R.Variable, value: E.Value, dependencies: Vector[Int]): Option[Int] = {
      val event = record(node, J.Kind.Write(variable, value), dependencies)
      event.foreach(id => state.update(current => current.copy(writes = current.writes.updated(variable.id, id))))
      event
    }

    def expression(node: J.SourceNode): E.Observer = new E.Observer {
      def isRecording: Boolean = FrameTrace.this.isRecording
      private def at(path: Vector[Int]): J.SourceNode = node.copy(path = node.path ++ path.map(_.toString))
      def read(path: Vector[Int], variable: R.Variable, value: E.Value): Option[Int] = FrameTrace.this.read(at(path), variable, value)
      def write(path: Vector[Int], variable: R.Variable, value: E.Value, dependencies: Vector[Int]): Option[Int] =
        FrameTrace.this.write(at(path), variable, value, dependencies)
      def result(path: Vector[Int], value: E.Value, dependencies: Vector[Int]): Option[Int] =
        record(at(path), J.Kind.Expression(value), dependencies)
    }

    def condition(node: J.SourceNode, value: Boolean, dependency: Option[Int]): Unit = {
      val event = record(node, J.Kind.Condition(value), dependency.toVector)
      // Retain earlier predicates through this chain, including guards whose other branch returns.
      event.foreach(id => state.update(current => current.copy(condition = Some(id))))
    }

    def turtle(node: J.SourceNode, index: Int, command: Command, dependency: Option[Int]): Unit = {
      val event = record(node, J.Kind.Turtle(index, command), dependency.toVector, recorder.lastTurtle.toVector)
      event.foreach(id => state.update(current => current.copy(lastEffect = Some(id))))
    }

    def returned(node: J.SourceNode, explicit: Boolean): Option[Int] =
      record(node, J.Kind.Return(explicit), ordered = state.now().lastEffect.toVector)

    def resume(event: Option[Int]): Unit =
      if isRecording then state.update(current => current.copy(lastReturn = event, lastEffect = event.orElse(current.lastEffect)))
  }

  private class Frame(arguments: Map[R.VariableId, E.Value], body: R.Block,
      val method: R.MethodId, val tracing: Option[FrameTrace]) {
    val values: mutable.Map[R.VariableId, E.Value] = mutable.Map.from(arguments)
    val bodyNode = if tracing.exists(_.isRecording) then J.SourceNode(method, Vector("body")) else untracedNode
    var scopes: List[Scope] = Nil
    var pending: List[Action] = List(Action.EnterBlock(body, 1, bodyNode))

    val read: E.Reader = variable => values.get(variable.id).toRight(E.Failure.MissingValue(variable.id))
    val write: E.Writer = (variable, value) => E.widen(value, variable.valueType).map { widened =>
      values.update(variable.id, widened)
    }

    def schedule(actions: Action*): Unit = pending = actions.toList ::: pending
  }

  private class Runner(definitions: Vector[R.Method], entryPoint: R.MethodId, limits: Limits,
      isCancelled: () => Boolean, recorder: Option[J.Recorder]) {
    private val methods = definitions.map(method => method.id -> method).toMap
    private val commands = mutable.ArrayBuffer.empty[Command]
    private var frames = List.empty[Frame]
    private var used = 0

    def execute(method: R.MethodId, arguments: Vector[E.Value], main: Boolean): Execution = {
      var result = start(method, arguments, main)
      while result.isRight && frames.nonEmpty do {
        val frame = frames.head
        frame.pending match {
          case Nil => finishFrame(frame.bodyNode, false)
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

    private def child(node: J.SourceNode, role: String): J.SourceNode =
      if recorder.exists(_.isRecording) then node.child(role) else node

    private def child(node: J.SourceNode, index: Int): J.SourceNode =
      if recorder.exists(_.isRecording) then node.child(index.toString) else node

    private def evaluate(frame: Frame, expression: R.Expression, node: J.SourceNode): Result[E.Observed] =
      E.evaluateObservedWithGate(expression, frame.read, E.Limits(), () => gate(), frame.write,
        frame.tracing.filter(_.isRecording).map(_.expression(node))).left.map(status)

    private def condition(frame: Frame, expression: R.Expression, node: J.SourceNode): Result[Boolean] =
      evaluate(frame, expression, node).flatMap { result => result.value match {
        case E.Value.BooleanValue(value) =>
          frame.tracing.foreach(_.condition(node, value, result.dependency))
          Right(value)
        case _ => Left(status(E.Failure.TypeMismatch))
      }
      }

    private def start(id: R.MethodId, arguments: Vector[E.Value], main: Boolean = false,
        node: Option[J.SourceNode] = None, dependencies: Vector[Option[Int]] = Vector.empty): Result[Unit] =
      methods.get(id).toRight(Status.Failed(Failure.InvalidInvocation)).flatMap { method =>
        val valid = if main then id == entryPoint && arguments.isEmpty
          else id != entryPoint && method.parameters.size == arguments.size &&
            method.parameters.zip(arguments).forall((parameter, value) => matches(parameter, value))
        if !valid then Left(Status.Failed(Failure.InvalidInvocation))
        else {
          step().flatMap { _ =>
            if frames.size >= limits.maxCallDepth then Left(Status.LimitExceeded)
            else {
              val bindings = if main then Map.empty[R.VariableId, E.Value]
                else method.parameters.zip(arguments).map((parameter, value) =>
                  parameter.id -> E.widen(value, parameter.valueType).toOption.get).toMap
              val tracing = recorder.filter(_.isRecording).map { recorder =>
                val caller = frames.headOption.flatMap(_.tracing)
                val invocation = recorder.invocation()
                val call = recorder.record(invocation, node.getOrElse(J.SourceNode(id, Vector("entry"))),
                  J.Kind.Call(id, arguments), dependencies.flatten,
                  caller.toVector.flatMap(_.controls), caller.toVector.flatMap(_.call), caller.toVector.flatMap(_.order))
                val trace = new FrameTrace(recorder, invocation, call)
                if !main then method.parameters.zipWithIndex.foreach { (parameter, index) =>
                  trace.write(J.SourceNode(id, Vector("parameter", index.toString)), parameter, bindings(parameter.id),
                    dependencies.lift(index).flatten.toVector)
                }
                trace
              }
              frames = new Frame(bindings, method.body, id, tracing) :: frames
              Right(())
            }
          }
        }
      }

    private def finishFrame(node: J.SourceNode, explicit: Boolean): Unit = {
      val returned = frames.head.tracing.flatMap(_.returned(node, explicit))
      frames = frames.tail
      frames.headOption.flatMap(_.tracing).foreach(_.resume(returned))
    }

    private def matches(variable: R.Variable, value: E.Value): Boolean = (variable.valueType, value) match {
      case (R.ValueType.IntValue, _: E.Value.IntValue) => true
      case (R.ValueType.DoubleValue, _: E.Value.IntValue | _: E.Value.DoubleValue) => true
      case (R.ValueType.BooleanValue, _: E.Value.BooleanValue) => true
      case _ => false
    }

    private def write(frame: Frame, variable: R.Variable, value: E.Value, node: J.SourceNode,
        dependencies: Vector[Int]): Result[Unit] =
      E.widen(value, variable.valueType).left.map(status).map { widened =>
        frame.values.update(variable.id, widened)
        frame.tracing.foreach(_.write(node, variable, widened, dependencies))
      }

    private def scope(frame: Frame, depth: Int): Result[Scope] = step().flatMap { _ =>
      if depth > limits.maxBlockDepth then Left(Status.LimitExceeded)
      else {
        val opened = mutable.Set.empty[R.VariableId]
        frame.scopes = opened :: frame.scopes
        Right(opened)
      }
    }

    private def dispatch(frame: Frame, action: Action): Result[Unit] = action match {
      case Action.EnterBlock(block, depth, node) => scope(frame, depth).map { opened =>
        frame.schedule(Action.Sequence(block.statements, 0, depth, node), Action.ExitScope(opened))
      }
      case Action.Sequence(statements, index, depth, node) =>
        if index < statements.size then
          frame.schedule(Action.Statement(statements(index), depth, child(node, index)),
            Action.Sequence(statements, index + 1, depth, node))
        Right(())
      case Action.ExitScope(opened) =>
        opened.foreach(frame.values.remove)
        frame.tracing.foreach(_.clear(opened.toSet))
        frame.scopes = frame.scopes.tail
        Right(())
      case Action.Statement(statement, depth, node) => step().flatMap(_ => executeStatement(frame, statement, depth, node))
      case Action.WhileTest(loop, depth, node) => step().flatMap(_ => condition(frame, loop.condition, child(node, "condition"))).map { repeat =>
        if repeat then frame.schedule(Action.EnterBlock(loop.body, depth + 1, child(node, "body")), Action.WhileTest(loop, depth, node))
      }
      case Action.ForTest(loop, depth, node) =>
        step().flatMap { _ => loop.condition match {
          case Some(expression) => condition(frame, expression, child(node, "condition"))
          case None => frame.tracing.foreach(_.condition(node, true, None)); Right(true)
        }
        }.map { repeat =>
          if repeat then frame.schedule(Action.EnterBlock(loop.body, depth + 1, child(node, "body")),
            Action.Sequence(loop.update.statements, 0, depth, child(node, "update")), Action.ForTest(loop, depth, node))
        }
    }

    private def executeStatement(frame: Frame, statement: R.Statement, depth: Int, node: J.SourceNode): Result[Unit] = statement match {
      case R.Empty => Right(())
      case R.Return => finishFrame(node, true); Right(())
      case R.Declare(variable, initial) =>
        frame.values.remove(variable.id)
        frame.tracing.foreach(_.clear(Set(variable.id)))
        frame.scopes.head += variable.id
        initial.fold[Result[Unit]](Right(()))(value => evaluate(frame, value, child(node, "initial")).flatMap(result =>
          write(frame, variable, result.value, node, result.dependency.toVector)))
      case R.Assign(variable, R.AssignmentOperator.Set, expression) =>
        evaluate(frame, expression, child(node, "value")).flatMap(result =>
          write(frame, variable, result.value, node, result.dependency.toVector))
      case R.Assign(variable, operator, expression) =>
        for {
          old <- frame.read(variable).left.map(status)
          read = frame.tracing.filter(_.isRecording).flatMap(_.read(child(node, "target"), variable, old))
          value <- evaluate(frame, expression, child(node, "value"))
          result <- assigned(operator, old, value.value)
          _ <- write(frame, variable, result, node, read.toVector ++ value.dependency.toVector)
        } yield ()
      case R.Evaluate(expression) => evaluate(frame, expression, child(node, "expression")).map(_ => ())
      case R.Call(target, expressions) => arguments(frame, expressions, node).flatMap { values =>
        target match {
          case R.CallTarget.Helper(id) => start(id, values.map(_.value), node = Some(node), dependencies = values.map(_.dependency))
          case R.CallTarget.Turtle(command) => values.map(_.value) match {
            case Vector(E.Value.IntValue(value)) => emit(frame, node, command, value.toDouble, values.head.dependency)
            case Vector(E.Value.DoubleValue(value)) => emit(frame, node, command, value, values.head.dependency)
            case _ => Left(status(E.Failure.TypeMismatch))
          }
        }
      }
      case R.If(test, positive, negative) => condition(frame, test, child(node, "condition")).map { value =>
        (if value then Some(positive) else negative).foreach(body =>
          frame.schedule(Action.EnterBlock(body, depth + 1, child(node, if value then "then" else "else"))))
      }
      case loop: R.While => frame.schedule(Action.WhileTest(loop, depth, node)); Right(())
      case loop: R.For => scope(frame, depth + 1).map { opened =>
        frame.schedule(Action.Sequence(loop.init.statements, 0, depth + 1, child(node, "init")),
          Action.ForTest(loop, depth + 1, node), Action.ExitScope(opened))
      }
    }

    private def arguments(frame: Frame, expressions: Vector[R.Expression], node: J.SourceNode): Result[Vector[E.Observed]] =
      expressions.zipWithIndex.foldLeft[Result[Vector[E.Observed]]](Right(Vector.empty)) { case (values, (expression, index)) =>
        values.flatMap(previous => evaluate(frame, expression, child(child(node, "argument"), index)).map(previous :+ _))
      }

    private def assigned(operator: R.AssignmentOperator, left: E.Value, right: E.Value): Result[E.Value] =
      if operator == R.AssignmentOperator.Set then Right(right)
      else {
        val arithmetic = operator match {
          case R.AssignmentOperator.Add => R.BinaryOperator.Add
          case R.AssignmentOperator.Subtract => R.BinaryOperator.Subtract
          case R.AssignmentOperator.Multiply => R.BinaryOperator.Multiply
          case R.AssignmentOperator.Divide => R.BinaryOperator.Divide
          case R.AssignmentOperator.Remainder => R.BinaryOperator.Remainder
          case R.AssignmentOperator.Set => throw IllegalStateException("Not an arithmetic assignment.")
        }
        E.arithmetic(arithmetic, left, right).left.map(status)
      }

    private def emit(frame: Frame, node: J.SourceNode, command: R.TurtleCommand, value: Double,
        dependency: Option[Int]): Result[Unit] = step().flatMap { _ =>
      if commands.size >= limits.maxCommands then Left(Status.LimitExceeded)
      else if !value.isFinite then Left(Status.Failed(Failure.NonFiniteCommand))
      else {
        commands += Command(command, value)
        frame.tracing.foreach(_.turtle(node, commands.size - 1, commands.last, dependency))
        Right(())
      }
    }
  }
}
