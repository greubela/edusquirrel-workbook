package it.evadid.homepage.workbook

import it.evadid.core.util.UtcTime

import it.evadid.workbook.session.{AllWorkbookInfo, WorkbookConfig}
import it.evadid.core.datastructures.language.WorkbookContent

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.TextInputWithScaffoldingAndGradingRenderer
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionFeedbackPanel
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.homepage.control.info.interaction.GradingReporter
import it.evadid.workbook.history.InteractionVariableState
import it.evadid.workbook.sync.UpdateImportance
import java.time.LocalDateTime
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus}
import it.evadid.workbook.interactions.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.workbook.interactions.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction


class TextInputFeedbackRendererSpec extends EditorDomSuite {
  import com.raquo.laminar.api.L.*
  import it.evadid.homepage.control.model.*

  private def hintExercise(id: String) = TextInputWithScaffoldingAndGrading(id, LanguageMapContentId("test/task"),
    contentGrading = Nil, contentScaffolding = Some(ContentForLlmInteraction(LanguageMapContentId("test/hint"), Map.empty)))

  test("the below-exercise panel restores hints without grading and preserves a collapsed hint across language changes") {
    val previous = fullInfo.signals.currentLanguage.now()
    fullInfo.displayControl.changeLanguage(English)
    val exercise = hintExercise("renderer-saved-hint")
    inOpenWorkbook(exercise) {
      val content = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "English hint\nSecond line", German -> "Deutscher Hinweis"))
      fullInfo.interactions.updateHistory(exercise)(_.addScaffolding(ScaffoldingResult[String, MessengerModel]("",
        ScaffoldingStatus.ScaffoldingFeedback(content), FeedbackEntity.LlmEntity, None, UtcTime.now())))
      val element = TextInputWithScaffoldingAndGradingRenderer.renderAppElement(fullInfo, exercise).getDomElement()
      val close = mount(element)
      try {
        val panel = element.ref.querySelector(".feedback-panel .interaction-hint-panel")
        assert(!panel.hasAttribute("hidden"))
        assert(panel.querySelector(".ai-badge") != null)
        assertEquals(panel.querySelector(".feedback-hint-text").textContent, "English hint\nSecond line")
        val details = panel
        assert(details.hasAttribute("open"))
        details.removeAttribute("open")
        fullInfo.displayControl.changeLanguage(German)
        assertEquals(panel.querySelector(".feedback-hint-text").textContent, "Deutscher Hinweis")
        assert(!details.hasAttribute("open"))
        assertEquals(element.ref.querySelectorAll(".feedback-entry").length, 0)
      } finally { close(); fullInfo.displayControl.changeLanguage(previous) }
    }
  }

  test("the shared bottom panel shows only completed hints for the current answer") {
    val exercise = hintExercise("renderer-current-hint")
    inOpenWorkbook(exercise) {
      val context = fullInfo.workbookContextInformation
      val reporter = GradingReporter(exercise, fullInfo.interactions)
      val element = InteractionFeedbackPanel(fullInfo, exercise, reporter).getDomElement()
      val close = mount(element)
      val time = UtcTime.now()
      def save(status: ScaffoldingStatus, offset: Long): Unit = fullInfo.interactions.updateHistory(exercise)(_.addScaffolding(
        ScaffoldingResult[String, MessengerModel]("", status, FeedbackEntity.TestEntity, None, time.plusSeconds(offset))))
      try {
        val panel = element.ref.querySelector(".interaction-hint-panel")
        val button = element.ref.querySelector("button.scaffolding-info").asInstanceOf[org.scalajs.dom.HTMLButtonElement]
        assert(panel.hasAttribute("hidden"))
        save(ScaffoldingStatus.ScaffoldingRequestedButNotFinished(), 1)
        assert(button.disabled)
        assertEquals(button.getAttribute("aria-busy"), "true")
        assert(button.querySelector(".feedback-icon-spin") != null)
        assert(panel.hasAttribute("hidden"))
        save(ScaffoldingStatus.ScaffoldingError(it.evadid.distribution.command.SerializedException("PRIVATE ERROR")), 2)
        assert(!button.disabled)
        assert(button.classList.contains("feedback-status--error"))
        assert(button.querySelector(".feedback-icon-spin") == null)
        assert(panel.hasAttribute("hidden"))
        assert(!element.ref.textContent.contains("PRIVATE ERROR"))
        save(ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("CURRENT HINT")), 3)
        assert(!panel.hasAttribute("hidden"))
        assertEquals(panel.querySelector(".feedback-hint-text").textContent, "CURRENT HINT")
        assert(panel.classList.contains("feedback-status--correct"))
        assert(panel.querySelector(".ai-badge") == null)
        fullInfo.interactions.updateHistory(exercise)(_.addState(InteractionVariableState("changed answer", UpdateImportance.MAJOR, time.plusSeconds(4))))
        assert(panel.hasAttribute("hidden"))
        assertEquals(panel.querySelector(".feedback-hint-text").textContent, "")
        save(ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("LATE OLD HINT")), 5)
        assert(panel.hasAttribute("hidden"))
        fullInfo.interactions.updateHistory(exercise)(_.addState(InteractionVariableState("", UpdateImportance.MAJOR, time.plusSeconds(6))))
        assert(!panel.hasAttribute("hidden"))
        assertEquals(panel.querySelector(".feedback-hint-text").textContent, "LATE OLD HINT")
      } finally close()
    }
  }

  test("each criterion renders its own saved feedback instead of repeating the first result") {
    val exercise = TextInputWithScaffoldingAndGrading("renderer-criteria", LanguageMapContentId("test/task"),
      contentGrading = List("first", "second").map(id => ContentForLlmInteraction(LanguageMapContentId(s"test/$id"), Map.empty)))
    inOpenWorkbook(exercise) {
      val results = exercise.gradingElements.zipWithIndex.map { (grader, index) =>
        GradingResult[String, MessengerModel](grader, "", if index == 0 then GradingStatus.Correct else GradingStatus.PartiallyCorrect, Some(WorkbookContent(LanguageMap.universalMap[HumanLanguage](s"Feedback for criterion $index"))), None, UtcTime.now())
      }
      fullInfo.interactions.updateHistory(exercise)(_.addGradings(results))
      val element = TextInputWithScaffoldingAndGradingRenderer.renderAppElement(fullInfo, exercise).getDomElement()
      val close = mount(element)
      try {
        val displayed = element.ref.querySelectorAll(".feedback-entry")
        assertEquals(displayed.length, 2)
        assertEquals(displayed(0).querySelector(".feedback-text").textContent, "Feedback for criterion 0")
        assertEquals(displayed(1).querySelector(".feedback-text").textContent, "Feedback for criterion 1")
        assert(displayed(1).classList.contains("feedback-status--partial"))
        assert(!element.ref.textContent.contains("Correct"))
        assert(!element.ref.textContent.contains("PartiallyCorrect"))
      } finally close()
    }
  }

  test("feedback for another answer is hidden and localization uses the shared fallback") {
    val exercise = TextInputWithScaffoldingAndGrading("renderer-stale", LanguageMapContentId("test/task"),
      contentGrading = List(ContentForLlmInteraction(LanguageMapContentId("test/check"), Map.empty)))
    inOpenWorkbook(exercise) {
      val grader = exercise.gradingElements.head
      val otherLanguage = if fullInfo.signals.currentLanguage.now() == English then German else English
      val wrongAnswer = GradingResult[String, MessengerModel](grader, "another answer", GradingStatus.Correct, Some(WorkbookContent(LanguageMap.universalMap("Wrong answer feedback"))), None, UtcTime.now())
      val wrongLanguage = wrongAnswer.copy(gradedState = "", feedbackInformation = Some(WorkbookContent(
        LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(otherLanguage -> "Wrong language feedback")))))
      val element = TextInputWithScaffoldingAndGradingRenderer.renderAppElement(fullInfo, exercise).getDomElement()
      val close = mount(element)
      try {
        // Use the correct event-based API - addGrading creates a GradingCompletedEvent
        fullInfo.interactions.updateHistory(exercise)(_.addGrading(wrongAnswer))
        assertEquals(element.ref.querySelectorAll(".feedback-entry").length, 1)
        assert(!element.ref.textContent.contains("Wrong answer feedback"))
        fullInfo.interactions.updateHistory(exercise)(_.addGrading(wrongLanguage))
        assertEquals(element.ref.querySelectorAll(".feedback-entry").length, 1)
        assert(element.ref.textContent.contains("Wrong language feedback"))
      } finally close()
    }
  }
}
