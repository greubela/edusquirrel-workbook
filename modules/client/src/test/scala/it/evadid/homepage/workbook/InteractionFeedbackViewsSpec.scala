package it.evadid.homepage.workbook

import it.evadid.core.util.UtcTime

import it.evadid.core.datastructures.language.WorkbookContent

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.workbook.reports.GradingSummary
import it.evadid.workbook.reports.GradingSummary.Group
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel.InteractionInfoSidebar
import it.evadid.vm.test.ProgrammingTestSuite
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.interactions.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingUnitTestGrader}
import it.evadid.workbook.interactions.programming.state.ProgrammingState
import it.evadid.workbook.abstractions.ExecutionContextInformation
import it.evadid.homepage.control.info.interaction.GradingReporter
import java.time.LocalDateTime
import com.raquo.laminar.api.L.div

class InteractionFeedbackViewsSpec extends EditorDomSuite {
  private val date = LocalDateTime.parse("2026-11-15T12:30:00")
  private def grader(id: String, hidden: Boolean = false) = ProgrammingUnitTestGrader(id, s"assert '$id'",
    if hidden then GradingImportance.HIDDEN_TEST else GradingImportance.REQUIRED_TEST)
  private def feedback = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Öffentliche Rückmeldung", English -> "Public feedback"))

  test("summary counts public and hidden outcomes, groups partial results, and counts missing authored tests") {
    val graders = List(grader("ok"), grader("hidden-ok", true), grader("partial"), grader("hidden-bad", true),
      grader("pending"), grader("error", true), grader("unrequested"), grader("missing"))
    val statuses = List(GradingStatus.Correct, GradingStatus.Correct, GradingStatus.PartiallyCorrect,
      GradingStatus.Incorrect, GradingStatus.NotFinished,
      GradingStatus.TestError(it.evadid.distribution.command.SerializedException("private execution error")), GradingStatus.NotRequested)
    val state: ProgrammingState = it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw(Python, "forward(10)")
    val results = graders.zip(statuses).map((g, status) => GradingResult(g, state, status))
    val obsolete = GradingResult(grader("removed-grader"), state, GradingStatus.Correct)
    val stale = GradingResult(graders.last,
      it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw(Python, "forward(999)"), GradingStatus.Correct)
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
    inOpenWorkbook(exercise) {
      val source = fullInfo.interactions.currentValue(exercise)
      val publicResult = GradingResult(public, source, GradingStatus.Correct, Some(WorkbookContent(feedback)), None, date)
      val hiddenResult = GradingResult(hidden, source, GradingStatus.Correct, Some(WorkbookContent(LanguageMap.universalMap[HumanLanguage]("SECRET-HIDDEN-FEEDBACK"))), None, LocalDateTime.parse("1999-01-01T01:02:03"))
      fullInfo.interactions.updateHistory(exercise)(_.addGradings(List(publicResult, hiddenResult)))
      val sidebar = InteractionInfoSidebar(fullInfo, exercise,GradingReporter(exercise, fullInfo.interactions)).getDomElement()
      val previous = fullInfo.signals.currentLanguage.now()
      fullInfo.displayControl.changeLanguage(German)
      val close = mount(sidebar)
      try {
        val details = sidebar.ref.querySelector("details.feedback-grading")
        assert(details.hasAttribute("open"), "the sidebar shows results expanded")
        assert(sidebar.ref.textContent.contains("Öffentliche Rückmeldung"))
        val time = sidebar.ref.querySelector(".feedback-entry .feedback-date")
        assertEquals(time.textContent, "12:30")
        assertEquals(time.getAttribute("title"), "2026-11-15 12:30:00")
        assert(!sidebar.ref.outerHTML.contains("SECRET-HIDDEN"))
        assert(!sidebar.ref.outerHTML.contains("1999-01-01"))
        val rows = sidebar.ref.querySelectorAll(".feedback-entry")
        assertEquals(rows.length, 2)
        assert(rows(1).textContent.toLowerCase.contains("gradinghiddentest"), clues(rows(1).textContent)) // the hidden-test label, not its description
        assertEquals(rows(1).querySelectorAll(".feedback-date, .feedback-text").length, 0)
        fullInfo.displayControl.changeLanguage(English)
        assert(sidebar.ref.textContent.contains("Public feedback"))
        details.removeAttribute("open")
        val changed = publicResult.copy(gradingStatus = GradingStatus.Incorrect, gradingTime = date.plusMinutes(1))
        fullInfo.interactions.updateHistory(exercise)(_.addGrading(changed))
        assert(sidebar.ref.querySelector("details.feedback-grading") eq details)
        assert(!details.hasAttribute("open"), "a result update keeps the reader's disclosure choice")
        assertEquals(sidebar.ref.querySelectorAll(".feedback-entry.feedback-status--incorrect").length, 1)
        assertEquals(sidebar.ref.querySelector(".feedback-score-chip").textContent, "1/2")
      } finally { close(); fullInfo.displayControl.changeLanguage(previous) }
    }
  }

