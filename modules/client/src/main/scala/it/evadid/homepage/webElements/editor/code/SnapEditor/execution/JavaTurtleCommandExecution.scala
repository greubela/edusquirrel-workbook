package it.evadid.homepage.webElements.editor.code.SnapEditor.execution

import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.{PythonRunConfig, PythonRunReport}
import it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonExport
import it.evadid.vm.parsing.java.turtle.{JavaTurtleInputLimits, JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleInvocationTrace, JavaTurtleRuntime as T}
import todomove.`export`.workers.PyodideWorkerClient

import java.util.concurrent.{CancellationException, TimeoutException}
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

trait JavaTurtlePythonWorker {
  def ready(): Future[Unit]
  def run(code: String, config: PythonRunConfig): Future[PythonRunReport]
  def terminate(): Unit
}

final class JavaTurtleCommandRunner(
    workerFactory: () => JavaTurtlePythonWorker = JavaTurtleCommandRunner.workerFactory,
    startupTimeoutMs: Int = 120000,
    executionTimeoutMs: Int = 10000
) {
  require(startupTimeoutMs > 0 && executionTimeoutMs > 0, "Java worker timeouts must be positive.")

  private case class WorkerSlot(worker: JavaTurtlePythonWorker, ready: Future[Unit])
  private class Run(val slot: WorkerSlot) {
    val promise: Promise[T.Execution] = Promise()
    var timer: Option[SetTimeoutHandle] = None
  }

  private var worker: Option[WorkerSlot] = None
  private var active: Option[Run] = None
  private var closed = false

  def run(program: P.Program, limits: T.Limits = T.Limits(), traceInvocations: Boolean = false): Future[T.Execution] =
    execute(program, None, Vector.empty, limits, traceInvocations)

  def invoke(program: P.Program, method: R.MethodId, arguments: Vector[E.Value],
      limits: T.Limits = T.Limits(), traceInvocations: Boolean = false): Future[T.Execution] =
    execute(program, Some(method), arguments, limits, traceInvocations)

  def cancel(): Boolean = active match {
    case Some(run) =>
      finish(run, Failure(new CancellationException("Java execution cancelled.")), discard = true)
      true
    case None => false
  }

  def close(): Unit = {
    closed = true
    if !cancel() then discardWorker()
  }

  private def current(run: Run): Boolean = active.exists(_ eq run)

  private def discardWorker(): Unit = {
    val discarded = worker
    worker = None
    discarded.foreach { slot =>
      // Cleanup must not leave the caller's promise pending if the transport is already broken.
      try slot.worker.terminate()
      catch { case NonFatal(_) => () }
    }
  }

  private def finish(run: Run, result: Try[T.Execution], discard: Boolean): Unit =
    if current(run) then {
      run.timer.foreach(clearTimeout)
      run.timer = None
      active = None
      run.promise.tryComplete(result)
      if discard then discardWorker()
    }

  private def deadline(run: Run, milliseconds: Int, phase: String): Unit = {
    run.timer.foreach(clearTimeout)
    run.timer = Some(setTimeout(milliseconds.toDouble) {
      finish(run, Failure(new TimeoutException(s"Java worker $phase timed out.")), discard = true)
    })
  }

  private def acquireWorker(): WorkerSlot = worker.getOrElse {
    val transport = workerFactory()
    val ready = try transport.ready() catch { case NonFatal(error) => Future.failed(error) }
    val slot = WorkerSlot(transport, ready)
    worker = Some(slot)
    slot
  }

  private def execute(program: P.Program, method: Option[R.MethodId], arguments: Vector[E.Value],
      limits: T.Limits, traceInvocations: Boolean): Future[T.Execution] = {
    if closed then Future.failed(new IllegalStateException("Java runner is closed."))
    else if active.nonEmpty then Future.failed(new IllegalStateException("Java execution is already running."))
    else if !JavaTurtleCommandRunner.valid(limits) then
      Future.successful(T.Execution(T.Status.Failed(T.Failure.InvalidLimits), Vector.empty, 0))
    else if !JavaTurtleCommandRunner.validInvocation(program, method, arguments) then
      Future.successful(T.Execution(T.Status.Failed(T.Failure.InvalidInvocation), Vector.empty, 0))
    else {
      val prepared = Try(JavaTurtleCommandRunner.code(program, method, arguments, limits, traceInvocations) -> acquireWorker())
      prepared match {
        case Failure(error) => Future.failed(error)
        case Success((code, slot)) =>
          val run = new Run(slot)
          active = Some(run)
          deadline(run, startupTimeoutMs, "startup")
          slot.ready.onComplete {
            case Success(_) if current(run) =>
              deadline(run, executionTimeoutMs, "execution")
              val response = try slot.worker.run(code, PythonRunConfig(resetGlobals = false))
                catch { case NonFatal(error) => Future.failed(error) }
              response.onComplete {
                case Success(report) =>
                  if current(run) then {
                    val expected = if !traceInvocations then None else {
                      val target = method.getOrElse(program.root.entryPoint.binding.id)
                      val parameters = program.root.methods.find(_.binding.id == target).get.binding.parameters
                      val initial = parameters.zip(arguments).map((parameter, argument) =>
                        E.widen(argument, parameter.variable.valueType).toOption.get)
                      Some(target -> initial)
                    }
                    val decoded = Try(JavaTurtleCommandRunner.decode(report, limits,
                      Some(program.root.methods.map(_.binding.id).toSet), expected))
                    finish(run, decoded, discard = decoded.isFailure)
                  }
                case Failure(error) => finish(run, Failure(error), discard = true)
              }
            case Failure(error) => finish(run, Failure(error), discard = true)
            case _ => ()
          }
          run.promise.future
      }
    }
  }
}

