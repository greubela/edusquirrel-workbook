package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.steps

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.{LineResult, RenderedLine, Scene}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import org.scalajs.dom
import upickle.default.*

import scala.concurrent.ExecutionContext

class StepPanelsDomSpec extends EditorDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private val templates = Map(
    "stepCommand" -> "Step {step}: {command}", "sortStepSwap" -> "Swap {first} and {second}",
    "sortStepGet" -> "Read {index}: {value}", "sortStepStats" -> "{swaps} swaps, {sets} sets, {reads} reads",
    "programDataFailed" -> "Failed:", "programDataUnavailable" -> "Unavailable:")
  private def text(key: String): Signal[String] = Val(templates.getOrElse(key, key))
  private def find(root: dom.Element, selector: String): String = Option(root.querySelector(selector)).fold("")(_.textContent)
  private def click(root: dom.Element, key: String): Unit = root.querySelector(s"button[title='$key']").asInstanceOf[dom.HTMLButtonElement].click()

  test("line classes mark the current and upcoming strokes of a replay, keeping the jump style") {
    def line(result: LineResult, jump: Boolean = false) = RenderedLine(Point(0.0, 0.0), Point(1.0, 0.0), result, jump)
    assertEquals(TurtleJsxGraphRenderer.lineClasses(line(LineResult.Current)), "turtle-line turtle-line--current")
    assertEquals(TurtleJsxGraphRenderer.lineClasses(line(LineResult.Upcoming, jump = true)), "turtle-line turtle-line--upcoming turtle-line--jump")
    assertEquals(TurtleJsxGraphRenderer.lineClasses(line(LineResult.Correct)), "turtle-line turtle-line--correct")
    val scene = Scene(Seq(line(LineResult.Current)), Nil, Some(Point(1.0, 0.0)))
    assertEquals(read[Scene](write(scene)), scene)
    assertEquals(read[Scene](write(Scene(Nil, Nil))).marker, None)
  }

  test("the turtle panel replays the commands of the bound answer and follows changes") {
    val state: Var[ProgrammingState] = Var(ProgrammingStateRaw(Python, "forward(100)\nright(90)\nforward(50)\n"))
    val panel = new TurtleStepPanel(state, text).getDomElement()
    val close = mount(panel)
    val root = panel.ref
    (for {
      _ <- eventually(find(root, ".step-navigator__position") == "3 / 3", find(root, ".step-panel"))
      _ = assertEquals(find(root, ".step-panel__caption"), "Step 3: forward(50)")
      _ = click(root, "stepPrevious")
      _ = assertEquals(find(root, ".step-panel__caption"), "Step 2: right(90)")
      _ = click(root, "stepFirst")
      _ = assertEquals(find(root, ".step-panel__caption"), "turtleStepStart")
      _ = state.set(ProgrammingStateRaw(Python, "forward(10)\n"))
      _ <- eventually(find(root, ".step-navigator__position") == "1 / 1", "debounced update")
      _ = state.set(ProgrammingStateRaw(Java, "public class Main { void broken( }"))
      _ <- eventually(find(root, ".step-panel__problem").startsWith("Unavailable:"), find(root, ".step-panel"))
    } yield ()).andThen(_ => close())
  }

  private val bubble = ProgrammingStateRaw(Python,
    """def sortiere():
      |    n = NumberList.length()
      |    for d in range(n):
      |        for i in range(n - 1 - d):
      |            if NumberList.get(i) > NumberList.get(i + 1):
      |                NumberList.swap(i, i + 1)
      |""".stripMargin)

  test("the sort panel replays changes as bars, can include reads and marks the sorted result") {
    val state: Var[ProgrammingState] = Var(bubble)
    val panel = new SortStepPanel(state, "sortiere", Seq(3, 1, 2), text).getDomElement()
    val close = mount(panel)
    val root = panel.ref
    def values = (0 until root.querySelectorAll(".sort-bar__value").length).map(root.querySelectorAll(".sort-bar__value")(_).textContent)
    (for {
      _ <- eventually(find(root, ".step-navigator__position") == "2 / 2", find(root, ".step-panel"))
      _ = assertEquals(values, Seq("1", "2", "3"))
      _ = assert(root.querySelector(".sort-steps--sorted") != null)
      _ = assertEquals(find(root, ".step-panel__caption"), "Swap 1 and 2")
      _ = assertEquals(root.querySelectorAll(".sort-bar--changed").length, 2)
      _ = assertEquals(find(root, ".step-panel__stats"), "2 swaps, 0 sets, 6 reads")
      _ = click(root, "stepFirst")
      _ = assertEquals((values, find(root, ".step-panel__caption")), (Seq("3", "1", "2"), "sortStepStart"))
      _ = assert(root.querySelector(".sort-steps--sorted") == null)
      toggle = root.querySelector("input[type='checkbox']").asInstanceOf[dom.HTMLInputElement]
      _ = toggle.click()
      _ <- eventually(find(root, ".step-navigator__position") == "9 / 9", "length, six reads and two swaps")
      _ = click(root, "stepFirst")
      _ = click(root, "stepNext")
      _ = click(root, "stepNext")
      _ = assertEquals(find(root, ".step-panel__caption"), "Read 0: 3")
      _ = assertEquals(root.querySelectorAll(".sort-bar--read").length, 1)
    } yield ()).andThen(_ => close())
  }

  test("the sort panel explains a missing sort function") {
    val state: Var[ProgrammingState] = Var(ProgrammingStateRaw(Python, "def andere():\n    pass\n"))
    val panel = new SortStepPanel(state, "sortiere", Seq(2, 1), text).getDomElement()
    val close = mount(panel)
    eventually(find(panel.ref, ".step-panel__problem").contains("does not define sortiere"), find(panel.ref, ".step-panel"))
      .map(_ => assertEquals(find(panel.ref, ".step-navigator__position"), "0 / 0"))
      .andThen(_ => close())
  }
}
