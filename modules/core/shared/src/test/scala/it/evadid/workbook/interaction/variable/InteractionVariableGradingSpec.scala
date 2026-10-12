package it.evadid.workbook.interaction.variable

import it.evadid.workbook.abstractions.{FeedbackEntity, WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import munit.FunSuite
import upickle.default.*

import java.time.LocalDateTime
import scala.collection.immutable.ListSet
import scala.concurrent.Future

class InteractionVariableGradingSpec extends FunSuite {
  private class RecordingGrader extends WorkbookInteractionGrader[String, String] {
    var requests = List.empty[(String, Boolean)]
    override val importance = GradingImportance.REQUIRED_TEST
    override val runningBehavior = GraderRunningBehavior.RUN_ALWAYS
    override val gradingEntity = FeedbackEntity.TestEntity()
    override def gradeState(state: String): Future[GradingResult[String, String]] =
      Future.successful(GradingResult(this, state, GradingStatus.Correct()))
    override def gradeStateSafe(state: String, forced: Boolean): Future[GradingResult[String, String]] = {
      requests = requests :+ (state -> forced)
      gradeState(state)
    }
  }

  private def variable(grader: RecordingGrader): InteractionVariable[String, String, String] = {
    val interaction = new WorkbookInteractionElementFull[String, String, String] {
      override val elementId = "grading-test"
      override val defaultValue = ""
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val gradingElements = List(grader)
    }
    interaction.interactionVariable
  }

  test("grading uses the newest timestamp when history iteration is out of order") {
    val grader = new RecordingGrader
    val value = variable(grader)
    val time = LocalDateTime.of(2026, 10, 12, 0, 0)
    value.updateHistory(_.copy(events = ListSet(
      InteractionVariableState("older", MAJOR, time),
      InteractionVariableState("newest", MAJOR, time.plusSeconds(1))
    )))
    value.startGrading(null)
    assertEquals(grader.requests, List("newest" -> false))
  }

  test("cached grading is reused unless grading is explicitly forced") {
    val grader = new RecordingGrader
    val value = variable(grader)
    val cached = GradingResult[String, String](grader, "", GradingStatus.Correct())
    value.updateHistory(_.copy(gradings = Map("" -> GradedState("", Set(cached)))))
    value.startGrading(null)
    assertEquals(grader.requests, Nil)
    value.startGrading(null, forceGrading = true)
    assertEquals(grader.requests, List("" -> true))
  }

  test("empty histories do not request grading") {
    val grader = new RecordingGrader
    val value = variable(grader)
    value.updateHistory(_.copy(events = Set.empty))
    value.startGrading(null, forceGrading = true)
    assertEquals(grader.requests, Nil)
  }
}
