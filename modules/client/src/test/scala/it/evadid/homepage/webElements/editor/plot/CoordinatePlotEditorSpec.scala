package it.evadid.homepage.webElements.editor.plot

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.workbook.interactions.plot.CoordinatePlotInteraction
import it.evadid.workbook.interactions.plot.{PlotAnswer, PlotAxis, PlotPoint}
import org.scalajs.dom

class CoordinatePlotEditorSpec extends UiDomSuite {
  private val config = CoordinatePlotInteraction("plot", LanguageMapContentId("plot/title"), LanguageMapContentId("plot/time"),
    LanguageMapContentId("plot/distance"), PlotAxis(0, 10, 2), PlotAxis(0, 100, 25))
  private def fields(root: dom.Element) = all(root, "input[type=text]")
  private def add(root: dom.Element) = buttonNamed(root, loadingText("digitalWorkbooks/plotAdd"))
  private def rows(root: dom.Element) = all(root, "tbody tr").map(row => all(row, "td").take(2).map(_.textContent))
  private def enter(root: dom.Element, x: String, y: String): Unit = {
    typeInto(fields(root).head, x); typeInto(fields(root)(1), y)
  }

  test("points typed with either decimal separator are added, replaced by x, plotted and removed") {
    val answer = Var(PlotAnswer())
    mounted(CoordinatePlotEditor(pageInfo, config, answer, Var(false)).getDomElement()) { root =>
      assertEquals(all(root, ".coordinate-plot__tick-x").map(_.textContent), List("0", "2", "4", "6", "8", "10"))
      assertEquals(all(root, ".coordinate-plot__tick-y").map(_.textContent), List("0", "25", "50", "75", "100"))
      assert(isDisabled(add(root)), "nothing entered yet")
      enter(root, "2,5", "40")
      click(add(root))
      enter(root, "1", "10")
      click(add(root))
      assertEquals(answer.now().points, Seq(PlotPoint(1, 10), PlotPoint(2.5, 40)))
      assertEquals(rows(root), List(List("1", "10"), List("2.5", "40")))
      assertEquals(all(root, "circle.coordinate-plot__point").map(_.textContent), List("1, 10", "2.5, 40"))
      assertEquals(one(root, "polyline.coordinate-plot__curve").getAttribute("points"), "112,290 190,200",
        "the curve connects the points in x order")

      enter(root, "1", "90")
      click(add(root))
      assertEquals(answer.now().points, Seq(PlotPoint(1, 90), PlotPoint(2.5, 40)), "a point with the same x is replaced")
      enter(root, "11", "5")
      assert(isDisabled(add(root)), "points outside the axes are refused")
      enter(root, "3", "abc")
      assert(isDisabled(add(root)))

      click(buttonNamed(all(root, "tbody tr").head, loadingText("digitalWorkbooks/plotRemove")))
      assertEquals(answer.now().points, Seq(PlotPoint(2.5, 40)))
      assertEquals(all(root, "polyline.coordinate-plot__curve").size, 0, "one point draws no curve")
    }
  }

  test("the connect option toggles the curve and locking disables every control") {
    val answer = Var(PlotAnswer(Seq(PlotPoint(0, 0), PlotPoint(10, 100))))
    val locked = Var(false)
    mounted(CoordinatePlotEditor(pageInfo, config, answer, locked).getDomElement()) { root =>
      val connect = one(root, "input[type=checkbox]")
      assert(connect.asInstanceOf[dom.html.Input].checked)
      click(connect)
      assertEquals(answer.now().connected, false)
      assertEquals(all(root, "polyline.coordinate-plot__curve").size, 0)
      click(connect)
      assertEquals(answer.now().connected, true)
      locked.set(true)
      assert(fields(root).forall(isDisabled) && isDisabled(connect))
      enter(root, "4", "50")
      assert(isDisabled(add(root)))
      assert(all(root, "tbody button").forall(isDisabled))
      assertEquals(answer.now().points.size, 2)
    }
  }
}