  test("overview panel and editor sidebar render the same results from the shared reporter") {
    val tests = List(grader("first"), grader("second"), grader("secret", true))
    val exercise = ProgrammingExercise("feedback-shared-source", Some(ProgrammingTestSuite(tests)))
    inOpenWorkbook(exercise) {
      val reporter = GradingReporter(exercise, fullInfo.interactions)
      val overview = it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo
        .InteractionFeedbackPanel(fullInfo, exercise, reporter).getDomElement()
      val sidebar = InteractionInfoSidebar(fullInfo, exercise,reporter).getDomElement()
      val close = mount(div(overview, sidebar))
      def rows(root: org.scalajs.dom.Element) = (0 until root.querySelectorAll(".feedback-entry").length)
        .map(i => root.querySelectorAll(".feedback-entry")(i)).map(row => row.getAttribute("class") -> row.textContent)
      try {
        assertEquals(rows(overview.ref).size, 3)
        assert(rows(overview.ref).forall(_._1.contains("feedback-status--neutral")))
        val details = overview.ref.querySelector(".feedback-details")
        val toggle = overview.ref.querySelector("button.feedback-details-toggle").asInstanceOf[org.scalajs.dom.HTMLButtonElement]
        assert(details.hasAttribute("hidden"), "unchecked results start collapsed")
        assertEquals(toggle.getAttribute("aria-expanded"), "false")
        val statuses = List(GradingStatus.Correct, GradingStatus.PartiallyCorrect, GradingStatus.Incorrect)
        fullInfo.interactions.updateHistory(exercise)(_.addGradings(tests.zip(statuses).map((test, status) =>
          GradingResult(test, fullInfo.interactions.currentValue(exercise), status, Some(WorkbookContent(feedback)), None, date))))
        assertEquals(rows(overview.ref), rows(sidebar.ref))
        assertEquals(rows(overview.ref).map(_._1.split(' ').last), Seq("feedback-status--correct", "feedback-status--partial", "feedback-status--incorrect"))
        assertEquals(overview.ref.querySelector(".feedback-score-chip").textContent, sidebar.ref.querySelector(".feedback-score-chip").textContent)
        assertEquals(overview.ref.querySelectorAll(".feedback-group-chips .feedback-chip").length, 3, "correct, partial and incorrect are counted separately")
        assert(details.hasAttribute("hidden"), "arriving results do not expand the overview")
        toggle.click()
        assert(!details.hasAttribute("hidden"), "the reader expands the results manually")
        assertEquals(toggle.getAttribute("aria-expanded"), "true")
        toggle.click()
        assert(details.hasAttribute("hidden"))
        assert(sidebar.ref.querySelector("details.feedback-grading").hasAttribute("open"), "the sidebar starts expanded")
      } finally close()
    }
  }

  test("sidebar never presents old feedback as a result for a changed answer") {
    val test = grader("visible")
    val exercise = ProgrammingExercise("feedback-current-answer", Some(ProgrammingTestSuite(List(test))))
    inOpenWorkbook(exercise) {
      fullInfo.interactions.updateHistory(exercise)(_.addGrading(GradingResult(test, fullInfo.interactions.currentValue(exercise), GradingStatus.Correct, Some(WorkbookContent(LanguageMap.universalMap[HumanLanguage]("FEEDBACK-FOR-OLD-ANSWER"))), None, UtcTime.now())))
      val sidebar = InteractionInfoSidebar(fullInfo, exercise,GradingReporter(exercise, fullInfo.interactions)).getDomElement()
      val close = mount(sidebar)
      try {
        assert(sidebar.ref.textContent.contains("FEEDBACK-FOR-OLD-ANSWER"))
        val next = it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw(Python, "forward(20)")
        fullInfo.interactions.updateHistory(exercise)(_.addState(it.evadid.workbook.history.InteractionVariableState(
          next, it.evadid.workbook.sync.UpdateImportance.MAJOR, date.plusHours(1))))
        assert(!sidebar.ref.textContent.contains("FEEDBACK-FOR-OLD-ANSWER"))
        assertEquals(sidebar.ref.querySelectorAll(".feedback-entry.feedback-status--neutral").length, 1)
        assertEquals(sidebar.ref.querySelectorAll(".feedback-entry .feedback-date").length, 0)
      } finally close()
    }
  }
  test("available hints open automatically and disabled interactions cannot request feedback") {
    import it.evadid.workbook.interactions.gpt.TextInputWithScaffoldingAndGrading
    import TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
    import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
    import it.evadid.workbook.abstractions.FeedbackEntity
    import it.evadid.core.datastructures.chat.MessengerModel
    val exercise = TextInputWithScaffoldingAndGrading("hint-sidebar", LanguageMapContentId("test/task"),
      contentGrading = Nil, contentScaffolding = Some(ContentForLlmInteraction(LanguageMapContentId("test/hint"), Map.empty)))
    inOpenWorkbook(exercise) {
      val saved = ScaffoldingResult[String, MessengerModel]("", ScaffoldingStatus.ScaffoldingFeedback(
        LanguageMap.universalMap[HumanLanguage]("PUBLIC-HINT")), FeedbackEntity.TestEntity, None, date)
      fullInfo.interactions.updateHistory(exercise)(_.addScaffolding(saved))
      val sidebar = InteractionInfoSidebar(fullInfo, exercise,GradingReporter(exercise, fullInfo.interactions)).getDomElement()
      val close = mount(sidebar)
      try {
        assert(sidebar.ref.querySelector(".interaction-hint-panel").hasAttribute("open"))
        assert(sidebar.ref.querySelector(".feedback-text").textContent.contains("PUBLIC-HINT"))
        val request = sidebar.ref.querySelector("button.scaffolding-info").asInstanceOf[org.scalajs.dom.HTMLButtonElement]
        assert(!request.disabled)
        exercise.isDisabledState.set(true)
        assert(request.disabled)
      } finally close()
    }
  }

}
