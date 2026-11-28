package it.evadid.vm.analysis

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.defining.{BeDependency, BeUnknownDependency}
import it.evadid.vm.conversion.dependencies.{KnownDependencies, ProgrammingEnvironment, UnknownDependencies}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.code.errors.BeSourcePlaceholder
import it.evadid.vm.simulation.turtle.TurtleExecutionStatus
import it.evadid.workbook.interactions.programming.state.{ProgrammingState, ProgrammingStateJavaStructured, ProgrammingStatePythonStructured, ProgrammingStateRaw}
import upickle.default.ReadWriter

import scala.util.Try

/** What can be known about a program without running it dynamically.
  *
  * Static facts come from the shared BeExpression: the turtle run (from the bounded interpreter, without
  * side effects), the libraries it calls, the dependencies it calls without anything defining them and the source
  * it could not represent, with their located uses as `warnings` where the parser locates them (Java). Dynamic execution (console output, network access) runs in Pyodide, so the analysis
  * also carries the Python source: Python as written, other languages converted. A `Left` explains why a fact is
  * unavailable.
  */
final case class ProgramAnalysis(
    python: Either[String, String],
    turtle: Either[String, ProgramAnalysis.TurtleRun],
    unknownDependency: Option[BeUnknownDependency],
    unsupportedSource: Seq[String],
    libraries: Seq[BeEntityName],
    warnings: Seq[SourceWarning]
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

  /** The analysis of `state` in an exercise providing `environment`'s libraries. */
  def of(state: ProgrammingState, environment: ProgrammingEnvironment = ProgrammingEnvironment.all): ProgramAnalysis = {
    val derivations = state.inEnvironment(environment)
    val expression = Try(derivations.expression)
    val placeholders = expression.toOption.toSeq.flatMap(_.expression.subtree.collect { case value: BeSourcePlaceholder => value })
    val unsupported = placeholders.map(_.originalSource)
    val python = state match {
      case ProgrammingStateRaw(Python, code) => Right(code)
      case value: ProgrammingStatePythonStructured => Right(value.toString)
      case _ => Try(derivations.python.code).toEither.left.map(reason)
    }
    val turtle = expression.flatMap(value => Try(value.turtleCommandExecutionInfo)).toEither.left.map(reason)
      .map(info => TurtleRun(info.executedTurtleCommandsInOrder, info.executionStatus))
    val libraries = expression.toOption.toSeq.flatMap(value => KnownDependencies.requiredBy(value.expression)).map(_.structureName).distinct
    val unknownPaths = expression.toOption.toSeq.flatMap(value =>
      UnknownDependencies.calls(value.expression).map(_.path) ++ UnknownDependencies.attributes(value.expression).map(_.path)).toSet
    val warnings = state match {
      case ProgrammingStateRaw(Java, _) | _: ProgrammingStateJavaStructured => JavaSourceWarnings.of(derivations.javaSource, unknownPaths)
      case ProgrammingStateRaw(Python, code) => PythonSourceWarnings.of(
        it.evadid.vm.parsing.source.SourceParsing.parse(code, it.evadid.vm.parsing.source.SourceParsing.SourceLanguage.Python), unknownPaths)
      case _: ProgrammingStatePythonStructured => PythonSourceWarnings.of(derivations.pythonSource, unknownPaths)
      case _ => Nil
    }
    ProgramAnalysis(python, turtle, expression.toOption.flatMap(_.unknownDependency), unsupported, libraries, warnings)
  }
}
