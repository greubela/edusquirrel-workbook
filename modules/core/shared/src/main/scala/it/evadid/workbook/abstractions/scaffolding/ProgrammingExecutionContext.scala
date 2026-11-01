package it.evadid.workbook.abstractions.scaffolding

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.util.logging.BasicLogger
import it.evadid.vm.simulation.java.JavaTurtleExecutionEvidence as Evidence
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingTestInvocation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

private[scaffolding] object ProgrammingExecutionContext {
  case class Observation(id: String, expression: String, call: String, line: Int, value: String) {
    def json(invocationId: String): ujson.Value = ujson.Obj("id" -> id, "invocationId" -> invocationId,
      "expression" -> expression, "call" -> call, "line" -> line, "value" -> value)
  }

  def collect(source: String, invocation: ProgrammingTestInvocation,
      clients: List[ExecutionClient], verifyCurrent: () => Unit)(using ExecutionContext): Future[Vector[Observation]] = {
    val prepared = Try {
      val difference = invocation.commandComparison.get
      require(difference.actual.nonEmpty && difference.expected.nonEmpty)
      require(difference.actual.get.name == difference.expected.get.name)
      val arguments = invocation.arguments.map {
        case ProgrammingTestInvocation.Scalar.Bool(value) => Evidence.Scalar.Bool(value)
        case ProgrammingTestInvocation.Scalar.Float64(bits) => Evidence.Scalar.Float64(bits)
        case ProgrammingTestInvocation.Scalar.Integer(value) =>
          require(BigInt(value).isValidInt)
          Evidence.Scalar.Int32(value.toInt)
      }
      val request = WorkerCommands.JavaExecutionEvidenceRequest(source, invocation.target, arguments,
        commandIndex = Some(difference.commandIndex))
      val workerCommand = ExecutionCommand(WorkerCommands.javaExecutionEvidence.name, upickle.default.writeJs(request))
      val worker = clients.find(_.canExecuteCommand(workerCommand)).get
      (difference, request, worker)
    }
    prepared.toOption match {
      case None => Future.successful(Vector.empty)
      case Some((difference, request, worker)) =>
        WorkerCommands.javaExecutionEvidence.sendCommandTo(worker, request, Some(BasicLogger())).map { response =>
          verifyCurrent()
          val snapshot = response.result
          require(snapshot.outcome == Evidence.Outcome.Completed && snapshot.commandCount == difference.actualCount)
          val focus = snapshot.focus.get
          val actual = difference.actual.get
          require(focus.index == difference.commandIndex && focus.command == actual.name && actual.args.size == 1)
          require(number(focus.value).exists(value =>
            java.lang.Double.doubleToRawLongBits(value) == java.lang.Double.doubleToRawLongBits(actual.args.head)))
          require(focus.facts.size <= 12)
          focus.facts.zipWithIndex.flatMap { (fact, index) => observation(source, fact, index) }
            .distinctBy(value => (value.expression, value.call, value.line, value.value))
        }.recover { case NonFatal(_) => Vector.empty }
    }
  }

  private def number(value: Evidence.Scalar): Option[Double] = value match {
    case Evidence.Scalar.Int32(value) => Some(value.toDouble)
    case value: Evidence.Scalar.Float64 => Try(value.toValue).toOption.collect {
      case it.evadid.vm.simulation.java.JavaTurtleEvaluation.Value.DoubleValue(value) if value.isFinite => value
    }
    case _ => None
  }

  private def valueText(value: Evidence.Scalar): Option[String] = value match {
    case Evidence.Scalar.Int32(value) => Some(value.toString)
    case Evidence.Scalar.Bool(value) => Some(value.toString)
    case Evidence.Scalar.Float64(bits) => number(value).map { number =>
      if bits == "8000000000000000" then "-0.0"
      else {
        val text = java.lang.Double.toString(number)
        if text.matches("-?[0-9]+") then s"$text.0" else text
      }
    }
  }

  private def observation(source: String, fact: Evidence.ExpressionFact, index: Int): Option[Observation] = {
    val expression = fact.expression
    for {
      _ <- Option.when(expression.start >= 0 && expression.end > expression.start && expression.end <= source.length)(())
      text = source.substring(expression.start, expression.end).trim
      _ <- Option.when(text.length <= 120 && text.exists(Character.isJavaIdentifierStart) &&
        !Set("true", "false").contains(text) && !text.contains("/*") && !text.contains("//") &&
        !text.exists(char => Character.isISOControl(char) || char == '\u2028' || char == '\u2029'))(())
      value <- valueText(expression.value)
      arguments <- fact.arguments.foldLeft(Option(Vector.empty[String])) { (all, value) =>
        for { previous <- all; next <- valueText(value) } yield previous :+ next
      }
      _ <- Option.when(expression.method.nonEmpty && expression.method.length <= 128 &&
        expression.method.forall(Character.isJavaIdentifierPart) && arguments.size <= 8)(())
    } yield Observation(s"execution-${index + 1}", text, s"${expression.method}(${arguments.mkString(", ")})",
      source.substring(0, expression.start).split("\\r\\n|\\r|\\n", -1).length, value)
  }
}
