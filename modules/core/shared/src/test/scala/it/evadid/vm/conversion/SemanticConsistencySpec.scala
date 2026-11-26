package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.vm.code.errors.ProgrammingConversionKind
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

/** Programs keep their meaning in every language, including where the languages' rules differ:
  * module variables assigned in functions, string equality, truth values, chained comparisons,
  * reserved words, multi-value printing and Unicode names.
  */
class SemanticConsistencySpec extends FunSuite {
  private def converted(state: ProgrammingState, language: ProgrammingLanguage): ProgrammingState = {
    val result = state.convertToLanguage(language)
    result.finalState.getOrElse(fail(s"no $language state: ${result.conversionInfo}\n$state"))
  }
  private def text(state: ProgrammingState) = state.toString.split("\nclass Turtle")(0)
  private def drawing(state: ProgrammingState) = state.toBeExpression.turtleCommandExecutionInfo.completedCommands.flatMap(_.args)
  private def everyLanguage(state: ProgrammingState): Seq[ProgrammingState] =
    Seq(Python, Java, SnapLanguage).map(converted(state, _))
  private def java(members: String, main: String) =
    ProgrammingStateRaw(Java, s"class Main {\n$members\n    public static void main(String[] args) {\n$main\n    }\n}\n")

  test("a helper that assigns a module variable updates it in every language") {
    val fromJava = java("    static double count = 0;\n    static void step() {\n        count = count + 1;\n        Turtle.forward(count);\n    }",
      "        step();\n        step();")
    val python = converted(fromJava, Python)
    assert(text(python).contains("def step():\n    global count\n    count = count + 1"), text(python))
    val fromPython = ProgrammingStateRaw(Python, "count = 0\ndef step():\n    global count\n    count = count + 1\n    forward(count)\nstep()\nstep()\n")
    Seq(fromJava, fromPython).foreach { source =>
      assertEquals(drawing(source), List(1.0, 2.0))
      everyLanguage(source).foreach(state => assertEquals(drawing(state), List(1.0, 2.0), state.toString))
    }
  }

  test("a Python local that hides a module variable is reported, as the shared tree has no such shadowing") {
    val source = ProgrammingStateRaw(Python, "size = 100\ndef draw():\n    size = 10\n    forward(size)\ndraw()\nforward(size)\n")
    val result = source.convertToLanguage(Java)
    val error = result.conversionInfo.head
    assert(error.warningMsg.contains("hides the module variable size"), error.warningMsg)
    assertEquals(error.sourceError.map(e => (e.kind, e.line)), Some((ProgrammingConversionKind.UnsupportedConstruct, Some(2))))
  }

  test("text is compared by content in Java and read back as equality") {
    val python = ProgrammingStateRaw(Python, "name = \"ab\"\nif name == \"ab\":\n    forward(10)\nif name != \"x\":\n    forward(1)\n")
    val javaText = text(converted(python, Java))
    assert(javaText.contains("if (name.equals(\"ab\")) {") && javaText.contains("if (!name.equals(\"x\")) {"), javaText)
    assert(text(converted(converted(python, Java), Python)).contains("if name == \"ab\":"))
    val fromJava = java("", "        String s = \"ab\";\n        if (!s.isEmpty() && s.equals(\"ab\")) {\n            Turtle.forward(3);\n        }")
    assert(text(converted(fromJava, Python)).contains("if (s != \"\") and (s == \"ab\"):"), text(converted(fromJava, Python)))
    assertEquals(drawing(fromJava), List(3.0))
  }

  test("numbers used as conditions compare with zero in Java") {
    val python = ProgrammingStateRaw(Python, "count = 3\nwhile count:\n    forward(count)\n    count = count - 1\nif not count:\n    forward(9)\n")
    val javaText = text(converted(python, Java))
    assert(javaText.contains("while (count != 0) {") && javaText.contains("if (!(count != 0)) {"), javaText)
    everyLanguage(python).foreach(state => assertEquals(drawing(state), List(3.0, 2.0, 1.0, 9.0), state.toString))
  }

  test("chained comparisons mean each pair holds") {
    val python = ProgrammingStateRaw(Python, "x = 5\nif 0 < x < 10:\n    forward(x)\nif 0 < x < 3:\n    forward(1)\n")
    assert(text(converted(python, Java)).contains("if ((0 < x) && (x < 10)) {"), text(converted(python, Java)))
    everyLanguage(python).foreach(state => assertEquals(drawing(state), List(5.0), state.toString))
  }

  test("names reserved in the target language get a trailing underscore and read back") {
    val python = ProgrammingStateRaw(Python, "new = 5\ndouble = 2\nforward(new * double)\n")
    assert(text(converted(python, Java)).contains("static double new_ = 5;"), text(converted(python, Java)))
    assert(text(converted(converted(python, Java), Python)).contains("new: float = 5"))
    val fromJava = java("", "        double lambda = 3;\n        double pass = 2;\n        Turtle.forward(lambda * pass);")
    assert(text(converted(fromJava, Python)).contains("lambda_: float = 3"), text(converted(fromJava, Python)))
    everyLanguage(python).foreach(state => assertEquals(drawing(state), List(10.0), state.toString))
  }

