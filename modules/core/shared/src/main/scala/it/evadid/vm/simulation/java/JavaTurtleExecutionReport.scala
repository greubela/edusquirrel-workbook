package it.evadid.vm.simulation.java

import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleRuntime as T}
import scala.util.Try

object JavaTurtleExecutionReport {
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

  private def parse(text: String): ujson.Value = {
    if text == null || text.length > 1048576 then invalid("response is too large")
    val (depth, quoted, escaped) = text.foldLeft((0, false, false)) { case ((depth, quoted, escaped), char) =>
      if quoted then (depth, escaped || char != '"', !escaped && char == '\\')
      else char match {
        case '"' => (depth, true, false)
        case '{' | '[' =>
          if depth >= 3 then invalid("response is too deeply nested")
          (depth + 1, false, false)
        case '}' | ']' =>
          if depth <= 0 then invalid("unbalanced JSON")
          (depth - 1, false, false)
        case _ => (depth, false, false)
      }
    }
    if depth != 0 || quoted || escaped then invalid("incomplete JSON")
    Try(ujson.read(text)).getOrElse(invalid("malformed JSON"))
  }

  def decode(value: ujson.Value, limits: T.Limits): T.Execution = {
    val report = value match {
      case ujson.Str(text) => parse(text)
      case other => other
    }
    val required = Set("status", "problem", "commands", "steps")
    val fields = report match {
      case value: ujson.Obj if value.obj.keySet.toSet == required => value.obj
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
    status match {
      case T.Status.Failed(T.Failure.InvalidLimits | T.Failure.InvalidInvocation) =>
        if steps != 0 || commands.nonEmpty then
          invalid("invalid input must not execute")
      case _ =>
        if !limits.isValid || steps > limits.maxSteps || commands.size > limits.maxCommands || commands.size > steps then
          invalid("execution exceeds its limits")
        if steps == 0 && (status == T.Status.Completed || status == T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero)) ||
          status == T.Status.Failed(T.Failure.NonFiniteCommand)) then
          invalid("execution did not start")
    }
    if report.render().length > 1048576 then invalid("response is too large")
    T.Execution(status, commands, steps)
  }
}