object JavaTurtleCommandRunner {
  private def workerFactory(): JavaTurtlePythonWorker = new JavaTurtlePythonWorker {
    private val client = new PyodideWorkerClient()
    override def ready(): Future[Unit] = client.run("pass", PythonRunConfig(resetGlobals = false)).map(_ => ())
    override def run(code: String, config: PythonRunConfig): Future[PythonRunReport] = client.run(code, config)
    override def terminate(): Unit = client.terminate()
  }

  private def valid(limits: T.Limits): Boolean =
    limits.maxSteps > 0 && limits.maxSteps <= T.Limits.MaxSteps &&
      limits.maxCommands >= 0 && limits.maxCommands <= T.Limits.MaxCommands &&
      limits.maxCallDepth > 0 && limits.maxCallDepth <= T.Limits.MaxCallDepth &&
      limits.maxBlockDepth > 0 && limits.maxBlockDepth <= T.Limits.MaxBlockDepth

  private def validInvocation(program: P.Program, method: Option[R.MethodId], arguments: Vector[E.Value]): Boolean =
    method.fold(arguments.isEmpty) { id =>
      program.root.methods.find(_.binding.id == id).exists { method =>
        (method ne program.root.entryPoint) && method.binding.parameters.size == arguments.size &&
          method.binding.parameters.zip(arguments).forall { (parameter, value) =>
            (parameter.variable.valueType, value) match {
              case (R.ValueType.IntValue, _: E.Value.IntValue) => true
              case (R.ValueType.DoubleValue, _: E.Value.IntValue | _: E.Value.DoubleValue) => true
              case (R.ValueType.BooleanValue, _: E.Value.BooleanValue) => true
              case _ => false
            }
          }
      }
    }

