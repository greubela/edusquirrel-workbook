package it.evadid.vm.projectexport

import it.evadid.core.util.InfoUtil

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.vm.modules.BeModules
import it.evadid.vm.simulation.data.{DataLibrarySources, DataWorld}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}

import scala.util.control.NonFatal

/**
 * Downloadable projects for the programming editors, one format per language: a BlueJ project for Java,
 * a folder with `main.py` for Python and the project XML for Snap. Programs in another language are
 * converted first, so the result follows the editor tab the student downloads from.
 */
object ProjectExport {
  def forLanguage(state: ProgrammingState, language: ProgrammingLanguage, title: String,
      world: DataWorld = DataWorld()): Either[String, ExportedProject] = language match {
    case Java => JavaProjectExport.blueJ(state, title, world)
    case Python => python(state, title, world)
    case SnapLanguage => attempt(ExportedProject(InfoUtil.fileNameSafe(title),
      Seq(ProjectFile(s"${InfoUtil.fileNameSafe(title)}.xml", state.toSnapXml.code))))
    case other => Left(s"Downloading $other projects is not supported.")
  }

  /** `main.py` with the list and sudoku classes it calls; the printer already adds imports such as `turtle`. */
  def python(state: ProgrammingState, title: String, world: DataWorld = DataWorld()): Either[String, ExportedProject] = attempt {
    val source = state match {
      case ProgrammingStateRaw(Python, code) => code
      case other => other.toPython.code
    }
    val program = try Some(state.toBeExpression.expression) catch { case NonFatal(_) => None }
    def uses(module: it.evadid.vm.modules.BeModule[?]) = program.exists(_.moduleCalls(module).nonEmpty)
    def defines(name: String) = source.linesIterator.exists(line => line.startsWith(s"class $name:") || line.startsWith(s"class $name("))
    val libraries = Seq(
      Option.when(uses(BeModules.NumberList) && !defines("NumberList"))(DataLibrarySources.pythonNumberList(world.list)),
      Option.when(uses(BeModules.Sudoku) && !defines("Sudoku"))(DataLibrarySources.pythonSudoku(world.sudoku))).flatten
    val main = (libraries :+ source.trim).mkString("", "\n\n", "\n")
    ExportedProject(InfoUtil.fileNameSafe(title), Seq(ProjectFile("main.py", main)))
  }

  private def attempt(project: => ExportedProject): Either[String, ExportedProject] =
    try Right(project) catch { case NonFatal(error) => Left(Option(error.getMessage).getOrElse(error.getClass.getSimpleName)) }
}