  test("print with several values joins them with spaces in Java, and Python converts non-text operands") {
    val python = ProgrammingStateRaw(Python, "x = 3\nprint(\"x is\", x)\n")
    assert(text(converted(python, Java)).contains("System.out.println(\"x is\" + \" \" + x);"), text(converted(python, Java)))
    val fromJava = java("", "        double n = 3;\n        System.out.println(\"n=\" + n);")
    assert(text(converted(fromJava, Python)).contains("print(\"n=\" + str(n))"), text(converted(fromJava, Python)))
  }

  test("Unicode names are ordinary names in Python and Java") {
    val python = ProgrammingStateRaw(Python, "länge = 30\ngrößeX = 2\nforward(länge * größeX)\n")
    assert(text(converted(python, Java)).contains("static double länge = 30;"), text(converted(python, Java)))
    everyLanguage(python).foreach(state => assertEquals(drawing(state), List(60.0), state.toString))
  }

  test("the remainder of negative numbers is flagged between Java and Python or Snap, not elsewhere") {
    val python = ProgrammingStateRaw(Python, "x = -7 % 3\nforward(x)\n")
    assert(python.convertToLanguage(Java).conversionInfo.exists(_.warningMsg.contains("sign of the divisor")))
    assert(!python.convertToLanguage(SnapLanguage).conversionInfo.exists(_.warningMsg.contains("sign of the divisor")))
    assertEquals(drawing(python), List(2.0), "the shared tree keeps Python's and Snap's floor remainder")
  }

  test("Java programs carrying the generated Turtle class do not report integer widening") {
    val generated = converted(ProgrammingStateRaw(Python, "for _ in range(4):\n    forward(10)\n    right(90)\nx = int(2.5)\n"), Java)
    val result = generated.convertToLanguage(Python)
    assert(!result.hasLostFunctionality, result.conversionInfo.toString)
    assert(java("    static int value = 2;", "        Turtle.forward(value);").convertToLanguage(Python).hasLostFunctionality)
  }

  test("the drawing simulation computes with text, floor division and powers instead of reading them as 0") {
    val python = ProgrammingStateRaw(Python,
      "word = \"ab\" + str(3)\nif word == \"ab3\" and word:\n    forward(7 // 2)\nforward(2 ** 3)\nforward(int(\"4\"))\n")
    // Snap has no blocks for text joining, floor division or powers, so only Python and Java run it.
    Seq(python, converted(python, Java)).foreach(state => assertEquals(drawing(state), List(3.0, 8.0, 4.0), state.toString))
    assert(python.convertToLanguage(SnapLanguage).hasLostFunctionality)
    val failed = ProgrammingStateRaw(Python, "distance = \"far\"\nforward(distance)\n").toBeExpression.turtleCommandExecutionInfo.executionStatus
    assert(failed.toString.contains("is not a number"), failed.toString)
  }

  private def snap(scripts: String, variables: String = "") = ProgrammingStateRaw(SnapLanguage,
    s"""<project name="p" app="Snap! 9" version="2"><scenes select="1"><scene name="p"><blocks></blocks><stage name="Stage"><sprites select="1">""" +
      s"""<sprite name="S"><scripts><script><block s="receiveGo"/>$scripts</script></scripts></sprite></sprites></stage><variables>$variables</variables></scene></scenes></project>""")

  test("Snap slots keep Snap's meaning: empty conditions are false, slot text is text, repeat counts may be computed") {
    val emptyCondition = snap("""<block s="doIf"><l/><script><block s="forward"><l>10</l></block></script><list></list></block>""")
    val falseLiteral = snap("""<block s="doIf"><l><bool>false</bool></l><script><block s="forward"><l>10</l></block></script><list></list></block>""")
    Seq(emptyCondition, falseLiteral).foreach(state => assertEquals(drawing(state), Nil))
    val computedRepeat = snap("""<block s="doSetVar"><l>n</l><l>3</l></block><block s="doRepeat"><block var="n"/><script><block s="forward"><l>5</l></block></script></block>""",
      """<variable name="n"><l>0</l></variable>""")
    everyLanguage(computedRepeat).foreach(state => assertEquals(drawing(state), List(5.0, 5.0, 5.0), state.toString))
    assert(text(converted(computedRepeat, Python)).contains("for _ in range(n):"))
    val textSlot = snap("""<block s="bubble"><l>A &amp; B &lt;3 &#228;</l></block>""")
    assert(text(converted(textSlot, Python)).contains("print(\"A & B <3 ä\")"), text(converted(textSlot, Python)))
    val wordInOperator = snap("""<block s="doIf"><block s="reportVariadicEquals"><list><l>abc</l><l>abc</l></list></block><script><block s="forward"><l>1</l></block></script><list></list></block>""")
    assert(text(converted(wordInOperator, Python)).contains("if \"abc\" == \"abc\":"), "slot words are text, not variables")
    assertEquals(drawing(wordInOperator), List(1.0))
    val spacedName = snap("""<block s="doSetVar"><l>my size</l><l>7</l></block><block s="forward"><block var="my size"/></block>""",
      """<variable name="my size"><l>0</l></variable>""")
    val python = text(converted(spacedName, Python))
    assert(python.contains("my_size: Any = 7") && python.contains("turtle.forward(my_size)") && !python.contains("my size"), python)
  }

  test("unsupported Snap blocks are reported without internal messages") {
    val result = snap("""<block s="doForever"><script><block s="forward"><l>1</l></block></script></block>""").convertToLanguage(Python)
    assert(result.conversionInfo.head.warningMsg.startsWith("Unsupported Snap blocks: doForever"), result.conversionInfo.toString)
  }
}
