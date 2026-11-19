package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.naming.BeEntityName
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.vm.code.defining.BeDefineClass
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.conversion.snapxml.SnapXmlParser
import munit.FunSuite
import upickle.default.*

class StructuredProgrammingStateSpec extends FunSuite {
  private def name(value: String) = BeEntityName.fromCodeString(value)
  private val python = """x = 100
    |def doSomething():
    |    forward(20)
    |    forward(20)
    |    forward(20)
    |def main():
    |    doSomething()
    |    forward(x)
    |main()
    |""".stripMargin
  private val java = """class Main {
    | static int x = 100;
    | static void doSomething() { forward(20); forward(20); forward(20); }
    | static void main(String[] unusableArgs) { doSomething(); forward(x); }
    |}
    |""".stripMargin
  private def distances(state: ProgrammingState) = state.toBeExpression.turtleCommandExecutionInfo.completedCommands.flatMap(_.args)

  private val languages = Seq(SnapLanguage, Python, Java)
  private def converted(state: ProgrammingState, language: ProgrammingLanguage): ProgrammingState =
    state.convertToLanguage(language).finalState.getOrElse(fail(s"$state could not be converted to $language"))
  private def assertEmptyIn(state: ProgrammingState, language: ProgrammingLanguage, clue: String): Unit = language match {
    case SnapLanguage =>
      val xml = state.toSnapXml.code
      assertEquals("<script\\b".r.findAllIn(xml).size, 1, s"$clue: one script")
      assertEquals("""<block s="([^"]+)"""".r.findAllMatchIn(xml).map(_.group(1)).toList, List("receiveGo"), s"$clue: only the green flag")
    case Python =>
      assertEquals(state.toPythonStructured.toString, "def main() -> None:\n    pass\nmain()", clue)
    case Java =>
      val classes = state.toJavaStructured.definedClasses
      assertEquals(classes.keySet, Set(name("Main")), clue)
      assertEquals(SourceStructure.javaMembers(classes(name("Main"))).map(_.replaceAll("\\s+", " ")),
        Vector("public static void main(String[] unusableArgs) { }"), clue)
    case other => fail(s"unexpected language $other")
  }

  test("an empty raw source is a green flag in Snap, a called main with pass in Python and a static main in Java, across all conversions") {
    def isEmpty(state: ProgrammingState) = state.toBeExpression.expression match {
      case BeStartProgram(Some(sequence)) => sequence.body.isEmpty
      case BeStartProgram(None) => true
      case _ => false
    }
    languages.foreach { origin =>
      val raw = ProgrammingStateRaw(origin, "")
      assert(isEmpty(raw), s"raw empty $origin")
      // Every conversion chain of length three, starting from the empty raw source.
      for (first <- languages; second <- languages; third <- languages) {
        val chain = Seq(first, second, third).scanLeft(raw: ProgrammingState)(converted).tail
        chain.zip(Seq(first, second, third)).foreach { (state, language) =>
          val clue = s"raw $origin -> $first -> $second -> $third, at $language"
          assertEmptyIn(state, language, clue)
          assert(isEmpty(state), s"$clue: ${state.toBeExpression}")
          assertEquals(state.toBeExpression.turtleCommandExecutionInfo.completedCommands, Nil, clue)
        }
      }
    }
    // The generated representations parse back as written.
    assertEquals(ProgrammingStatePythonStructured(ProgrammingStatePythonStructured("").toString), ProgrammingStatePythonStructured(""))
    assertEquals(ProgrammingStateJavaStructured(ProgrammingStateJavaStructured("").toString), ProgrammingStateJavaStructured(""))
    assert(isEmpty(ProgrammingStateRaw(Python, "def main():\n    pass\n\nmain()")), "a hand-written entry point is read the same way")
  }

  test("Python's called main idiom is the main program, not a helper function") {
    val idiom = ProgrammingStateRaw(Python, "x = 100\ndef main():\n    forward(x)\n\nmain()")
    assertEquals(idiom.toBeExpression, ProgrammingStateRaw(Python, "x = 100\nforward(x)").toBeExpression)
    val helper = ProgrammingStateRaw(Python, "def main():\n    forward(1)\n\nmain()\nmain()")
    assertNotEquals(helper.toBeExpression, ProgrammingStateRaw(Python, "forward(1)\nforward(1)").toBeExpression,
      "a main called twice stays an ordinary function")
  }

  test("Python class structure is inferred, hoisted and rendered before the main script") {
    val state = ProgrammingStatePythonStructured("""x = 1
      |def build():
      |    class Nested:
      |        def hello(self):
      |            print('hello')
      |class Other:
      |    pass
      |forward(x)
      |""".stripMargin)
    assertEquals(state.definedClasses.keySet, Set(name("Nested"), name("Other")))
    assert(!state.mainScript.contains("class "))
    assert(state.mainScript.contains("pass"), state.mainScript)
    assert(state.toString.indexOf("class Other") < state.toString.indexOf("x = 1"))
    assertEquals(ProgrammingStatePythonStructured(state.toString), state)
  }

  test("Java nested classes are hoisted without losing opaque or unfinished bodies") {
    val state = ProgrammingStateJavaStructured("class Outer { void f() { class Inner { Object data; } } }")
    assertEquals(state.definedClasses.keySet, Set(name("Outer"), name("Inner")))
    assert(!state.definedClasses(name("Outer")).contains("class Inner"))
    val draft = "class Main { static void main(String[] args) { forward(\n\t "
    assertEquals(ProgrammingStateJavaStructured(draft).toString, draft)
  }

  test("duplicate classes retain only the first declaration and produce a warning") {
    val warnings = scala.collection.mutable.ListBuffer.empty[String]
    val result = SourceStructure.python("class A:\n    x = 1\nclass A:\n    x = 2", warnings += _)
    assert(result.definedClasses(name("A")).contains("x = 1"))
    assert(!result.remaining.contains("x = 2"))
    assertEquals(warnings.size, 1)
    val javaResult = SourceStructure.java("class A { int x = 1; } class A { int x = 2; }", warnings += _)
    assert(javaResult.definedClasses(name("A")).contains("x = 1"))
    assertEquals(warnings.size, 2)
  }

  test("class words inside strings, multiline strings and comments are not definitions") {
    val py = ProgrammingStatePythonStructured("text = '''\nclass Fake:\n    pass\n'''\n# class Nope:\nclass Real:\n    pass")
    assertEquals(py.definedClasses.keySet, Set(name("Real")))
    val j = ProgrammingStateJavaStructured("class Real { String text = \"class Fake {}\"; /* class Nope {} */ }")
    assertEquals(j.definedClasses.keySet, Set(name("Real")))
  }

  test("the fastparse source lexer tolerates unfinished code and classifies members by their tokens") {
    // Unclosed strings, comments and blocks run to the end instead of failing.
    assertEquals(SourceStructure.javaClasses("class Open { void draw() { forward(\"x").map(_.name), Vector("Open"))
    assertEquals(SourceStructure.mask("x = 1 # c", python = true), "x = 1    ")
    assertEquals(SourceStructure.methodName("static int[] values(int n) { return null; }"), Some("values"))
    assertEquals(SourceStructure.methodName("List<String> names() { return null; }"), Some("names"))
    assertEquals(SourceStructure.methodName("forward(10);"), None)
    assertEquals(SourceStructure.declaration("static double side = 40;"),
      Some(SourceStructure.Declaration("double", "side", Some("40"))))
    assertEquals(SourceStructure.declaration("int[] values;").map(_.name), Some("values"))
    assertEquals(SourceStructure.declaration("return side;"), None)
    assertEquals(SourceStructure.declaration("side = 40;"), None)
    assertEquals(SourceStructure.javaMembers("class A { int[] v = {1, 2}; void f() { } int x; }"),
      Vector("int[] v = {1, 2};", "void f() { }", "int x;"))
    assertEquals(SourceStructure.pythonBlocks("def f(a,\n      b):\n    return (a +\n  b)\nx = 1", "def").map(_.name), Vector("f"))
  }

  test("loose Java variables, methods and statements become static Main members and startup") {
    val state = ProgrammingStateJavaStructured("int x = 100; void draw() { forward(x); } draw();")
    assertEquals(state.definedClasses.keySet, Set(name("Main")))
    assert(state.toString.contains("static int x;"))
    assert(state.toString.contains("x = 100;"))
    assert(state.toString.contains("static void draw()"))
    assert(state.toString.contains("static void main(String[] unusableArgs)"))
    assertEquals(distances(state), List(100.0))
  }

  test("the requested Python example becomes a complete Java class with static globals and helpers") {
    val result = ProgrammingStatePythonStructured(python).toJavaStructured
    // The turtle library class lets the file run on a regular JVM; it is not a user class.
    assertEquals(result.definedClasses.keySet, Set(name("Main"), name("Turtle")))
    assertEquals(distances(result), distances(ProgrammingStatePythonStructured(python)))
    assert(result.toString.contains("static double x = 100;"), result.toString)
    assert(result.toString.contains("static void main(String[] unusableArgs)"), result.toString)
    assertEquals(distances(result), List(20.0, 20.0, 20.0, 100.0))
  }

  test("an entirely static Main becomes Python module code and not a Python class") {
    val result = ProgrammingStateJavaStructured(java).toPythonStructured
    assertEquals(result.definedClasses, Map.empty)
    assert(result.mainScript.contains("def do_something("), result.mainScript)
    assert(result.mainScript.trim.endsWith("def main() -> None:\n    do_something()\n    turtle.forward(x)\nmain()"), result.mainScript)
    assertEquals(distances(result), List(20.0, 20.0, 20.0, 100.0))
    assertEquals(distances(result.toJavaStructured.toPythonStructured), distances(result))
  }

  test("mixed static and instance scope is rejected without changing source") {
    val source = ProgrammingStateJavaStructured("class Shape { int distance = 10; static int angle = 90; Shape() { forward(5); } void draw() { forward(20); } }")
    val before = source.toString
    intercept[IllegalArgumentException](source.toPythonStructured)
    assertEquals(source.toString, before)
  }

  test("ordinary Python classes are retained in Java and precede startup in BeExpression") {
    val state = ProgrammingStatePythonStructured("class Shape:\n    def draw(self, n: float):\n        forward(n)\n\nx = 100\nforward(x)")
    val java = state.toJavaStructured
    assert(java.definedClasses.contains(name("Shape")))
    assert(java.toString.contains("class Main"))
    val expression = state.toBeExpression.expression.asInstanceOf[BeStartProgram]
    assert(expression.startSequence.get.body.head.isInstanceOf[BeDefineClass])
    assertEquals(distances(state), List(100.0))
  }

  test("Snap export rejects unsupported classes rather than dropping them") {
    val state = ProgrammingStatePythonStructured("class Shape:\n    pass\n\nforward(10)")
    val before = state.toString
    intercept[IllegalArgumentException](state.toSnapXml)
    assertEquals(state.toString, before)
  }

  test("Snap project variables, custom blocks and flag startup survive structured round trips") {
    val snap = ProgrammingStatePythonStructured(python).toSnapXml
    val restored = snap.toPythonStructured
    assert(restored.mainScript.contains("100"), restored.toString)
    assert(restored.mainScript.contains("def main("))
    assertEquals(distances(restored), List(20.0, 20.0, 20.0, 100.0))
    assertEquals(distances(restored.toJavaStructured.toPythonStructured.toSnapXml.toPythonStructured), distances(restored))
    val repeated = (1 to 3).foldLeft(snap)((state, _) => state.toPythonStructured.toJavaStructured.toPythonStructured.toSnapXml)
    assertEquals(SnapXmlParser.elements(repeated.code, "block").count(_.attrOrEmpty("s") == "doSetVar"),
      SnapXmlParser.elements(snap.code, "block").count(_.attrOrEmpty("s") == "doSetVar"))
    assertEquals(distances(repeated), distances(restored))
  }

  test("both structured states have current JSON and MessagePack programming-state codecs") {
    val values: List[ProgrammingState] = List(ProgrammingStateJavaStructured(java), ProgrammingStatePythonStructured(python))
    values.foreach { value =>
      assertEquals(read[ProgrammingState](write(value)), value)
      assertEquals(readBinary[ProgrammingState](writeBinary(value)), value)
      assertEquals(read[ProgrammingState](write(value)).fingerprint(), value.fingerprint())
    }
    assertEquals(values.head.convertToLanguage(Python).finalState.get, values.head.toPythonStructured)
    assertEquals(values.last.convertToLanguage(Java).finalState.get, values.last.toJavaStructured)
  }

  test("Python classes with two typed instance methods preserve their instance scope") {
    val state = ProgrammingStatePythonStructured("class Shape:\n    def draw(self, distance: float):\n        forward(distance)\n    def turn(self, angle: float):\n        turn_right(angle)\n\nforward(10)")
    val java = state.toJavaStructured
    val shape = java.definedClasses(name("Shape"))
    assert(!shape.contains("static"), shape)
    assert(!shape.contains("self"), shape)
    val restored = java.toPythonStructured
    assert(restored.definedClasses(name("Shape")).contains("def draw(self, distance: float)"), restored.toString)
    assert(restored.definedClasses(name("Shape")).contains("def turn(self, angle: float)"), restored.toString)
    assertEquals(distances(restored), List(10.0))
  }

  test("module reassignment updates the static field rather than declaring a shadow variable") {
    val state = ProgrammingStatePythonStructured("x = 100\nforward(x)\nx = 200\nforward(x)")
    val java = state.toJavaStructured
    assert(java.toString.contains("x = 200;"), java.toString)
    assert(!java.toString.contains("double x = 200"), java.toString)
    assertEquals(distances(java), List(100.0, 200.0))
  }

  test("a plain Python main wrapper survives expression conversion without recursive wrapping") {
    val state = ProgrammingStateRaw(Python, python).toBeExpression.toPythonStructured
    assertEquals(state.toString.linesIterator.count(_.startsWith("def main(")), 1)
    assertEquals(distances(state.toJavaStructured), List(20.0, 20.0, 20.0, 100.0))
  }

  test("loose Java control flow becomes startup, not a static method declaration") {
    val state = ProgrammingStateJavaStructured("if (true) { forward(12); }")
    assert(!state.toString.contains("static if"), state.toString)
    assertEquals(distances(state), List(12.0))
  }

  test("repeated module assignments stay executable instead of overwriting saved defaults") {
    val state = ProgrammingStatePythonStructured("x = 1\nx = 2\nforward(x)")
    assertEquals(distances(state.toSnapXml), List(2.0))
  }

  test("Java package declarations, imports and comments remain source preamble") {
    val source = "package school;\nimport java.util.List;\n// a draft\nclass Main {static void main(String[] args) {} }\n// end"
    val state = ProgrammingStateJavaStructured(source)
    assert(state.toString.startsWith("package school;\nimport java.util.List;"), state.toString)
    assertEquals("static void main".r.findAllIn(state.toString).size, 1)
    assert(!state.toString.contains("static import"))
    assert(state.toString.contains("// a draft"))
    assertEquals(ProgrammingStateJavaStructured(state.toString), state)
  }

  test("unsupported constructors retain their source when conversion is rejected") {
    val source = ProgrammingStateJavaStructured("class Shape { int distance = 10; static int angle = 90; Shape() { forward(5); } void draw() { forward(20); } }")
    val before = source.toString
    intercept[IllegalArgumentException](source.toPythonStructured)
    assertEquals(source.toString, before)
  }

  test("instance qualifier conversion preserves literal text") {
    val state = ProgrammingStateJavaStructured("class Label { String text() { return \"this.label\"; } }")
    val restored = state.toPythonStructured.toJavaStructured
    assert(state.toPythonStructured.toString.contains("this.label"))
    assert(restored.toString.contains("this.label"), restored.toString)
  }
}
