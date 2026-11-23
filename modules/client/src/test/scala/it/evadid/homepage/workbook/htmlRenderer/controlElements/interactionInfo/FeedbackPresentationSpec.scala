package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.info.interaction.GradingReporter
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.FeedbackPresentation.*
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus}
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
import it.evadid.workbook.meta.{GradingSummary, ScaffoldingReport}

class FeedbackPresentationSpec extends EditorDomSuite {
  private def criteria(count: Int) = TextInputWithScaffoldingAndGrading(s"criteria-$count", LanguageMapContentId("test/task"),
    contentGrading = (1 to count).map(index => ContentForLlmInteraction(LanguageMapContentId(s"test/criterion$index"), Map.empty)).toList)
  private def summary(exercise: TextInputWithScaffoldingAndGrading, statuses: GradingStatus*) =
    GradingSummary(exercise.gradingElements, GradedState("", exercise.gradingElements.zip(statuses).map((grader, status) =>
      grader -> GradingResult[String, MessengerModel](grader, "", status)).toMap))

  test("placeholders are filled by name and unknown ones stay visible") {
    assertEquals(fill("Sorts {list}", Map("list" -> "[3, 1]")), "Sorts [3, 1]")
    assertEquals(fill("{a} and {a}, {b}", Map("a" -> "x")), "x and x, {b}")
    assertEquals(fill("plain", Map.empty), "plain")
  }

  test("rows keep partial credit apart from failures, and every status has a student-facing word") {
    val exercise = criteria(6)
    val result = summary(exercise, GradingStatus.Correct(), GradingStatus.PartiallyCorrect(), GradingStatus.Incorrect(),
      GradingStatus.NotFinished(), GradingStatus.TestError(it.evadid.distribution.command.SerializedException("x")), GradingStatus.NotRequested())
    assertEquals(result.entries.map(_.tone), Seq(Tone.Correct, Tone.Partial, Tone.Incorrect, Tone.Pending, Tone.Error, Tone.Neutral))
    assertEquals(result.entries.map(_.statusKey), Seq("gradingGroupCorrect", "gradingPartiallyCorrect", "gradingGroupIncorrect",
      "gradingLoading", "gradingError", "gradingNotRun"))
    assertEquals(result.toneCounts, Tone.values.toSeq.map(_ -> 1))
    assert(Tone.values.forall(tone => tone.css.nonEmpty && tone.labelKey.startsWith("grading")))
  }

  test("the overall tone shows partial credit instead of a plain failure") {
    val exercise = criteria(2)
    assertEquals(summary(exercise, GradingStatus.Correct(), GradingStatus.Correct()).tone, Tone.Correct)
    assertEquals(summary(exercise, GradingStatus.Correct(), GradingStatus.Incorrect()).tone, Tone.Partial)
    assertEquals(summary(exercise, GradingStatus.PartiallyCorrect(), GradingStatus.Incorrect()).tone, Tone.Partial)
    assertEquals(summary(exercise, GradingStatus.Incorrect(), GradingStatus.Incorrect()).tone, Tone.Incorrect)
    assertEquals(summary(exercise, GradingStatus.NotFinished(), GradingStatus.Correct()).tone, Tone.Pending)
    assertEquals(summary(exercise).tone, Tone.Neutral)
    assertEquals(summary(exercise, GradingStatus.Incorrect(), GradingStatus.Incorrect()).toneCounts, Seq(Tone.Incorrect -> 2))
  }

  test("hint states map to labels, explanations and tones") {
    import ScaffoldingReport.Status
    assertEquals(Status.values.toSeq.map(_.messageKey), Seq("requestScaffoldingHint", "scaffoldingLoading", "scaffoldingError", "scaffoldingReady"))
    assertEquals(Status.values.toSeq.map(_.tone), Seq(Tone.Neutral, Tone.Pending, Tone.Error, Tone.Correct))
    assertEquals(Status.Ready.labelKey, "scaffoldingReady")
  }

  test("AI criteria are titled as one assessment, or numbered when there are several") {
    def titles(exercise: TextInputWithScaffoldingAndGrading): Seq[String] = {
      val panel = InteractionFeedbackPanel(exercise, GradingReporter(exercise)).getDomElement()
      val close = mount(panel)
      try (0 until panel.ref.querySelectorAll(".feedback-test-description").length)
        .map(panel.ref.querySelectorAll(".feedback-test-description")(_).textContent.toLowerCase)
      finally close()
    }
    assert(titles(criteria(1)).forall(_.contains("gradingaiassessment")), titles(criteria(1)))
    val numbered = titles(criteria(2))
    assertEquals(numbered.size, 2)
    assert(numbered.forall(_.contains("gradingaicriterion")), numbered)
  }

  test("the action bar offers checking for graded exercises and hints only where a hint task exists") {
    val graded = criteria(1)
    val hinted = TextInputWithScaffoldingAndGrading("hinted", LanguageMapContentId("test/task"), contentGrading = Nil,
      contentScaffolding = Some(ContentForLlmInteraction(LanguageMapContentId("test/hint"), Map.empty)))
    def buttons(exercise: TextInputWithScaffoldingAndGrading): (Int, Int, Int) = {
      val panel = InteractionFeedbackPanel(exercise, GradingReporter(exercise)).getDomElement()
      val close = mount(panel)
      try (panel.ref.querySelectorAll("button.feedback-action--primary:not([disabled])").length,
        panel.ref.querySelectorAll("button.scaffolding-info").length, panel.ref.querySelectorAll(".feedback-grading").length)
      finally close()
    }
    assertEquals(buttons(graded), (1, 0, 1))
    assertEquals(buttons(hinted), (0, 1, 0))
  }
}
