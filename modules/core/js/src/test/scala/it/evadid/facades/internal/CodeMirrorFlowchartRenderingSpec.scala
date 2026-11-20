package it.evadid.facades.internal

import it.evadid.facades.CodeMirror.{Flowchart, FlowchartRow, FlowchartSlice}
import it.evadid.facades.CodeMirrorSuite
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class CodeMirrorFlowchartRenderingSpec extends CodeMirrorSuite {
  private val chart = Flowchart(30, Map(
    1 -> FlowchartRow(List(FlowchartSlice("img-1", 30, 60, 0, 20)), "Start"),
    2 -> FlowchartRow(List(FlowchartSlice("img-2a", 12, 60, 20, 20), FlowchartSlice("img-2b", 18, 40, 5, 20, x = 12)))))

  /** The analysis boundary: each requested source waits for the test to answer it. */
  private final class Analysis {
    val requests: js.Array[(String, Promise[Flowchart])] = js.Array()
    def chartFor(source: String): Future[Flowchart] = {
      val answer = Promise[Flowchart]()
      requests.push(source -> answer)
      answer.future
    }
    def answer(index: Int, value: Flowchart): Unit = requests(index)._2.success(value)
  }

  private def withEditor[A](doc: String)(body: (js.Dynamic, dom.Element, Analysis) => Future[A]): Future[A] = withLibraries { libs =>
    val analysis = new Analysis
    val parent = host()
    val view = js.Dynamic.newInstance(libs.EditorView)(js.Dynamic.literal(doc = doc, parent = parent,
      extensions = CodeMirrorFlowchartRendering.extension(analysis.chartFor)))
    body(view, parent, analysis).andThen(_ => view.destroy())
  }

  /** Rendered gutter rows (excluding the hidden spacer) as (title, slice images). */
  private def rows(parent: dom.Element): List[(String, List[String])] =
    parent.querySelectorAll(".cm-flowchart-gutter .cm-gutterElement").toList
      .filterNot(_.asInstanceOf[dom.html.Element].style.visibility == "hidden")
      .map(_.querySelector(".cm-flowchart-row"))
      .filter(_ != null)
      .map(row => row.getAttribute("title") -> row.querySelectorAll("image").toList.map(_.getAttribute("href")))

  private def edit(view: js.Dynamic, insert: String): Unit =
    view.dispatch(js.Dynamic.literal(changes = js.Dynamic.literal(from = view.state.doc.length, insert = insert)))

  test("charts for the document render one sliced gutter row per line") {
    withEditor("a\nb\nc") { (_, parent, analysis) =>
      waitUntil(analysis.requests.length == 1).flatMap { _ =>
        assertEquals(analysis.requests(0)._1, "a\nb\nc")
        assertEquals(rows(parent), Nil, "no rows are shown before the chart arrives")
        analysis.answer(0, chart)
        waitUntil(rows(parent).nonEmpty)
      }.map { _ =>
        assertEquals(rows(parent), List("Start" -> List("img-1"), (null: String) -> List("img-2a", "img-2b"), (null: String) -> Nil))
        val first = parent.querySelector("[title='Start']").asInstanceOf[dom.html.Div]
        assertEquals(first.style.width, "30px")
        val outer = first.querySelector("svg")
        assertEquals(outer.getAttribute("viewBox"), "0 0 30 20")
        assertEquals(outer.getAttribute("preserveAspectRatio"), "none")
        val viewport = parent.querySelectorAll("svg svg").toList.map(slice =>
          List("x", "width", "viewBox").map(slice.getAttribute))
        assertEquals(viewport, List(List("0", "30", "0 0 30 20"), List("0", "12", "0 20 12 20"), List("12", "18", "0 5 18 20")))
        assertEquals(parent.querySelector("image[href='img-2b']").getAttribute("height"), "40")
      }
    }
  }

  test("edits request a new chart after a pause and stale results are ignored") {
    withEditor("x") { (view, parent, analysis) =>
      waitUntil(analysis.requests.length == 1).flatMap { _ =>
        analysis.answer(0, chart)
        waitUntil(rows(parent).nonEmpty)
      }.flatMap { _ =>
        edit(view, " = 1")
        tick(50).flatMap { _ =>
          assertEquals(analysis.requests.length, 1, "edits are debounced")
          waitUntil(analysis.requests.length == 2)
        }
      }.flatMap { _ =>
        assertEquals(analysis.requests(1)._1, "x = 1")
        edit(view, "\ny")
        analysis.answer(1, Flowchart(5, Map(1 -> FlowchartRow(Nil, "stale"))))
        waitUntil(analysis.requests.length == 3)
      }.flatMap { _ =>
        assertEquals(rows(parent).map(_._1).head, "Start", "a chart for an outdated document is discarded")
        analysis.answer(2, Flowchart(5, Map(2 -> FlowchartRow(Nil, "Loop"))))
        waitUntil(rows(parent).exists(_._1 == "Loop"))
      }.map(_ => assertEquals(rows(parent).map(_._1), List(null, "Loop")))
    }
  }

  test("failed analyses clear the chart and identical charts are not dispatched again") {
    withEditor("x") { (view, parent, analysis) =>
      waitUntil(analysis.requests.length == 1).flatMap { _ =>
        analysis.answer(0, chart)
        waitUntil(rows(parent).nonEmpty)
      }.flatMap { _ =>
        edit(view, "1")
        waitUntil(analysis.requests.length == 2)
      }.flatMap { _ =>
        val before = view.state
        analysis.answer(1, chart)
        tick(10).map(_ => assert(view.state eq before, "an unchanged chart keeps the editor state"))
      }.flatMap { _ =>
        edit(view, "2")
        waitUntil(analysis.requests.length == 3)
      }.flatMap { _ =>
        analysis.requests(2)._2.failure(IllegalStateException("worker crashed"))
        waitUntil(rows(parent).isEmpty)
      }.map(_ => assertEquals(rows(parent), Nil))
    }
  }

  test("destroyed editors cancel pending analysis and ignore late results") {
    withEditor("x") { (view, _, analysis) =>
      waitUntil(analysis.requests.length == 1).flatMap { _ =>
        edit(view, "y")
        val before = view.state
        view.destroy()
        analysis.answer(0, chart)
        tick(200).map { _ =>
          assertEquals(analysis.requests.length, 1, "the debounced request is cancelled")
          assert(view.state eq before)
        }
      }
    }
  }
}
