package it.evadid.homepage.workbook

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.IconGradingAndScaffolding
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction

class InteractionInfoPanelSpec extends EditorDomSuite {
  private def exercise = TextInputWithScaffoldingAndGrading("info-icons", LanguageMapContentId("test/task"),
    contentGrading = List("first", "second").map(id => ContentForLlmInteraction(LanguageMapContentId(s"test/$id"), Map.empty)),
    contentScaffolding = Some(ContentForLlmInteraction(LanguageMapContentId("test/hint"), Map.empty)))

  test("grading icons count statuses and never expose private grading details") {
    val interaction = exercise
    val icons = IconGradingAndScaffolding(fullInfo, LaminarRenderHelper.singleton, interaction)
    val cases = List(
      (List(GradingStatus.Correct(), GradingStatus.Correct()), "grading-icon-thumbs-up-path"),
      (List(GradingStatus.Correct(), GradingStatus.Incorrect()), "grading-icon-hand-path"),
      (List(GradingStatus.Incorrect(), GradingStatus.Incorrect()), "grading-icon-thumbs-down-path"))
    cases.foreach { (statuses, expected) =>
      val results: Map[WorkbookInteractionGrader[String, MessengerModel], GradingResult[String, MessengerModel]] = interaction.gradingElements.zip(statuses).map { (grader, status) =>
        grader -> GradingResult(grader, "", status, Some(LanguageMap.universalMap[HumanLanguage]("PRIVATE_FEEDBACK")))
      }.toMap
      val element = icons.deriveGradingSymbol(GradedState("", results))
      val close = mount(element)
      try {
        assert(element.ref.querySelector(s".$expected") != null, clues(element.ref.outerHTML))
        assert(!element.ref.textContent.contains("PRIVATE_FEEDBACK"))
        assertEquals(element.ref.querySelectorAll("[style]").length, 0)
      } finally close()
    }
  }

  test("completed scaffolding remains available when the display language changes") {
    val interaction = exercise
    val icons = IconGradingAndScaffolding(fullInfo, LaminarRenderHelper.singleton, interaction)
    val feedback = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Hinweis", English -> "Hint"))
    val saved = ScaffoldingResult[String, Any]("", ScaffoldingStatus.ScaffoldingFeedback(feedback), FeedbackEntity.TestEntity(), None)
    val element = icons.deriveScaffoldingSymbol(Some(saved))
    val originalLanguage = fullInfo.signals.currentLanguage.now()
    val close = mount(element)
    try {
      fullInfo.usageControl.changeLanguage(German)
      assertEquals(element.ref.getAttribute("data-tooltip"), "Hinweis")
      fullInfo.usageControl.changeLanguage(English)
      assertEquals(element.ref.getAttribute("data-tooltip"), "Hint")
      assert(element.ref.querySelector(".scaffolding-info-svg-fully") != null, clues(element.ref.outerHTML))
      assertEquals(saved.scaffoldingStatus, ScaffoldingStatus.ScaffoldingFeedback(feedback))
    } finally { fullInfo.usageControl.changeLanguage(originalLanguage); close() }
  }
}
