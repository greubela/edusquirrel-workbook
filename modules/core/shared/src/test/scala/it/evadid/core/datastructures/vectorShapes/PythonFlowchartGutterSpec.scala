package it.evadid.core.datastructures.vectorShapes

import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import munit.FunSuite

class PythonFlowchartGutterSpec extends FunSuite {
  private def chart(source: String) = PythonFlowchartGutter.fromPython(source)

  test("physical rows survive blank lines, inline comments, pass and normalized assignments") {
    val result = chart("# heading\nx = 1 # value\n\npass\nx += 2\nprint(x)\n")
    assertEquals(result.nodes.map(_.line), List(2, 5, 6))
    assertEquals(result.edges.map(e => e.from -> e.to).toSet, Set(2 -> 5, 5 -> 6))
    assertEquals(result.entries, List(2))
  }

  test("if branches join after the else body rather than falling through it") {
    val result = chart("x = 3\nif x < 5:\n    print(x)\nelse:\n    print(5)\nprint(9)")
    assertEquals(result.nodes.map(_.line), List(1, 2, 3, 5, 6))
    assert(result.edges.exists(e => e.from == 2 && e.to == 3 && e.kind == "yes"))
    assert(result.edges.exists(e => e.from == 2 && e.to == 5 && e.kind == "no"))
    assert(result.edges.exists(e => e.from == 3 && e.to == 6))
    assert(!result.edges.exists(e => e.from == 3 && e.to == 5))
  }

  test("elif decisions keep their original rows after normalization") {
    val result = chart("x = 2\nif x == 1:\n    print(1)\nelif x == 2:\n    print(2)\nelse:\n    print(3)\nprint(4)")
    assertEquals(result.nodes.filter(_.kind == "decision").map(_.line), List(2, 4))
    assert(result.edges.exists(e => e.from == 2 && e.to == 4 && e.kind == "no"))
    assert(result.edges.exists(e => e.from == 4 && e.to == 7 && e.kind == "no"))
  }

  test("nested loops have back edges and a false exit to the next statement") {
    val result = chart("x = 0\nwhile x < 3:\n    for _ in range(2):\n        print(x)\n    x += 1\nprint(x)")
    assertEquals(result.nodes.filter(_.kind == "loop").map(_.line), List(2, 3))
    assert(result.edges.exists(e => e.from == 4 && e.to == 3))
    assert(result.edges.exists(e => e.from == 5 && e.to == 2))
    assert(result.edges.exists(e => e.from == 2 && e.to == 6 && e.kind == "no"))
  }

  test("function bodies are separate and return has no fall-through") {
    val result = chart("def answer():\n    return 42\n    print(0)\nprint(answer())")
    assertEquals(result.nodes.map(_.line), List(1, 2, 4))
    assertEquals(result.entries.toSet, Set(1, 2))
    assert(result.edges.exists(e => e.from == 1 && e.to == 4))
    assert(!result.edges.exists(_.from == 2))
    assert(!result.edges.exists(e => e.from == 1 && e.to == 2))
  }

  test("a conditional at the end has an explicit false termination") {
    val result = chart("if True:\n    print(1)")
    assert(result.edges.exists(e => e.from == 1 && e.to == 1 && e.kind == "no-end"))
    assert(result.edges.exists(e => e.from == 1 && e.to == 2 && e.kind == "yes"))
  }

  test("class attributes and methods retain their physical rows") {
    val result = chart("class Example:\n    value: int = 1\n    def get(self):\n        return 42\nx = 2")
    assertEquals(result.nodes.map(_.line), List(1, 2, 3, 4, 5))
    assertEquals(result.nodes.filter(_.kind == "function").map(_.line), List(3))
    assert(!result.edges.exists(e => e.from == 3 && e.to == 4))
  }

  test("tabs, CRLF and comment-only programs are safe") {
    val result = chart("if True:\r\n\tprint(1)\r\nprint(2)")
    assertEquals(result.nodes.map(_.line), List(1, 2, 3))
    assertEquals(chart("# nothing\n\n"), PythonFlowchartGutter.empty)
    assertEquals(chart(""), PythonFlowchartGutter.empty)
  }

  test("an unfinished nested loop keeps the finished outer chart") {
    val dropped = chart("x = 0\nfor i in range(3):\n    print(i)\n    for j in")
    assertEquals(dropped.nodes.map(_.line), List(1, 2, 3))
    assert(dropped.nodes.exists(node => node.line == 2 && node.kind == "loop"))
    val header = chart("x = 0\nfor i in range(3):\n    print(i)\n    for j in range(2):")
    assertEquals(header.nodes.filter(_.kind == "loop").map(_.line), List(2, 4))
    assert(header.nodes.exists(_.line == 3))
  }

  test("a bare incomplete header draws its own node") {
    val decision = chart("if True:")
    assertEquals(decision.nodes.map(node => node.line -> node.kind), List(1 -> "decision"))
    val loop = chart("for i in range(3):")
    assertEquals(loop.nodes.map(node => node.line -> node.kind), List(1 -> "loop"))
  }

  test("an unfinished call keeps the finished outer chart") {
    val result = chart("x = 0\nfor i in range(3):\n    print(i")
    assertEquals(result.nodes.map(_.line), List(1, 2))
    assert(result.nodes.exists(node => node.line == 2 && node.kind == "loop"))
  }

  test("dropping an unfinished body still closes the new loop header") {
    val result = chart("x = 0\nfor i in range(3):\n    print(i)\n    for j in range(2):\n        print(j")
    assertEquals(result.nodes.filter(_.kind == "loop").map(_.line), List(2, 4))
    assert(result.nodes.exists(_.line == 3))
  }

  test("an error above the last few code lines clears the chart") {
    assertEquals(
      chart("???\nx = 1\ny = 2\nz = 3\nw = 4"),
      PythonFlowchartGutter.empty
    )
  }

  test("unsupported programs clear the chart") {
    assertEquals(chart("try:\n    print(1)\nexcept:\n    print(2)"), PythonFlowchartGutter.empty)
    assertEquals(chart("break"), PythonFlowchartGutter.empty)
    assertEquals(chart("print(1)\nbreak"), PythonFlowchartGutter.empty)
    assertEquals(chart("continue"), PythonFlowchartGutter.empty)
  }
}
