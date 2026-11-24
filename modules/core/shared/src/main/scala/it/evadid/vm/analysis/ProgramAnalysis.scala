package it.evadid.vm.analysis

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.defining.BeUnknownDependency
import it.evadid.vm.code.errors.BeSourcePlaceholder
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStatePythonStructured, ProgrammingStateRaw}
import upickle.default.ReadWriter

import scala.util.Try

/** What can be known about a program without running it dynamically.
  *
  * Static facts come from the shared BeExpression: the turtle drawing (from the bounded interpreter, without
  * side effects), the dependencies it calls without defining them and the source it could not represent.
  * Dynamic execution (console output, network access) runs in Pyodide, so the analysis also carries the Python
  * source: Python as written, other languages converted. A `Left` explains why a fact is unavailable.
  */
final case class ProgramAnalysis(
    python: Either[String, String],
    turtleCommands: Either[String, Seq[TurtleCommand[Double]]],
    unknownDependency: Option[BeUnknownDependency],
    unsupportedSource: Seq[String]
) derives ReadWriter

object ProgramAnalysis {
  private def reason(error: Throwable): String = Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)

  def of(state: ProgrammingState): ProgramAnalysis = {
    val expression = Try(state.toBeExpression)
    val placeholders = expression.toOption.toSeq.flatMap(_.expression.subtree.collect { case value: BeSourcePlaceholder => value })
    val unsupported = placeholders.map(_.originalSource)
    val python = state match {
      case ProgrammingStateRaw(Python, code) => Right(code)
      case value: ProgrammingStatePythonStructured => Right(value.toString)
      case other => Try(other.toPython.code).toEither.left.map(reason)
    }
    val turtle =
      if unsupported.nonEmpty then Left(s"Unsupported source: ${unsupported.mkString(", ")}")
      else expression.flatMap(value => Try(value.turtleCommandExecutionInfo.completedCommands.toSeq)).toEither.left.map(reason)
    ProgramAnalysis(python, turtle, expression.toOption.flatMap(_.unknownDependency), unsupported)
  }
}
