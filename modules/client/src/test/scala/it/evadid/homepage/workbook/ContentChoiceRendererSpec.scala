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
        Some(GradingForChoice(GradingStatus.Correct, LanguageMapContentId("digitalWorkbooks/correct"))))
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

  test("only a check grades; a failed check marks the wrong option until the next change and the solved strip counts the attempts") {
    val options = Seq(true, false).zipWithIndex.map { (correct, index) =>
      ChoiceOptionWorkbookContent(WorkbookContent(LanguageMap.universalMap[HumanLanguage](s"Option $index")),
        Some(GradingForChoice(if correct then GradingStatus.Correct else GradingStatus.Incorrect, LanguageMapContentId("digitalWorkbooks/correct"))))
    }
    val exercise = WorkbookContentChoiceInteraction("checked-choice", ChoiceConfig(LanguageMapContentId("test/prompt"), Some(false), GraderRunningBehavior.RUN_ONLY_MANUALLY), options)
    load(exercise)
    val element = WorkbookContentChoiceInteractionRenderer.renderAppElement(exercise).getDomElement()
    val close = mount(element)
    val control = exercise.interactionVariable.control
    val context = fullInfo.workbookContextInformation
    // Selecting starts an automatic grading that skips the manual grader; the check runs it afterwards.
    def check() = control.getOrCalculateGrading(context).flatMap(_ => control.getOrCalculateGrading(context, forceGrading = true))
    def option(index: Int) = element.ref.querySelectorAll(".choice-interaction label")(index)
    def marked(index: Int) = option(index).classList.contains("answer-part--wrong")
    def tracker = Option(element.ref.querySelector(".interaction-info__attempts"))
    def select(index: Int) = option(index).querySelector("input").asInstanceOf[dom.html.Input].click()
    Future.unit.flatMap { _ =>
      select(1)
      control.getOrCalculateGrading(context)
    }.flatMap { unchecked =>
      assertEquals(unchecked.totalEvaluation(), GradingStatus.NotRequested)
      assert(!marked(1))
      check()
    }.flatMap { failed =>
      assertEquals(failed.totalEvaluation(), GradingStatus.Incorrect)
      assert(marked(1))
      assert(tracker.isEmpty)
      select(0)
      assert(!marked(1), "the mark ends with the next interaction")
      check()
    }.map { solved =>
      assertEquals(solved.totalEvaluation(), GradingStatus.Correct)
      assert(!marked(0) && !marked(1))
      assertEquals(tracker.map(_.querySelector(".interaction-info__attempts-count").textContent), Some("2"))
    }.andThen { case _ => close() }
  }
}
