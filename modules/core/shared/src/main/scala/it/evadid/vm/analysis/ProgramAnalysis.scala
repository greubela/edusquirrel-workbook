package it.evadid.vm.analysis

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.defining.{BeDependency, BeUnknownDependency}
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.code.errors.BeSourcePlaceholder
import it.evadid.vm.simulation.turtle.TurtleExecutionStatus
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStatePythonStructured, ProgrammingStateRaw}
import upickle.default.ReadWriter

import scala.util.Try

/** What can be known about a program without running it dynamically.
  *
  * Static facts come from the shared BeExpression: the turtle run (from the bounded interpreter, without
  * side effects), the libraries it calls, the dependencies it calls without anything defining them and the source
  * it could not represent. Dynamic execution (console output, network access) runs in Pyodide, so the analysis
  * also carries the Python source: Python as written, other languages converted. A `Left` explains why a fact is
  * unavailable.
  */
final case class ProgramAnalysis(
    python: Either[String, String],
    turtle: Either[String, ProgramAnalysis.TurtleRun],
    unknownDependency: Option[BeUnknownDependency],
    unsupportedSource: Seq[String],
    libraries: Seq[BeEntityName]
) derives ReadWriter {
  /** The known dependencies the program calls into. */
  def libraryDependencies: Seq[BeDependency] = libraries.flatMap(KnownDependencies.named)

  /** The commands of a completed run, which graders compare. */
  def completedCommands: Either[String, Seq[TurtleCommand[Double]]] =
    turtle.flatMap(run => Either.cond(run.status == TurtleExecutionStatus.Completed, run.commands,
      s"Turtle execution did not complete: ${run.status}."))

  /** The program's drawing, refused with the unrepresentable source when there is any. */
  def drawing: Either[String, Seq[TurtleCommand[Double]]] =
    if unsupportedSource.nonEmpty then Left(s"Unsupported source: ${unsupportedSource.mkString(", ")}") else completedCommands
}

object ProgramAnalysis {
  /** The turtle commands the interpreter executed, in order, and how the run ended (possibly early). */
  final case class TurtleRun(commands: Seq[TurtleCommand[Double]], status: TurtleExecutionStatus) derives ReadWriter

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
    val turtle = expression.flatMap(value => Try(value.turtleCommandExecutionInfo)).toEither.left.map(reason)
      .map(info => TurtleRun(info.executedTurtleCommandsInOrder, info.executionStatus))
    val libraries = expression.toOption.toSeq.flatMap(value => KnownDependencies.requiredBy(value.expression)).map(_.structureName).distinct
    ProgramAnalysis(python, turtle, expression.toOption.flatMap(_.unknownDependency), unsupported, libraries)
  }
}
