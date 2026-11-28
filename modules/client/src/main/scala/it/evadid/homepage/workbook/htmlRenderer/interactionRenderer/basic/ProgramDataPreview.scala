package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.vm.simulation.ProgramExecution
import it.evadid.vm.simulation.data.SudokuGrid
import it.evadid.vm.simulation.turtle.TurtleExecutionStatus
import it.evadid.workbook.interactions.programming.programmingExerciseRegular.ExerciseDataView
import it.evadid.workbook.interactions.programming.state.ProgrammingState

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * The provided number list or sudoku after running `main` on the default data, derived from the
 * central answer state. Runs are debounced and computed in a Future, so typing stays responsive.
 */
final class ProgramDataPreview(state: Var[ProgrammingState], view: ExerciseDataView, label: String => Signal[String]) extends HtmlAppElement {
  private given ExecutionContext = ExecutionContext.global

  private def run(value: ProgrammingState): Either[String, ProgramExecution] =
    try Right(value.toBeExpression.programExecution)
    catch { case NonFatal(error) => Left(Option(error.getMessage).getOrElse(error.getClass.getSimpleName)) }

  private val executions: Signal[Option[Either[String, ProgramExecution]]] =
    state.signal.changes.debounce(300).toSignal(state.now())
      .flatMapSwitch(value => Signal.fromFuture(Future(run(value))))

  private def listView(values: Vector[Int]): Element =
    ol(cls := "program-data-list", aria.label <-- label("programDataList"),
      values.zipWithIndex.map((value, index) =>
        li(cls := "program-data-list__item", span(cls := "program-data-list__value", value.toString),
          span(cls := "program-data-list__index", index.toString))))

  private def sudokuView(grid: SudokuGrid): Element =
    table(cls := "program-data-sudoku", aria.label <-- label("programDataSudoku"),
      tbody(grid.rows.zipWithIndex.map((row, r) => tr(row.zipWithIndex.map { (value, c) =>
        val fixed = grid.givens(r * 9 + c)
        td(cls := s"program-data-sudoku__cell${if fixed then " program-data-sudoku__cell--given" else if value != 0 then " program-data-sudoku__cell--placed" else ""}",
          if value == 0 then "" else value.toString)
      }))))

  private def problem(text: Signal[String]): Element = p(cls := "program-data-preview__problem", role := "status", child.text <-- text)

  override def getDomElement(): Element = div(cls := "program-data-preview",
    child <-- executions.map {
      case None => p(cls := "program-data-preview__pending", child.text <-- label("programDataRunning"))
      case Some(Left(message)) => problem(label("programDataUnavailable").map(prefix => s"$prefix $message"))
      case Some(Right(execution)) => div(
        view match {
          case ExerciseDataView.NumberList => listView(execution.world.list)
          case ExerciseDataView.Sudoku => sudokuView(execution.world.sudoku)
        },
        execution.status match {
          case TurtleExecutionStatus.Completed => emptyNode
          case TurtleExecutionStatus.Failed(message) => problem(label("programDataFailed").map(prefix => s"$prefix $message"))
          case TurtleExecutionStatus.LimitExceeded => problem(label("programDataLimit"))
          case TurtleExecutionStatus.Cancelled => emptyNode
        })
    })
}