  private def code(program: P.Program, method: Option[R.MethodId], arguments: Vector[E.Value], limits: T.Limits,
      traceInvocations: Boolean): String = {
    val source = ujson.Str(JavaTurtlePythonExport.render(program).source).render()
    val target = method.fold("None")(_.index.toString)
    val values = arguments.map {
      case E.Value.IntValue(value) => value.toString
      case E.Value.DoubleValue(value) => JavaTurtlePythonExport.doubleLiteral(value)
      case E.Value.BooleanValue(value) => if value then "True" else "False"
    }.mkString("[", ", ", "]")
    s"""import json as _java_json
_java_namespace = {}
exec($source, _java_namespace, _java_namespace)
_java_result = _java_namespace["${JavaTurtlePythonExport.EntryPoint}"](method=$target, arguments=$values, max_steps=${limits.maxSteps}, max_commands=${limits.maxCommands}, max_call_depth=${limits.maxCallDepth}, max_block_depth=${limits.maxBlockDepth}, trace_invocations=${if traceInvocations then "True" else "False"})
print(_java_json.dumps(_java_result, separators=(",", ":")))
"""
  }

  private def invalid(detail: String): Nothing =
    throw new IllegalArgumentException(s"Invalid Java execution result: $detail")

  private def integer(value: ujson.Value, lower: Int, upper: Int): Int = value match {
    case ujson.Num(number) if number.isFinite && number >= lower && number <= upper && number == math.floor(number) =>
      number.toInt
    case _ => invalid("expected a bounded integer")
  }

  private def finiteDouble(value: ujson.Value): Double = value match {
    case ujson.Num(number) if number.isFinite => number
    case _ => invalid("expected a finite number")
  }

  private def callEvidence(value: ujson.Value, steps: Int, knownMethods: Option[Set[R.MethodId]]): T.CallEvidence = {
    val fields = value match {
      case value: ujson.Obj if value.obj.keySet.toSet == Set("methods", "maxDepth") => value.obj
      case _ => invalid("expected methods and maximum call depth")
    }
    val methods = fields("methods") match {
      case values: ujson.Arr if values.value.size <= JavaTurtleInputLimits.MaxMethods => values.value.toVector.map {
        case row: ujson.Arr if row.value.size == 3 =>
          val method = R.MethodId(integer(row.value(0), 0, JavaTurtleInputLimits.MaxMethods - 1))
          if knownMethods.exists(!_.contains(method)) then invalid("unknown Java method")
          val calls = integer(row.value(1), 1, T.Limits.MaxSteps)
          T.MethodCalls(method, calls, integer(row.value(2), 0, calls - 1))
        case _ => invalid("expected method, call count and recursive call count")
      }
      case _ => invalid("expected bounded method calls")
    }
    val ids = methods.map(_.method.index)
    val total = methods.map(_.calls).sum
    val maxDepth = integer(fields("maxDepth"), 0, T.Limits.MaxCallDepth)
    if ids != ids.distinct.sorted || total > steps || maxDepth > total ||
      methods.isEmpty != (maxDepth == 0) || methods.exists(_.recursiveCalls > 0) && maxDepth < 2 then
      invalid("inconsistent method calls")
    T.CallEvidence(methods, maxDepth)
  }

  private def drawingEvidence(value: ujson.Value, calls: T.CallEvidence, commands: Vector[T.Command]): T.DrawingEvidence = {
    val forwards = commands.count(command => command.command == R.TurtleCommand.Forward && command.value != 0.0)
    val called = calls.methods.map(method => method.method -> method).toMap
    val methods = value match {
      case value: ujson.Obj if value.obj.keySet.toSet == Set("methods") => value("methods") match {
        case rows: ujson.Arr if rows.value.size <= JavaTurtleInputLimits.MaxMethods => rows.value.toVector.map {
          case row: ujson.Arr if row.value.size == 3 =>
            val method = R.MethodId(integer(row.value(0), 0, JavaTurtleInputLimits.MaxMethods - 1))
            val entered = called.getOrElse(method, invalid("drawing method did not execute"))
            val count = integer(row.value(1), 1, forwards)
            val recursive = integer(row.value(2), 0, count)
            if recursive > 0 && entered.recursiveCalls == 0 then invalid("drawing method did not recurse")
            T.MethodDrawing(method, count, recursive)
          case _ => invalid("expected method, drawing count and recursive drawing count")
        }
        case _ => invalid("expected bounded drawing methods")
      }
      case _ => invalid("expected drawing methods")
    }
    val ids = methods.map(_.method.index)
    if ids != ids.distinct.sorted || forwards > 0 && !methods.exists(_.forwardCommands == forwards) ||
      methods.map(_.forwardCommands).sum > forwards * calls.maxDepth then
      invalid("inconsistent drawing methods")
    T.DrawingEvidence(methods)
  }

