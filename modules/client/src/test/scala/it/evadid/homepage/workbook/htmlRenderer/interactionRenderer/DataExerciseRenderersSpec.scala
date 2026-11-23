package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{English, Java, Python}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.info.interaction.GradingReporter
import it.evadid.homepage.control.model.{AllWorkbookInfo, WorkbookConfig}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection.DomElementSingle
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.{HtmlProgrammingExerciseFullJavaRenderer, ProgramDataPreview}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.steps.HtmlSortListExerciseRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.HtmlTurtleRecreateShapeRenderer
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.{ExerciseDataView, ProgrammingExerciseFullJava, SortListExercise}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}
import org.scalajs.dom

import scala.concurrent.ExecutionContext

class DataExerciseRenderersSpec extends EditorDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private val previousWorkbook = Var(Option.empty[AllWorkbookInfo])
  override def beforeEach(context: BeforeEach): Unit = previousWorkbook.set(fullInfo.homepageInfoNow().workbookInfo)
  override def afterEach(context: AfterEach): Unit = fullInfo.workbookNavigationControl.changeWorkbook(previousWorkbook.now())
  private def load(elements: WorkbookElement*): Unit = {
    val section = WorkbookSection("data-tests", WorkbookSection.WorkbookSectionMetadata(LanguageMapContentId("test/section")), elements)
    val workbook = Workbook("data-tests", Workbook.WorkbookMetadata(Set.empty, Set.empty, LanguageMapContentId("test/title"), Seq(English)), Seq(section))
    fullInfo.workbookNavigationControl.changeWorkbook(Some(AllWorkbookInfo(workbook, WorkbookConfig(Some(section)), Map.empty)))
  }
  private def text(key: String): Signal[String] = Val(Map("programDataFailed" -> "Failed:", "programDataUnavailable" -> "Unavailable:").getOrElse(key, key))
  private def all(root: dom.Element, selector: String): Seq[String] =
    (0 until root.querySelectorAll(selector).length).map(root.querySelectorAll(selector)(_).textContent)
  private def java(main: String, methods: String = "") =
    ProgrammingStateRaw(Java, s"public class Main {\n$methods\n  public static void main(String[] args) {\n    $main\n  }\n}")

  test("the data preview shows the list after main") {
    val state: Var[ProgrammingState] = Var(java("NumberList.swap(0, 1);"))
    val preview = new ProgramDataPreview(state, ExerciseDataView.NumberList, text).getDomElement()
    val close = mount(preview)
    eventually(all(preview.ref, ".program-data-list__value") == Seq("3", "7", "9", "1", "5"), preview.ref.textContent)
      .map(_ => assertEquals(all(preview.ref, ".program-data-list__index"), Seq("0", "1", "2", "3", "4")))
      .andThen(_ => close())
  }

  test("the sudoku preview distinguishes givens, placed numbers and empty cells") {
    val state: Var[ProgrammingState] = Var(java("Sudoku.set(0, 2, 4);"))
    val preview = new ProgramDataPreview(state, ExerciseDataView.Sudoku, text).getDomElement()
    val close = mount(preview)
    eventually(preview.ref.querySelectorAll(".program-data-sudoku__cell--placed").length == 1, preview.ref.textContent).map { _ =>
      assertEquals(preview.ref.querySelectorAll(".program-data-sudoku td").length, 81)
      assertEquals(preview.ref.querySelectorAll(".program-data-sudoku__cell--given").length, 78)
      assertEquals(all(preview.ref, ".program-data-sudoku__cell--placed"), Seq("4"))
      assertEquals(all(preview.ref, ".program-data-sudoku td").count(_.isEmpty), 2)
    }.andThen(_ => close())
  }

  test("the data preview explains failed runs and programs that cannot run") {
    val state: Var[ProgrammingState] = Var(java("NumberList.get(99);"))
    val preview = new ProgramDataPreview(state, ExerciseDataView.NumberList, text).getDomElement()
    val close = mount(preview)
    (for {
      _ <- eventually(all(preview.ref, ".program-data-preview__problem").exists(_.startsWith("Failed:")), preview.ref.textContent)
      _ = assertEquals(all(preview.ref, ".program-data-list__value").size, 5, "the data stays visible")
      _ = state.set(ProgrammingStateRaw(Java, "public class Main { void broken( }"))
      _ <- eventually(all(preview.ref, ".program-data-preview__problem").exists(_.startsWith("Unavailable:")), preview.ref.textContent)
    } yield ()).andThen(_ => close())
  }

  test("full Java data exercises show the data and the task beside the editor instead of the drawing") {
    val element = ProgrammingExerciseFullJava("java-data", instructionText = Some(LanguageMapContentId("test/task")), dataView = Some(ExerciseDataView.NumberList))
    load(element)
    val state: Var[ProgrammingState] = Var(java("NumberList.swap(0, 1);"))
    val side = HtmlProgrammingExerciseFullJavaRenderer.createNewEditor(element, state, GradingReporter(element)).sidePanel.getDomElement()
    val close = mount(side)
    try {
      assert(side.ref.querySelector(".program-data-preview") != null)
      assert(side.ref.textContent.toLowerCase.contains("sidepanelinstructiontitle"), "the task card is shown")
      assert(side.ref.textContent.toLowerCase.contains("programdatatitle"), side.ref.textContent)
      assert(!side.ref.textContent.toLowerCase.contains("drawtitle"))
    } finally close()
  }

  test("full Java exercises without a data view keep the turtle drawing and add no task card") {
    val element = ProgrammingExerciseFullJava("java-drawing")
    load(element)
    val side = HtmlProgrammingExerciseFullJavaRenderer.createNewEditor(element, Var(java("")), GradingReporter(element)).sidePanel.getDomElement()
    val close = mount(side)
    try {
      assert(side.ref.textContent.toLowerCase.contains("drawtitle"), side.ref.textContent)
      assert(side.ref.querySelector(".program-data-preview") == null)
      assert(!side.ref.textContent.toLowerCase.contains("sidepanelinstructiontitle"))
    } finally close()
  }

  private val sortExercise = SortListExercise("sort-renderer", ProgrammingStateRaw(Python, "def sortiere():\n    NumberList.swap(0, 1)\n"),
    "sortiere", exampleList = Seq(2, 1, 3), testLists = Seq(Seq(2, 1), Seq(3, 1, 2)))

  test("the sort exercise overview shows the source, the replay and one result row per test list") {
    load(sortExercise)
    val element = HtmlSortListExerciseRenderer.renderAppElement(sortExercise).getDomElement()
    val close = mount(element)
    eventually(all(element.ref, ".step-navigator__position") == Seq("1 / 1"), element.ref.textContent).map { _ =>
      assert(all(element.ref, ".java-full-editor-preview").exists(_.contains("def sortiere()")))
      assertEquals(element.ref.querySelectorAll(".feedback-panel .feedback-entry").length, 2)
      assertEquals(all(element.ref, ".sort-bar__value"), Seq("1", "2", "3"))
    }.andThen(_ => close())
  }

  test("the sort overview shows a Snap answer as Python, with its number-list blocks as NumberList calls") {
    val blocks = ProgrammingStateRaw(it.evadid.core.datastructures.language.AppLanguage.SnapLanguage, sortExercise.initProgram.toSnapXml.code)
    val shown = HtmlSortListExerciseRenderer.source(blocks)
    assert(shown.contains("NumberList.swap(0, 1)") && !shown.contains("<project"), shown)
    assertEquals(HtmlSortListExerciseRenderer.source(sortExercise.initProgram), "def sortiere():\n    NumberList.swap(0, 1)\n")
  }

  test("the editor side modules replay sorting and turtle drawings") {
    load(sortExercise)
    val sortCard = HtmlSortListExerciseRenderer.sortStepsCard(sortExercise, Var(sortExercise.initProgram)).getDomElement()
    val turtleCard = HtmlTurtleRecreateShapeRenderer.turtleStepsCard(Var(ProgrammingStateRaw(Python, "forward(10)\nright(90)\n")).signal).getDomElement()
    val close = mount(div(sortCard, turtleCard))
    (for {
      _ <- eventually(all(sortCard.ref, ".step-navigator__position") == Seq("1 / 1"), sortCard.ref.textContent)
      _ <- eventually(all(turtleCard.ref, ".step-navigator__position") == Seq("2 / 2"), turtleCard.ref.textContent)
    } yield {
      assert(sortCard.ref.querySelector(".sort-step-panel") != null && turtleCard.ref.querySelector(".turtle-step-panel") != null)
      assert(sortCard.ref.textContent.toLowerCase.contains("sortstepstitle") && turtleCard.ref.textContent.toLowerCase.contains("turtlestepstitle"))
    }).andThen(_ => close())
  }

  test("the overview feedback panel appears only for interactions with grading or hints") {
    val plain = TextInteraction("plain-text")
    load(plain, sortExercise)
    val graded = AtomarLineRendering.basicLine(sortExercise, DomElementSingle(div("content"))).getDomElement()
    val ungraded = AtomarLineRendering.basicLine(plain, DomElementSingle(div("content"))).getDomElement()
    val close = mount(div(graded, ungraded))
    try {
      assertEquals(graded.ref.querySelectorAll(".feedback-panel--overview").length, 1)
      assertEquals(ungraded.ref.querySelectorAll(".feedback-panel").length, 0)
      assert(graded.ref.querySelector(".interaction-element-content").textContent.startsWith("content"), "the panel follows the content")
    } finally close()
  }
}
