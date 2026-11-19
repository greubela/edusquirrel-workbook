package it.evadid.homepage.workbook

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.workbook.meta.GradingSummary
import it.evadid.workbook.meta.GradingSummary.Group
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel.InteractionInfoSidebar
import it.evadid.vm.test.ProgrammingTestSuite
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingUnitTestGrader}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.meta.ExecutionContextInformation
import it.evadid.homepage.control.info.interaction.GradingReporter
import java.time.LocalDateTime

class InteractionFeedbackViewsSpec extends EditorDomSuite {
  private val date = LocalDateTime.parse("2026-11-15T12:30:00")
  private def grader(id: String, hidden: Boolean = false) = ProgrammingUnitTestGrader(id, s"assert '$id'",
    if hidden then GradingImportance.HIDDEN_TEST else GradingImportance.REQUIRED_TEST)
  private def feedback = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Öffentliche Rückmeldung", English -> "Public feedback"))

  test("summary counts public and hidden outcomes, groups partial results, and counts missing authored tests") {
    val graders = List(grader("ok"), grader("hidden-ok", true), grader("partial"), grader("hidden-bad", true),
      grader("pending"), grader("error", true), grader("unrequested"), grader("missing"))
    val statuses = List(GradingStatus.Correct(), GradingStatus.Correct(), GradingStatus.PartiallyCorrect(),
      GradingStatus.Incorrect(), GradingStatus.NotFinished(),
      GradingStatus.TestError(it.evadid.distribution.command.SerializedException("private execution error")), GradingStatus.NotRequested())
    val state: ProgrammingState = it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(Python, "forward(10)")
    val results = graders.zip(statuses).map((g, status) => GradingResult(g, state, status))
    val obsolete = GradingResult(grader("removed-grader"), state, GradingStatus.Correct())
    val stale = GradingResult(graders.last,
      it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(Python, "forward(999)"), GradingStatus.Correct())
    val summary = GradingSummary(graders, GradedState(state, (results ++ List(obsolete, stale)).map(result => result.grader -> result).toMap))
    assertEquals(summary.correct, 2)
    assertEquals(summary.total, 8)
    assertEquals(summary.in(Group.Incorrect).size, 2)
    assertEquals(summary.visibleIn(Group.Correct).size, 1)
    assertEquals(summary.visibleIn(Group.Error).size, 0)
    assertEquals(summary.in(Group.NotRequested).size, 2)
    assertEquals(summary.unavailable, 4)
  }

  test("sidebar omits hidden details, updates public feedback language, and preserves disclosure state") {
    val public = grader("public-description")
    val hidden = grader("SECRET-HIDDEN-DESCRIPTION", true)
    val exercise = ProgrammingExercise("feedback-sidebar", Some(ProgrammingTestSuite(List(public, hidden))))
    val variable = exercise.interactionVariable
    val source = variable.currentValue
    val publicResult = GradingResult(public, source, GradingStatus.Correct(), Some(feedback), None, date)
    val hiddenResult = GradingResult(hidden, source, GradingStatus.Correct(),
      Some(LanguageMap.universalMap[HumanLanguage]("SECRET-HIDDEN-FEEDBACK")), None, LocalDateTime.parse("1999-01-01T01:02:03"))
    variable.updateHistory(_.addGradings(List(publicResult, hiddenResult)), ExecutionContextInformation.default)
    val sidebar = InteractionInfoSidebar(exercise, None, GradingReporter(exercise)).getDomElement()
    val previous = fullInfo.signals.currentLanguage.now()
    fullInfo.usageControl.changeLanguage(German)
    val close = mount(sidebar)
    try {
      val group = sidebar.ref.querySelector(".feedback-status--correct")
      group.setAttribute("open", "")
      assert(sidebar.ref.textContent.contains("Öffentliche Rückmeldung"))
      assert(sidebar.ref.textContent.contains("2026-11-15 12:30:00"))
      assert(!sidebar.ref.outerHTML.contains("SECRET-HIDDEN"))
      assert(!sidebar.ref.textContent.contains("1999-01-01"))
      assertEquals(sidebar.ref.querySelectorAll(".feedback-results-table tbody tr").length, 1)
      fullInfo.usageControl.changeLanguage(English)
      assert(sidebar.ref.textContent.contains("Public feedback"))
      val changed = publicResult.copy(gradingStatus = GradingStatus.Incorrect(), gradingTime = date.plusMinutes(1))
      variable.updateHistory(_.addGrading(changed), ExecutionContextInformation.default)
      assert(sidebar.ref.querySelector(".feedback-status--correct") eq group)
      assert(group.hasAttribute("open"))
      assertEquals(sidebar.ref.querySelectorAll(".feedback-status--incorrect tbody tr").length, 1)
    } finally { close(); fullInfo.usageControl.changeLanguage(previous) }
  }

  test("sidebar never presents old feedback as a result for a changed answer") {
    val test = grader("visible")
    val exercise = ProgrammingExercise("feedback-current-answer", Some(ProgrammingTestSuite(List(test))))
    val variable = exercise.interactionVariable
    variable.updateHistory(_.addGrading(GradingResult(test, variable.currentValue, GradingStatus.Correct(),
      Some(LanguageMap.universalMap[HumanLanguage]("FEEDBACK-FOR-OLD-ANSWER")))), ExecutionContextInformation.default)
    val sidebar = InteractionInfoSidebar(exercise, None, GradingReporter(exercise)).getDomElement()
    val close = mount(sidebar)
    try {
      assert(sidebar.ref.textContent.contains("FEEDBACK-FOR-OLD-ANSWER"))
      val next = it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(Python, "forward(20)")
      variable.updateHistory(_.addState(it.evadid.workbook.interaction.variable.InteractionVariableState(
        next, it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR, date.plusHours(1))), ExecutionContextInformation.default)
      assert(!sidebar.ref.textContent.contains("FEEDBACK-FOR-OLD-ANSWER"))
      assertEquals(sidebar.ref.querySelectorAll(".feedback-status--neutral tbody tr").length, 1)
      assertEquals(sidebar.ref.querySelector(".feedback-status--neutral .feedback-date").textContent, "—")
    } finally close()
  }
  test("available hints open automatically and disabled interactions cannot request feedback") {
    import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading
    import TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
    import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
    import it.evadid.workbook.abstractions.FeedbackEntity
    import it.evadid.core.datastructures.chat.MessengerModel
    val exercise = TextInputWithScaffoldingAndGrading("hint-sidebar", LanguageMapContentId("test/task"),
      contentGrading = Nil, contentScaffolding = Some(ContentForLlmInteraction(LanguageMapContentId("test/hint"), Map.empty)))
    val saved = ScaffoldingResult[String, MessengerModel]("", ScaffoldingStatus.ScaffoldingFeedback(
      LanguageMap.universalMap[HumanLanguage]("PUBLIC-HINT")), FeedbackEntity.TestEntity(), None, date)
    exercise.interactionVariable.updateHistory(_.addScaffolding(saved), ExecutionContextInformation.default)
    val sidebar = InteractionInfoSidebar(exercise, None, GradingReporter(exercise)).getDomElement()
    val close = mount(sidebar)
    try {
      assert(sidebar.ref.querySelector(".feedback-section").hasAttribute("open"))
      assert(sidebar.ref.querySelector(".feedback-text").textContent.contains("PUBLIC-HINT"))
      val request = sidebar.ref.querySelector("button").asInstanceOf[org.scalajs.dom.HTMLButtonElement]
      assert(!request.disabled)
      exercise.isDisabledState.set(true)
      assert(request.disabled)
    } finally close()
  }

}
