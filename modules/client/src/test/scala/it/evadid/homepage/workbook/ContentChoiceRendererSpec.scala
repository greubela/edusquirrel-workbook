package it.evadid.homepage.workbook

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId, WorkbookContent}
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.choice.WorkbookContentChoiceInteractionRenderer
import it.evadid.workbook.abstractions.grading.{GraderRunningBehavior, GradingStatus}
import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction
import WorkbookContentChoiceInteraction.*
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class ContentChoiceRendererSpec extends EditorDomSuite {
  private val previous = Var(Option.empty[AllWorkbookInfo])
  private val language = Var[HumanLanguage](English)
  override def beforeEach(context: BeforeEach): Unit = {
    previous.set(fullInfo.homepageInfoNow().workbookInfo)
    language.set(fullInfo.signals.currentLanguage.now())
    fullInfo.displayControl.changeLanguage(English)
  }
  override def afterEach(context: AfterEach): Unit = {
    fullInfo.workbookNavigationControl.changeWorkbook(previous.now())
    fullInfo.displayControl.changeLanguage(language.now())
  }
  private def load(exercise: WorkbookContentChoiceInteraction): Unit = {
    val section = WorkbookSection("choices", WorkbookSection.WorkbookSectionMetadata(LanguageMapContentId("test/section")), Seq(exercise))
    val workbook = Workbook("choices", Workbook.WorkbookMetadata(Set.empty, Set.empty, LanguageMapContentId("test/title"), Seq(English, German)), Seq(section))
    fullInfo.workbookNavigationControl.changeWorkbook(Some(AllWorkbookInfo(workbook, WorkbookConfig(Some(section)), Map.empty)))
  }
  test("content options localize without changing selections and use the shared grading panel") {
    val options = Seq("First" -> "Erste", "Second" -> "Zweite").map { (en, de) =>
      ChoiceOptionWorkbookContent(WorkbookContent(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> en, German -> de))),
        Some(GradingForChoice(GradingStatus.Correct(), LanguageMapContentId("digitalWorkbooks/correct"))))
    }
    val exercise = WorkbookContentChoiceInteraction("renderer-choice", ChoiceConfig(LanguageMapContentId("test/prompt"), Some(true), GraderRunningBehavior.RUN_ALWAYS), options)
    load(exercise)
    val element = WorkbookContentChoiceInteractionRenderer.renderAppElement(exercise).getDomElement()
    val close = mount(element)
    Future.unit.flatMap { _ =>
      val inputs = element.ref.querySelectorAll("input[type=checkbox]")
      assertEquals(inputs.length, 2)
      inputs(0).asInstanceOf[dom.html.Input].click()
      inputs(1).asInstanceOf[dom.html.Input].click()
      val answer = exercise.interactionVariable.currentValue
      assertEquals(answer.selectedOptions.toSet, options.toSet)
      fullInfo.displayControl.changeLanguage(German)
      assert(element.ref.textContent.contains("Erste"), element.ref.textContent)
      assertEquals(exercise.interactionVariable.currentValue, answer)
      assertEquals(element.ref.querySelectorAll(".choice-feedback").length, 0)
      assertEquals(element.ref.querySelectorAll(".feedback-entry").length, 1)
      exercise.interactionVariable.control.getOrCalculateGrading(fullInfo.workbookContextInformation).map { graded =>
        assert(graded.testsWithResults.forall(_.gradingStatus.isCorrect))
      }
    }.andThen { case _ => close() }
  }
}
