package it.evadid.homepage.webElements.editor.code

import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorFlowchartGutter
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import NestedBlockRenderer.*
import todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth.*
import todomove.webElementsOld.webElements.svg.shapes.controlflow.doubleWidth.*
import scala.scalajs.js.URIUtils.decodeURIComponent
import org.scalajs.dom

class NestedFlowchartGutterSpec extends EditorDomSuite {
  private def svg(image: String): dom.Element = {
    val host = dom.document.createElement("div")
    host.innerHTML = decodeURIComponent(image.drop(image.indexOf(',') + 1))
    host.firstElementChild
  }
  private def paths(element: dom.Element): List[(String, String)] = {
    val nodes = element.querySelectorAll("path")
    (0 until nodes.length).map(index => (nodes(index).getAttribute("d"), nodes(index).getAttribute("stroke"))).toList
  }

  test("Python gutter uses the existing nested renderer's flow paths without expression text") {
    val chart = PythonFlowchartGutter.fromPython("if True:\n    print(1)\nelse:\n    print(2)")
    val rendered = CodeMirrorFlowchartGutter.render(chart)
    assertEquals(rendered.rows.keySet, Set(1, 2, 3, 4))
    val actual = svg(rendered.rows(1).slices.head.image)
    assertEquals(actual.querySelectorAll("text").length, 0)
    val lines: List[NestedBlockLine] = List(
      ControlFlowLine(ControlFlowProgramStarter()),
      ControlFlowIncreaseLine(IfElseSplit(), None, ControlFlowDirected(true, false)),
      ControlFlowLine(ControlFlowDirected(true, false)),
      ControlFlowReplaceLine(ControlFlowCross(), None, ControlFlowDirected(true, false)),
      ControlFlowLine(ControlFlowDirected(true, false)),
      ControlFlowDecreaseLine(IfElseUnion(), None),
      ControlFlowLine(ControlFlowProgramStopper())
    )
    val config = BeRenderingConfig.default().copy(pControlSegmentSize = 2)
    val original = NestedBlockRenderer(List(NestedBlockSegment(lines))).controlFlowOnly(config, List.fill(lines.size)(16.0))
      .render(config, Bounds(Point(0.0, 0.0), Dimension(100.0, 200.0))).renderWithMods.ref
    assertEquals(paths(actual), paths(original))
    assert(paths(actual).nonEmpty)
  }

  test("flow-only drawing is independent of expression labels and keeps virtual closing rows inside source lines") {
    val first = CodeMirrorFlowchartGutter.render(PythonFlowchartGutter.fromPython("while True:\n    print(1)"))
    val second = CodeMirrorFlowchartGutter.render(PythonFlowchartGutter.fromPython("while True:\n    print(123456789)"))
    assertEquals(first.rows.keySet, Set(1, 2))
    assertEquals(first.rows(1).slices.head.image, second.rows(1).slices.head.image)
    assert(first.rows(2).slices.head.span > first.rows(1).slices.head.span)
  }

  test("function diagrams stay independent and blank source rows do not become content shapes") {
    val chart = PythonFlowchartGutter.fromPython("def draw():\n    print(1)\n\nprint(2)")
    val rendered = CodeMirrorFlowchartGutter.render(chart)
    assertEquals(rendered.rows.keySet, Set(1, 2, 3, 4))
    assertEquals(rendered.rows(2).slices.size, 2)
    assert(rendered.rows(2).slices.exists(_.x > 0))
    assert(rendered.rows.values.flatMap(_.slices).forall(slice => svg(slice.image).querySelectorAll("text").length == 0))
    assertEquals(CodeMirrorFlowchartGutter.render(PythonFlowchartGutter.empty).rows, Map.empty)
  }
}