  private def invocationEvidence(value: ujson.Value, calls: T.CallEvidence, commands: Int, completed: Boolean,
      knownMethods: Option[Set[R.MethodId]], expected: Option[(R.MethodId, Vector[E.Value])]): T.InvocationEvidence = {
    val fields = value match {
      case value: ujson.Obj if value.obj.keySet.toSet == Set("method", "activations", "truncated") => value.obj
      case _ => invalid("expected invocation trace")
    }
    val method = R.MethodId(integer(fields("method"), 0, JavaTurtleInputLimits.MaxMethods - 1))
    if knownMethods.exists(!_.contains(method)) then invalid("unknown observed method")
    val truncated = fields("truncated") match {
      case ujson.Bool(flag) => flag
      case _ => invalid("expected trace truncation flag")
    }
    def argument(value: ujson.Value): E.Value = value match {
      case row: ujson.Arr if row.value.size == 2 => (row.value(0), row.value(1)) match {
        case (ujson.Str("i"), number) => E.Value.IntValue(integer(number, Int.MinValue, Int.MaxValue))
        case (ujson.Str("b"), ujson.Bool(flag)) => E.Value.BooleanValue(flag)
        case (ujson.Str("d"), ujson.Str(bits)) if bits.length == 16 && bits.forall("0123456789abcdef".contains(_)) =>
          E.Value.DoubleValue(java.lang.Double.longBitsToDouble(java.lang.Long.parseUnsignedLong(bits, 16)))
        case _ => invalid("expected typed invocation argument")
      }
      case _ => invalid("expected typed invocation argument")
    }
    val activations = fields("activations") match {
      case rows: ujson.Arr if rows.value.size <= T.Limits.MaxInvocations => rows.value.toVector.map {
        case row: ujson.Arr if row.value.size == 4 =>
          val parent = integer(row.value(0), -1, T.Limits.MaxInvocations - 1)
          val arguments = row.value(1) match {
            case values: ujson.Arr if values.value.size <= JavaTurtleInputLimits.MaxParameters => values.value.toVector.map(argument)
            case _ => invalid("expected bounded invocation arguments")
          }
          val start = integer(row.value(2), 0, commands)
          val end = integer(row.value(3), -1, commands)
          T.MethodInvocation(Option.when(parent >= 0)(parent), arguments, start, Option.when(end >= 0)(end))
        case _ => invalid("expected parent, arguments and command range")
      }
      case _ => invalid("expected bounded invocation trace")
    }
    val evidence = T.InvocationEvidence(method, activations, truncated)
    if !JavaTurtleInvocationTrace.valid(evidence, calls, commands, completed) then invalid("inconsistent invocation trace")
    if expected.exists { (target, arguments) =>
      target != method || activations.headOption.exists(row => !sameArguments(row.arguments, arguments))
    } then invalid("invocation trace does not match its request")
    evidence
  }

  private def sameArguments(left: Vector[E.Value], right: Vector[E.Value]): Boolean =
    left.size == right.size && left.zip(right).forall {
      case (E.Value.DoubleValue(a), E.Value.DoubleValue(b)) =>
        a.isNaN && b.isNaN || java.lang.Double.doubleToRawLongBits(a) == java.lang.Double.doubleToRawLongBits(b)
      case (a, b) => a == b
    }

