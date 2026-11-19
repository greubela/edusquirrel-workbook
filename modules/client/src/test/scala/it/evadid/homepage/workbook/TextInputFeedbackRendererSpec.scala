package it.evadid.homepage.workbook

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.TextInputWithScaffoldingAndGradingRenderer
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.linePanelBottom.GradingInfoLine
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.meta.InteractionVariableGradingReporter
import it.evadid.workbook.interaction.variable.InteractionVariableState
import it.evadid.workbook.interaction.sync.UpdateImportance
import java.time.LocalDateTime
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus}
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction


class TextInputFeedbackRendererSpec extends EditorDomSuite {
  private def hintExercise(id: String) = TextInputWithScaffoldingAndGrading(id, LanguageMapContentId("test/task"),
    contentGrading = Nil, contentScaffolding = Some(ContentForLlmInteraction(LanguageMapContentId("test/hint"), Map.empty)))

  test("the below-exercise panel restores hints without grading and preserves a collapsed hint across language changes") {
    val previous = fullInfo.signals.currentLanguage.now()
    fullInfo.usageControl.changeLanguage(English)
    val exercise = hintExercise("renderer-saved-hint")
    val content = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "English hint\nSecond line", German -> "Deutscher Hinweis"))
    exercise.interactionVariable.updateHistory(_.addScaffolding(ScaffoldingResult[String, MessengerModel]("",
      ScaffoldingStatus.ScaffoldingFeedback(content), FeedbackEntity.TestEntity(), None, LocalDateTime.now())), fullInfo.workbookContextInformation)
    val element = TextInputWithScaffoldingAndGradingRenderer.renderAppElement(exercise).getDomElement()
    val close = mount(element)
    try {
      val panel = element.ref.querySelector(".interaction-scaffolding .interaction-hint-panel")
      assert(!panel.hasAttribute("hidden"))
      assertEquals(panel.querySelector("p").textContent, "English hint\nSecond line")
      val details = panel.querySelector("details")
      assert(details.hasAttribute("open"))
      details.removeAttribute("open")
      fullInfo.usageControl.changeLanguage(German)
      assertEquals(panel.querySelector("p").textContent, "Deutscher Hinweis")
      assert(!details.hasAttribute("open"))
      assertEquals(element.ref.querySelectorAll(".text-input-feedback__result").length, 0)
    } finally { close(); fullInfo.usageControl.changeLanguage(previous) }
  }

  test("the shared bottom panel shows only completed hints for the current answer") {
    val exercise = hintExercise("renderer-current-hint")
    val variable = exercise.interactionVariable
    val context = fullInfo.workbookContextInformation
    val reporter = InteractionVariableGradingReporter(exercise)
    val element = GradingInfoLine(exercise, reporter).getDomElement()
    val close = mount(element)
    val time = LocalDateTime.now()
    def save(status: ScaffoldingStatus, offset: Long): Unit = variable.updateHistory(_.addScaffolding(
      ScaffoldingResult[String, MessengerModel]("", status, FeedbackEntity.TestEntity(), None, time.plusSeconds(offset))), context)
    try {
      val panel = element.ref.querySelector(".interaction-hint-panel")
      assert(panel.hasAttribute("hidden"))
      save(ScaffoldingStatus.ScaffoldingRequestedButNotFinished(), 1)
      assert(panel.hasAttribute("hidden"))
      save(ScaffoldingStatus.ScaffoldingError(it.evadid.distribution.command.SerializedException("PRIVATE ERROR")), 2)
      assert(panel.hasAttribute("hidden"))
      assert(!element.ref.textContent.contains("PRIVATE ERROR"))
      save(ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("CURRENT HINT")), 3)
      assert(!panel.hasAttribute("hidden"))
      assertEquals(panel.querySelector("p").textContent, "CURRENT HINT")
      variable.updateHistory(_.addState(InteractionVariableState("changed answer", UpdateImportance.MAJOR, time.plusSeconds(4))), context)
      assert(panel.hasAttribute("hidden"))
      assertEquals(panel.querySelector("p").textContent, "")
      save(ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("LATE OLD HINT")), 5)
      assert(panel.hasAttribute("hidden"))
      variable.updateHistory(_.addState(InteractionVariableState("", UpdateImportance.MAJOR, time.plusSeconds(6))), context)
      assert(!panel.hasAttribute("hidden"))
      assertEquals(panel.querySelector("p").textContent, "LATE OLD HINT")
    } finally close()
  }

  test("each criterion renders its own saved feedback instead of repeating the first result") {
    val exercise = TextInputWithScaffoldingAndGrading("renderer-criteria", LanguageMapContentId("test/task"),
      contentGrading = List("first", "second").map(id => ContentForLlmInteraction(LanguageMapContentId(s"test/$id"), Map.empty)))
    val results = exercise.gradingElements.zipWithIndex.map { (grader, index) =>
      GradingResult[String, MessengerModel](grader, "", if index == 0 then GradingStatus.Correct() else GradingStatus.PartiallyCorrect(),
        Some(LanguageMap.universalMap[HumanLanguage](s"Feedback for criterion $index")))
    }
    exercise.interactionVariable.updateHistory(_.addGradings(results), fullInfo.workbookContextInformation)
    val element = TextInputWithScaffoldingAndGradingRenderer.renderAppElement(exercise).getDomElement()
    val close = mount(element)
    try {
      val displayed = element.ref.querySelectorAll(".text-input-feedback__result")
      assertEquals(displayed.length, 2)
      assertEquals(displayed(0).querySelector("p").textContent, "Feedback for criterion 0")
      assertEquals(displayed(1).querySelector("p").textContent, "Feedback for criterion 1")
      assert(!element.ref.textContent.contains("Correct()"))
      assert(!element.ref.textContent.contains("PartiallyCorrect()"))
    } finally close()
  }

  test("feedback for another answer is hidden while an available language remains usable") {
    val exercise = TextInputWithScaffoldingAndGrading("renderer-stale", LanguageMapContentId("test/task"),
      contentGrading = List(ContentForLlmInteraction(LanguageMapContentId("test/check"), Map.empty)))
    val grader = exercise.gradingElements.head
    val otherLanguage = if fullInfo.signals.currentLanguage.now() == English then German else English
    val wrongAnswer = GradingResult[String, MessengerModel](grader, "another answer", GradingStatus.Correct(),
      Some(LanguageMap.universalMap("Wrong answer feedback")))
    val wrongLanguage = wrongAnswer.copy(gradedState = "", feedbackInformation = Some(
      LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(otherLanguage -> "Wrong language feedback"))))
    val element = TextInputWithScaffoldingAndGradingRenderer.renderAppElement(exercise).getDomElement()
    val close = mount(element)
    try {
      // Use the correct event-based API - addGrading creates a GradingCompletedEvent
      exercise.interactionVariable.updateHistory(_.addGrading(wrongAnswer), fullInfo.workbookContextInformation)
      assertEquals(element.ref.querySelectorAll(".text-input-feedback__result").length, 1)
      assert(!element.ref.textContent.contains("Wrong answer feedback"))
      exercise.interactionVariable.updateHistory(_.addGrading(wrongLanguage), fullInfo.workbookContextInformation)
      assertEquals(element.ref.querySelectorAll(".text-input-feedback__result").length, 1)
      assert(element.ref.textContent.contains("Wrong language feedback"))
    } finally close()
  }
}
