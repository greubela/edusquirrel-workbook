package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.util.logging.LoggingLevel
import it.evadid.vm.code.errors.{ProgrammingConversionError, ProgrammingConversionKind}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

/** Unfinished or unsupported source: its own language keeps the text for editing, every other
  * language rejects it with the same kind of diagnostic and the position of the first problem.
  * Conversion never throws, never changes the source and never silently drops code.
  */
class IncompleteSourceConversionSpec extends FunSuite {
  private val languages = Seq(Python, Java, SnapLanguage)

  private final case class Case(label: String, state: ProgrammingState, fragment: String, kind: ProgrammingConversionKind,
      line: Int, column: Int, message: String = "")

  private val unparsable = ProgrammingConversionKind.Unparsable
  private val unsupported = ProgrammingConversionKind.UnsupportedConstruct
  private def java(body: String) = s"class Main {\n    public static void main(String[] args) {\n$body\n    }\n}\n"

  private val cases = Seq(
    Case("Python if without colon", ProgrammingStateRaw(Python, "forward(10)\nif x > 3\n    forward(5)\nright(90)\n"),
      "if x > 3", unparsable, 2, 1, "must end with ':'"),
    Case("Python call without closing parenthesis", ProgrammingStateRaw(Python, "forward(10)\nright(90\nforward(5)\n"), "right(90", unparsable, 2, 1),
    Case("Python half-typed expression", ProgrammingStateRaw(Python, "x = 3 +\nforward(x)\n"), "x = 3 +", unparsable, 1, 1),
    Case("Python open string", ProgrammingStateRaw(Python, "print(\"hello)\nforward(3)\n"), "print(\"hello)", unparsable, 1, 1),
    Case("Python unfinished method body", ProgrammingStatePythonStructured("class A:\n    def m(self):\n        forward(\nforward(1)\n"),
      "forward(", unparsable, 3, 9),
    Case("Python import", ProgrammingStateRaw(Python, "forward(1)\nimport os\n"), "import os", unsupported, 2, 1),
    Case("Python try statement", ProgrammingStateRaw(Python, "try:\n    forward(1)\nexcept ValueError:\n    pass\n"), "try:", unsupported, 1, 1),
    Case("Java missing semicolon", ProgrammingStateRaw(Java, java("        Turtle.forward(10)\n        Turtle.turnRight(90);")),
      "Turtle.forward(10)", unparsable, 3, 9),
    Case("Java half-typed expression", ProgrammingStateRaw(Java, java("        double x = 3 + ;")), "double x = 3 + ;", unparsable, 3, 9),
    Case("Java unfinished helper", ProgrammingStateJavaStructured(
      "class Main {\n    static void draw() {\n        Turtle.forward(\n    }\n    public static void main(String[] args) {\n        draw();\n    }\n}\n"),
      "Turtle.forward(", unparsable, 3, 9),
    Case("Java unclosed class", ProgrammingStateRaw(Java, "class Main {\n    public static void main(String[] args) {\n        Turtle.forward(10);\n"),
      "Turtle.forward(10);", unparsable, 1, 1))

  private def error(result: ConversionResult): ProgrammingConversionError = {
    assertEquals(result.finalState, None, "no partial program is produced")
    assert(result.hasLostFunctionality)
    val info = result.conversionInfo.headOption.getOrElse(fail("the failure is reported"))
    assertEquals(info.warningLevel, LoggingLevel.ERROR)
    assert(!info.warningMsg.contains("requirement failed"), info.warningMsg)
    info.sourceError.getOrElse(fail(s"the failure has a kind: ${info.warningMsg}"))
  }

  cases.foreach { example =>
    test(s"${example.label}: kept in its language, rejected with its position elsewhere") {
      val origin = example.state.associatedLanguage
      val before = ProgrammingState.serializer.serialize(example.state)
      languages.foreach { target =>
        val result = example.state.convertToLanguage(target)
        if target == origin then {
          assert(!result.hasLostFunctionality, s"$target: ${result.conversionInfo}")
          val kept = result.finalState.getOrElse(fail(s"$target keeps the source")).toString
          assert(kept.contains(example.fragment), s"$target dropped `${example.fragment}`:\n$kept")
        } else {
          val problem = error(result)
          assertEquals((problem.kind, problem.line, problem.column), (example.kind, Some(example.line), Some(example.column)), s"$origin -> $target")
          assert(result.conversionInfo.head.warningMsg.contains(example.message), result.conversionInfo.head.warningMsg)
        }
      }
      assertEquals(ProgrammingState.serializer.serialize(example.state), before, "the source state is unchanged")
    }
  }

  test("malformed Snap XML is unparsable in every language instead of an empty program") {
    val state = ProgrammingStateRaw(SnapLanguage,
      "<project><scenes><scene><stage><sprites><sprite><scripts><script><block s=\"forward\"><l>10</l>")
    languages.foreach { target =>
      val problem = error(state.convertToLanguage(target))
      assertEquals(problem.kind, unparsable, target.toString)
      assert(state.convertToLanguage(target).conversionInfo.head.warningMsg.contains("Missing closing tag"))
    }
  }

  test("positions refer to the student's text when a library class follows the program") {
    val java = ProgrammingStateRaw(Python, "forward(10)\nright(90)\n").toJava.code.replace("Turtle.forward(10);", "Turtle.forward(10)")
    assert(java.contains("class Turtle {"), java)
    val problem = error(ProgrammingStateRaw(Java, java).convertToLanguage(Python))
    assertEquals((problem.kind, problem.line), (unparsable, Some(java.linesIterator.indexWhere(_.contains("Turtle.forward(10)")) + 1)))
  }

  test("a function header without a body reads as an empty function, as students type it") {
    val state = ProgrammingStateRaw(Python, "def draw(size):\n")
    languages.filterNot(_ == Python).foreach { target =>
      val result = state.convertToLanguage(target)
      assert(!result.hasLostFunctionality, s"$target: ${result.conversionInfo}")
    }
    assert(state.toJava.code.contains("draw(double size)") || state.toJava.code.contains("draw(Object size)"), state.toJava.code)
  }

  test("conversion never throws, whatever is typed") {
    val fragments = Seq("", " ", "(", ")", "{", "}", "def", "class", "class X", "for", "for i in", "while (", "\"", "'", "#", "//", "/*",
      "if:", "else", "return", "x =", "= 3", "forward(", "Turtle.", "public static void main(String[] args) {", "<project>", "<", "\t\tx")
    for fragment <- fragments; origin <- Seq[ProgrammingLanguage](Python, Java, SnapLanguage); target <- languages do {
      val state = ProgrammingStateRaw(origin, fragment)
      val result = try state.convertToLanguage(target) catch { case error: Throwable => fail(s"$origin `$fragment` -> $target threw $error") }
      assert(result.finalState.nonEmpty || result.hasLostFunctionality, s"$origin `$fragment` -> $target")
    }
  }
}