  private[execution] def decode(report: PythonRunReport, limits: T.Limits,
      knownMethods: Option[Set[R.MethodId]] = None,
      expectedInvocation: Option[(R.MethodId, Vector[E.Value])] = None): T.Execution = {
    if report.callbackOps.nonEmpty || report.stderr.nonEmpty then invalid("unexpected Python output")
    if report.stdout.length > 1048576 then invalid("response is too large")
    val parsed = try ujson.read(report.stdout) catch { case NonFatal(error) =>
      throw new IllegalArgumentException("Invalid Java execution result: expected JSON", error)
    }
    val required = Set("status", "problem", "commands", "steps")
    val fields = parsed match {
      case value: ujson.Obj if value.obj.keySet.toSet == required || value.obj.keySet.toSet == required + "calls" ||
          value.obj.keySet.toSet == required + "calls" + "drawing" ||
          value.obj.keySet.toSet == required + "calls" + "drawing" + "invocations" => value.obj
      case _ => invalid("expected status, problem, commands and steps")
    }
    val status = (fields("status"), fields("problem")) match {
      case (ujson.Str("Completed"), ujson.Null) => T.Status.Completed
      case (ujson.Str("LimitExceeded"), ujson.Null) => T.Status.LimitExceeded
      case (ujson.Str("Cancelled"), ujson.Null) => T.Status.Cancelled
      case (ujson.Str("Failed"), ujson.Str("InvalidLimits")) => T.Status.Failed(T.Failure.InvalidLimits)
      case (ujson.Str("Failed"), ujson.Str("InvalidInvocation")) => T.Status.Failed(T.Failure.InvalidInvocation)
      case (ujson.Str("Failed"), ujson.Str("DivisionByZero")) => T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero))
      case (ujson.Str("Failed"), ujson.Str("NonFiniteCommand")) => T.Status.Failed(T.Failure.NonFiniteCommand)
      case _ => invalid("unknown status or problem")
    }
    val steps = integer(fields("steps"), 0, T.Limits.MaxSteps)
    val commands = fields("commands") match {
      case values: ujson.Arr if values.value.size <= T.Limits.MaxCommands => values.value.toVector.map {
        case pair: ujson.Arr if pair.value.size == 2 =>
          val command = pair.value(0) match {
            case ujson.Str("forward") => R.TurtleCommand.Forward
            case ujson.Str("right") => R.TurtleCommand.TurnRight
            case _ => invalid("unknown turtle command")
          }
          T.Command(command, finiteDouble(pair.value(1)))
        case _ => invalid("expected a command and its numeric argument")
      }
      case _ => invalid("expected bounded turtle commands")
    }
    val calls = fields.get("calls").map(callEvidence(_, steps, knownMethods))
    val drawing = fields.get("drawing").map(drawingEvidence(_, calls.get, commands))
    val invocations = fields.get("invocations").map(invocationEvidence(_, calls.get, commands.size,
      status == T.Status.Completed, knownMethods, expectedInvocation))
    status match {
      case T.Status.Failed(T.Failure.InvalidLimits | T.Failure.InvalidInvocation) =>
        if steps != 0 || commands.nonEmpty || calls.exists(_ != T.CallEvidence()) || drawing.exists(_ != T.DrawingEvidence()) || invocations.nonEmpty then
          invalid("invalid input must not execute")
      case _ =>
        if !valid(limits) || steps > limits.maxSteps || commands.size > limits.maxCommands || commands.size > steps then
          invalid("execution exceeds its limits")
        if steps == 0 && (status == T.Status.Completed || status == T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero)) ||
          status == T.Status.Failed(T.Failure.NonFiniteCommand)) then
          invalid("execution did not start")
        if calls.exists(evidence => evidence.maxDepth > limits.maxCallDepth || steps > 0 && evidence.methods.isEmpty) then
          invalid("execution exceeds its call limits")
    }
    T.Execution(status, commands, steps, calls, drawing, invocations)
  }
}
