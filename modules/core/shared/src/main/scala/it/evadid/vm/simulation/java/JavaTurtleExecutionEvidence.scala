package it.evadid.vm.simulation.java

import it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonExport as Y
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleRuntime as T, JavaTurtleTrace as J}
import upickle.default.ReadWriter

object JavaTurtleExecutionEvidence {
  enum Scalar derives ReadWriter {
    case Int32(value: Int)
    case Float64(bits: String)
    case Bool(value: Boolean)

    def toValue: E.Value = this match {
      case Int32(value) => E.Value.IntValue(value)
      case Bool(value) => E.Value.BooleanValue(value)
      case Float64("nan") => E.Value.DoubleValue(Double.NaN)
      case Float64(bits) =>
        require(bits != null && bits.matches("[0-9a-f]{16}"), "Invalid double bits.")
        val value = java.lang.Double.longBitsToDouble(java.lang.Long.parseUnsignedLong(bits, 16))
        require(!value.isNaN, "Use the canonical NaN value.")
        E.Value.DoubleValue(value)
    }
  }

  object Scalar {
    def fromValue(value: E.Value): Scalar = value match {
      case E.Value.IntValue(value) => Scalar.Int32(value)
      case E.Value.DoubleValue(value) => Scalar.Float64(doubleBits(value))
      case E.Value.BooleanValue(value) => Scalar.Bool(value)
    }
  }

  enum Outcome derives ReadWriter {
    case Completed, LimitExceeded, DivisionByZero, NonFiniteCommand
  }

  case class LocatedExpression(invocation: Int, method: String, start: Int, end: Int,
      value: Scalar) derives ReadWriter
  case class Snapshot(outcome: Outcome, steps: Int, commandCount: Int,
      expressions: Vector[LocatedExpression]) derives ReadWriter

  case class Context(source: String, method: Option[R.MethodId] = None,
      arguments: Vector[E.Value] = Vector.empty, limits: T.Limits = T.Limits(), budget: J.Budget = J.Budget())

  final class Verified private[JavaTurtleExecutionEvidence](val context: Context, val execution: T.Execution,
      private val trace: J.Capture) {
    def events: Vector[J.Event] = trace.events

    def locate(eventId: Int, current: Context): Option[J.LocatedEvent] =
      Option.when(sameContext(context, current))(()).flatMap(_ => trace.locate(eventId, current.source))

    def locatedSlice(target: Int, current: Context, includeOrder: Boolean = false): Option[J.LocatedSlice] =
      Option.when(sameContext(context, current))(()).flatMap(_ => trace.locatedSlice(target, current.source, includeOrder))

    def snapshot: Snapshot = {
      val outcome = execution.status match {
        case T.Status.Completed => Outcome.Completed
        case T.Status.LimitExceeded => Outcome.LimitExceeded
        case T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero)) => Outcome.DivisionByZero
        case T.Status.Failed(T.Failure.NonFiniteCommand) => Outcome.NonFiniteCommand
        case _ => throw IllegalArgumentException("The execution has no usable observations.")
      }
      val expressions = trace.locatedCompletedExpressions(context.source).getOrElse(
        throw IllegalArgumentException("The expressions cannot be mapped to Java source.")).map { (located, value) =>
        val method = trace.methodName(located.event.node.method).getOrElse(
          throw IllegalArgumentException("The expression has no method."))
        LocatedExpression(located.event.invocation, method, located.range.start, located.range.end, Scalar.fromValue(value))
      }
      Snapshot(outcome, execution.steps, execution.commands.size, expressions)
    }
  }

  final class Prepared private[JavaTurtleExecutionEvidence](program: P.Program, val context: Context) {
    val code: String = Y.invocationSource(Y.renderObserved(program, context.budget.maxEvents),
      context.method, context.arguments, context.limits)

    private lazy val reference = context.method match {
      case None => T.runVmTraced(program, context.limits, budget = context.budget)
      case Some(method) => T.invokeVmTraced(program, method, context.arguments, context.limits, budget = context.budget)
    }

    def verify(raw: String, current: Context): Verified = {
      require(sameContext(context, current), "The execution context has changed.")
      val value = JavaTurtleExecutionReport.parse(raw, maxLength = 4194304, maxDepth = 4)
      require(value.objOpt.exists(_.keySet == Set("execution", "observations", "truncated")),
        "Invalid observation response fields.")
      require(value("truncated") == ujson.Bool(false), "The Python observations are incomplete.")
      val observations = value("observations").arrOpt.filter(_.size <= context.budget.maxEvents)
        .getOrElse(throw IllegalArgumentException("Invalid observation count."))
      require(value("execution").objOpt.isDefined, "Invalid observed execution report.")
      val execution = JavaTurtleExecutionReport.decode(value("execution"), context.limits)
      require(!reference.trace.truncated, "The VM trace is incomplete.")
      require(sameExecution(execution, reference.execution), "The execution reports do not match.")
      val expected = reference.trace.completedExpressions
        .getOrElse(throw IllegalArgumentException("The trace cannot be mapped to Java source."))
      require(observations.toVector == expected.map(observation), "The expression observations do not match.")
      new Verified(context, execution, reference.trace)
    }
  }

  def prepare(program: P.Program, method: Option[R.MethodId] = None, arguments: Vector[E.Value] = Vector.empty,
      limits: T.Limits = T.Limits(), budget: J.Budget = J.Budget()): Prepared = {
    require(method.nonEmpty || arguments.isEmpty, "Main does not accept invocation arguments.")
    new Prepared(program, Context(program.bindings.source.source, method, arguments, limits, budget))
  }

  private def sameContext(left: Context, right: Context): Boolean =
    left.source == right.source && left.method == right.method && left.limits == right.limits &&
      left.budget == right.budget && left.arguments.map(encodedValue) == right.arguments.map(encodedValue)

  private def sameExecution(left: T.Execution, right: T.Execution): Boolean =
    left.status == right.status && left.steps == right.steps && left.commands.size == right.commands.size &&
      left.commands.zip(right.commands).forall { (left, right) =>
        left.command == right.command && doubleBits(left.value) == doubleBits(right.value)
      }

  private def doubleBits(value: Double): String =
    if value.isNaN then "nan"
    else {
      val hex = java.lang.Long.toHexString(java.lang.Double.doubleToRawLongBits(value))
      "0" * (16 - hex.length) + hex
    }

  private def encodedValue(value: E.Value): ujson.Value = value match {
    case E.Value.IntValue(value) => ujson.Obj("kind" -> "int", "value" -> value.toString)
    case E.Value.DoubleValue(value) => ujson.Obj("kind" -> "double", "value" -> doubleBits(value))
    case E.Value.BooleanValue(value) => ujson.Obj("kind" -> "bool", "value" -> value)
  }

  private def observation(value: J.ExpressionObservation): ujson.Value = ujson.Obj(
    "invocation" -> value.invocation, "method" -> value.node.method.index,
    "path" -> ujson.Arr.from(value.node.path), "value" -> encodedValue(value.value))
}
