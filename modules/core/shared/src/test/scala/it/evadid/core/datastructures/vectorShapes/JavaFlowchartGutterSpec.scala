package it.evadid.core.datastructures.vectorShapes

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python}
import it.evadid.core.datastructures.vectorShapes.renderer.{FlowchartGutter, JavaFlowchartGutter}
import munit.FunSuite

class JavaFlowchartGutterSpec extends FunSuite {
  private def chart(source: String) = JavaFlowchartGutter.fromJava(source)

  private val program =
    """class Main {
      |  static int clamp(int x) {
      |    if (x > 10) {
      |      return 10;
      |    } else if (x < 0) {
      |      x = 0;
      |    } else {
      |      x = x + 1;
      |    }
      |    return x;
      |  }
      |
      |  static void main(String[] args) {
      |    int total = 0;
      |    // counting
      |    for (int i = 0; i < 3; i++) {
      |      total += clamp(i);
      |    }
      |    System.out.println(total);
      |  }
      |}""".stripMargin

  test("each method is its own graph with decisions, else-if chains and loops on their source lines") {
    val result = chart(program)
    assertEquals(result.nodes.filter(_.kind == "decision").map(_.line), List(3, 5))
    assertEquals(result.nodes.filter(_.kind == "loop").map(_.line), List(16))
    assert(result.edges.exists(e => e.from == 3 && e.to == 4 && e.kind == "yes"))
    assert(result.edges.exists(e => e.from == 3 && e.to == 5 && e.kind == "no"), "else if is the nested decision")
    assert(result.edges.exists(e => e.from == 5 && e.to == 8 && e.kind == "no"))
    assert(!result.edges.exists(_.from == 4), "return ends its path")
    assert(result.edges.exists(e => e.from == 17 && e.to == 16), "the loop body returns to the loop")
    assert(result.edges.exists(e => e.from == 16 && e.to == 19 && e.kind == "no"))
    assert(!result.nodes.exists(_.line == 15), "comments are not charted")
    // Each method's flow plan starts at its header.
    assertEquals(result.flow.map(_.head), List(FlowchartGutter.FlowLine(2, "start"), FlowchartGutter.FlowLine(13, "start")))
    // The else if row is the nested decision; only the final else has a row of its own.
    assertEquals(result.flow(0).filter(_.kind == "else").map(_.line), List(5, 7))
  }

  test("a method on its own (the Function Mode editor) is charted on its own lines") {
    val method = program.linesIterator.slice(12, 20).mkString("\n")
    val result = chart(method)
    assertEquals(result.nodes.filter(_.kind == "loop").map(_.line), List(4))
    assertEquals(result.flow.map(_.head), List(FlowchartGutter.FlowLine(1, "start")))
    assertEquals(result.nodes.find(_.kind == "function").map(_.depth), Some(0))
  }

  test("unsupported statements, unparsable code and statements sharing a line leave the gutter empty") {
    List(
      "class A { void f() { while (true) { break; } } }",
      "class A {\n  void f() {\n    try {\n      g();\n    } catch (Exception e) {\n    }\n  }\n}",
      "class A {\n  void f() {\n    int x = 1; x++;\n  }\n}",
      "class A {\n  void f() {\n    if (",
    ).foreach(source => assertEquals(chart(source), FlowchartGutter.empty, source))
  }

  test("verbatim library definitions are not charted") {
    val source = it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(Python, "forward(10)").toJava.code
    val result = chart(source)
    assert(result.nodes.nonEmpty)
    val turtleStart = source.linesIterator.indexWhere(_.startsWith("class Turtle")) + 1
    assert(result.nodes.forall(_.line < turtleStart), result.nodes)
  }

  test("charts are chosen by language") {
    assertEquals(FlowchartGutter.of(Java, program), chart(program))
    assertEquals(FlowchartGutter.of(Python, "print(1)").nodes.map(_.line), List(1))
    assertEquals(upickle.default.write[ProgrammingLanguage](Java), "\"Java\"")
  }
}
