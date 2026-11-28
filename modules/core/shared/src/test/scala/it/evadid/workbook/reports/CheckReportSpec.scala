package it.evadid.workbook.reports

import it.evadid.core.datastructures.language.{LanguageMapContentId, WorkbookContent}
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.InteractionFormat.MatchingInteractionFormat
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.interactions.choice.WorkbookContentChoiceInteraction
import it.evadid.workbook.interactions.choice.WorkbookContentChoiceInteraction.*
import it.evadid.workbook.interactions.matching.{MatchingGrader, MatchingInteraction, MatchingInteractionState}
import it.evadid.workbook.interactions.reorderExercise.ReorderInteraction.ReorderMapIdInteraction
import it.evadid.workbook.interactions.table.{AnswerTableInteraction, EditableTableCell, TableAnswer}
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.history.{InteractionVariableHistory, InteractionVariableState}
import munit.FunSuite

import java.time.LocalDateTime

class CheckReportSpec extends FunSuite {
  private def id(s: String) = LanguageMapContentId(s"test/$s")
  private val matching = MatchingInteraction("check-matching", None, Seq(id("a"), id("b"), id("c")), Seq(id("x"), id("y")), Some(Seq(0, 1, 0)))
  private val grader = matching.gradingElements.head.asInstanceOf[MatchingGrader]
  private def answer(rights: Option[Int]*) = MatchingInteractionState(rights)
  private def at(minute: Int) = LocalDateTime.of(2026, 1, 1, 10, minute)

  private def history(checks: (MatchingInteractionState, GradingStatus, Int)*): InteractionVariableHistory[MatchingInteractionFormat.type] =
    checks.foldLeft(InteractionVariableHistory.empty(MatchingInteractionFormat)) { case (current, (state, status, minute)) =>
      current.addState(InteractionVariableState(state, UpdateImportance.MAJOR, at(minute)))
        .addGrading(GradingResult(grader, state, status, None, Some(0), at(minute)))
    }

  test("failed checks are counted until the first passed one; only the current answer is marked") {
    val checks = Seq(
      (answer(Some(1), Some(1), None), GradingStatus.PartiallyCorrect, 1),
      (answer(Some(0), Some(0), Some(1)), GradingStatus.Incorrect, 2),
      (answer(Some(0), Some(1), Some(0)), GradingStatus.Correct, 3),
      (answer(Some(0), Some(1), Some(1)), GradingStatus.PartiallyCorrect, 4))
    val report = CheckReport(matching, history(checks*))
    assert(report.tracked)
    assertEquals(report.failedChecks, 2)
    // Unconnected statements are no mistakes.
    assertEquals(report.wrongParts, Set(2))
    val changed = history(checks*).addState(InteractionVariableState(answer(Some(0), None, None), UpdateImportance.MINOR, at(5)))
    assertEquals(CheckReport(matching, changed).wrongParts, Set.empty[Int])
    assertEquals(CheckReport(matching, changed).failedChecks, 2)
  }

  test("execution errors are no checks and automatically graded interactions are not tracked") {
    val errored = CheckReport(matching, history((answer(Some(1), None, None), GradingStatus.TestError(SerializedException("crash")), 1)))
    assertEquals(errored, CheckReport(tracked = true, 0, Set.empty))
    val options = Seq(true, false).map(correct => ChoiceOptionWorkbookContent(WorkbookContent(id(correct.toString)),
      Some(GradingForChoice(if correct then GradingStatus.Correct else GradingStatus.Incorrect, id("feedback")))))
    val automatic = WorkbookContentChoiceInteraction("auto-choice", ChoiceConfig(id("q"), Some(false), GraderRunningBehavior.RUN_ALWAYS), options)
    assertEquals(CheckReport(automatic, InteractionVariableHistory.empty(automatic.interactionFormat)), CheckReport.untracked)
  }

  test("wrong parts name wrong options, filled wrong cells and misplaced items only") {
    val options = Seq(true, false, false).zipWithIndex.map((correct, index) => ChoiceOptionWorkbookContent(WorkbookContent(id(s"o$index")),
      Some(GradingForChoice(if correct then GradingStatus.Correct else GradingStatus.Incorrect, id("feedback")))))
    val choice = WorkbookContentChoiceInteraction("choice", ChoiceConfig(id("q"), Some(true), GraderRunningBehavior.RUN_ONLY_MANUALLY), options)
    val selected = (0 until 3).foldLeft(choice.defaultValue)((state, index) => choice.select(state, index, index != 1))
    assertEquals(choice.wrongParts(selected), Set(2))

    val table = AnswerTableInteraction("table", id("caption"), Seq(id("r1"), id("r2"), id("r3")), Seq(id("c")),
      Seq(Seq(EditableTableCell(Some(Seq("1")))), Seq(EditableTableCell(Some(Seq("2")))), Seq(EditableTableCell(Some(Seq("3"))))))
    assertEquals(table.wrongParts(TableAnswer(Seq(" 1 ", "", "4"))), Set(2))

    val reorder = ReorderMapIdInteraction("reorder", Seq("a", "b", "c", "d", "e").map(id))
    // Moving item 4 to the front shifts all others, but only item 4 is misplaced.
    assertEquals(reorder.wrongParts(reorder.defaultValue.copy(currentOrder = Seq(4, 0, 1, 2, 3))), Set(4))
  }
}
