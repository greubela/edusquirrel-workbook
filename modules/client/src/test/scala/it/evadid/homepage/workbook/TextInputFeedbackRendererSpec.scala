package it.evadid.homepage.workbook

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.TextInputWithScaffoldingAndGradingRenderer
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus}
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
import it.evadid.workbook.interaction.variable.InteractionVariableGradingHistory

class TextInputFeedbackRendererSpec extends EditorDomSuite {
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
      exercise.interactionVariable.updateHistory(_.copy(historyGrading = new InteractionVariableGradingHistory(
        Map("" -> GradedState("", Map(grader -> wrongAnswer))))), fullInfo.workbookContextInformation)
      assertEquals(element.ref.querySelectorAll(".text-input-feedback__result").length, 0)
      exercise.interactionVariable.updateHistory(_.copy(historyGrading = new InteractionVariableGradingHistory(
        Map("" -> GradedState("", Map(grader -> wrongLanguage))))), fullInfo.workbookContextInformation)
      assertEquals(element.ref.querySelectorAll(".text-input-feedback__result").length, 1)
      assert(element.ref.textContent.contains("Wrong language feedback"))
    } finally close()
  }
}
