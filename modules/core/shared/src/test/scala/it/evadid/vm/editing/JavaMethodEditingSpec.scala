package it.evadid.vm.editing

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.parsing.source.JavaFunctionStructured
import munit.FunSuite
import it.evadid.vm.parsing.java.clean.model.JavaAST.{JavaClassDef, JavaMethodDef}

class JavaMethodEditingSpec extends FunSuite {
  import JavaMethodEditing.*

  private def rangeOf(function: JavaFunctionStructured): MethodRange = MethodRange(function.span.start, function.span.end)

  /** Asserts that `source` selects the public static `Program.main` with the given text and parameters. */
  private def assertMainSelected(source: String, expectedMain: String, expectedParameters: String): Option[JavaFunctionStructured] = {
    val selected = initialMethod(source)
    assertEquals(
      selected.map(function => (function.className, function.name, function.returnType, function.parameterList)),
      Some(("Program", "main", Some("void"), expectedParameters))
    )
    assert(selected.exists(function => rangeOf(function).isWithin(source)))
    assertEquals(selected.map(function => source.substring(function.span.start, function.span.end)), Some(expectedMain))
    selected
  }

  test("discovers classes and direct member functions without matching comments or strings") {
    val source =
      """package example;
        |class First {
        |  String note = "class Fake { void nope() {} }";
        |  // public void commentedOut() {}
        |  public int add(int left, int right) {
        |    return left + right;
        |  }
        |}
        |class Second {
        |  public void run() {}
        |}
        |""".stripMargin

    val classes = JavaMethodEditing.discover(source)

    assertEquals(classes.map(_.name), Vector("First", "Second"))
    assertEquals(classes.head.functions.map(_.name), Vector("add"))
    assertEquals(classes.head.functions.head.returnType, Some("int"))
    assertEquals(classes.head.functions.head.parameterList, "int left, int right")
    assertEquals(classes(1).functions.map(_.name), Vector("run"))
  }

  test("the generated Turtle library is not listed as a student class, while an edited copy is") {
    val source = it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(
      it.evadid.core.datastructures.language.AppLanguage.Python, "forward(10)").toJava.code
    assert(source.contains("class Turtle {"), source)
    assertEquals(JavaMethodEditing.discover(source).map(_.name), Vector("Main"))
    val edited = source.replace("move(distance);", "move(2 * distance);")
    assertEquals(JavaMethodEditing.discover(edited).map(_.name), Vector("Main", "Turtle"))
  }

  test("replacing and adding a function preserves a parseable full Java program") {
    val source = "class Example {\n  public int add(int left, int right) {\n    return left + right;\n  }\n  public void keep() {}\n}\n"
    val clazz = JavaMethodEditing.discover(source).head
    val original = clazz.functions.head
    val editedDraft = "\n  public int sum(int left, int right) {\n    return left + right;\n  }"
    val edited = JavaMethodEditing.replaceMethod(
      source,
      original,
      editedDraft
    )
    val afterEditClasses = JavaMethodEditing.discover(edited)
    val afterEdit = afterEditClasses.head
    val selection = JavaMethodEditing.findEditedMethod(
      edited,
      afterEditClasses,
      original,
      MethodRange(original.span.start, original.span.start + editedDraft.length)
    )
    assertEquals(selection.map(_.name), Some("sum"))
    val withNewFunction = JavaMethodEditing.insertMethod(
      edited,
      afterEdit,
      "public void reset() {\n    \n  }"
    )

    assert(JavaMethodEditing.parses(edited))
    assert(JavaMethodEditing.parses(withNewFunction))
    assert(!JavaMethodEditing.parses("class Example {"))
    assertEquals(
      JavaMethodEditing.discover(withNewFunction).head.functions.map(_.name),
      Vector("sum", "keep", "reset")
    )
  }

  test("function declarations include static only when selected and remain parseable") {
    List(true, false).foreach { isStatic =>
      val declaration = methodDeclaration(returnType = "void", name = "draw",
        parameters = "int distance", isStatic = isStatic)
      val modifiers = if isStatic then "public static" else "public"
      assertEquals(declaration, s"$modifiers void draw(int distance) {\n    \n  }")
      val program = SourceParsing.parse(s"class Drawing { $declaration }", SourceLanguage.Java).syntax.map(_.program)
        .fold(error => fail(error.message), identity)
      assertEquals(program.statements.size, 1)
      val clazz = program.statements.head.statement match {
        case value: JavaClassDef => value
        case other => fail(s"Expected a class definition, got $other")
      }
      assertEquals(clazz.body.statements.size, 1)
      val method = clazz.body.statements.head match {
        case value: JavaMethodDef => value
        case other => fail(s"Expected a method definition, got $other")
      }
      assertEquals(method.name, "draw")
      assertEquals(method.parameters.map(_.name), Seq("distance"))
      assertEquals(method.modifiers, if isStatic then Seq("public", "static") else Seq("public"))
    }
  }

  test("inserting static and instance declarations preserves surrounding source and separators") {
    val original = "// \ud83d\ude42 unchanged\nclass Drawing {\n\tpublic static void main(String[] args) {}\n\tprivate void keep() {}\n\t \n}  \t\n\nclass Other { public void keepOther() {} }\n\t "
    for {
      separator <- List("\n", "\r\n", "\r")
      isStatic <- List(true, false)
    } {
      val source = original.replace("\n", separator)
      val clazz = discover(source).head
      val declaration = methodDeclaration(returnType = "void", name = "draw",
        parameters = "double distance", isStatic = isStatic)
      val inserted = insertMethod(source, clazz, declaration)
      val normalized = declaration.replace("\n", separator)
      assertEquals(inserted, source.substring(0, closingBrace(clazz)) + separator + "  " + normalized + separator + source.substring(closingBrace(clazz)))
      assert(parses(inserted))
      assertEquals(discover(inserted).head.functions.map(_.name), Vector("main", "keep", "draw"))
      assertEquals(discover(inserted).map(_.name), Vector("Drawing", "Other"))
    }
  }

  test("owned ranges preserve surrounding source across invalid edits and recovery") {
    val source = "class Example {\r\n  public void edit() {}\r\n  public void keep() { int value = 9; }\r\n}\r\n\t  "
    val original = discover(source).head.functions.head
    val prefix = source.substring(0, original.span.start)
    val suffix = source.substring(original.span.end)
    var currentSource = source
    var range = rangeOf(original)
    val drafts = List(
      "public void edit(",
      "\n  public void edit() {\n    int value = ",
      "\n  public void repaired() {\n    int value = 8;\n  }\n  "
    )

    drafts.foreach { draft =>
      val (replaced, nextRange) = range.replace(currentSource, draft).get
      assertEquals(replaced, prefix + draft + suffix)
      assertEquals(replaced.substring(nextRange.start, nextRange.end), draft)
      assertEquals(nextRange, MethodRange(original.span.start, original.span.start + draft.length))
      currentSource = replaced
      range = nextRange
    }

    assertEquals(findEditedMethod(currentSource, discover(currentSource), original, range).map(_.name), Some("repaired"))
    assertEquals(discover(currentSource).head.functions.map(_.name), Vector("repaired", "keep"))
  }

  test("owned ranges validate UTF-16 bounds and support empty insertions") {
    val source = "\ud83d\ude42 prefix method tail"
    val start = source.indexOf("method")
    val range = MethodRange(start, start + "method".length)
    assertEquals(range.replace(source, "replacement"), Some(
      (source.substring(0, start) + "replacement" + source.substring(range.end), MethodRange(start, start + 11))
    ))
    assertEquals(MethodRange(0, 0).replace(source, "x").map(_._1), Some("x" + source))
    assertEquals(MethodRange(source.length, source.length).replace(source, "x").map(_._1), Some(source + "x"))
    assertEquals(MethodRange(0, source.length).replace(source, ""), Some(("", MethodRange(0, 0))))
    List(
      MethodRange(-1, 0),
      MethodRange(0, -1),
      MethodRange(3, 2),
      MethodRange(0, source.length + 1),
      MethodRange(source.length + 1, source.length + 1)
    ).foreach(invalid => assertEquals(invalid.replace(source, "x"), None))
  }

  test("method gutter lines use the original range across line endings and leading draft trivia") {
    val original = "// Grüße 🙂\nclass Example {\n  public void helper() {}\n\n  public static void main(String[] args) {}\n}\n"
    List("\n", "\r\n", "\r").foreach { separator =>
      val source = original.replace("\n", separator)
      val selected = initialMethod(source).get
      val range = rangeOf(selected)
      assertEquals(firstLineNumber(source, Some(range)), 5)
      val draft = separator + "/* retained */" + separator + source.substring(range.start, range.end)
      val (edited, nextRange) = range.replace(source, draft).get
      assertEquals(firstLineNumber(edited, Some(nextRange)), 5)
      assertEquals(edited.substring(nextRange.start, nextRange.end), draft)
      val moved = "// inserted" + separator + source
      val movedMain = initialMethod(moved).get
      assertEquals(firstLineNumber(moved, Some(rangeOf(movedMain))), 6)
      assertEquals(firstLineNumber(source, None), 1)
      assertEquals(firstLineNumber(source, Some(MethodRange(0, source.length + 1))), 1)
    }
    assertEquals(firstLineNumber("class Example { public void helper() {} }", Some(MethodRange(16, 39))), 1)
  }

  test("gutter offsets handle mixed separators, UTF-16 positions and EOF") {
    List(
      "" -> Vector(1),
      "x\nz" -> Vector(1, 1, 2, 2),
      "x\r\nz" -> Vector(1, 1, 1, 2, 2),
      "x\rz" -> Vector(1, 1, 2, 2),
      "🐢\r\nx\rz\n" -> Vector(1, 1, 1, 1, 2, 2, 3, 3, 4)
    ).foreach { (source, expectedLines) =>
      assertEquals(expectedLines.length, source.length + 1)
      expectedLines.zipWithIndex.foreach { (expectedLine, offset) =>
        assertEquals(firstLineNumber(source, Some(MethodRange(offset, source.length))), expectedLine,
          clue = (source, offset))
      }
    }
  }

  private val singleMethodSource = "class Example {\n  public void edit() {}\n  public void keep() {}\n}\n"

  private def candidateFor(draft: String): Option[JavaFunctionStructured] = {
    val original = discover(singleMethodSource).head.functions.head
    val (source, range) = rangeOf(original).replace(singleMethodSource, draft).get
    findEditedMethod(source, discover(source), original, range)
  }

  test("method projection accepts surrounding whitespace and comments without trimming the range") {
    val draft = "\r\n /* \ud83d\ude42 void fake() {} */\r\n public void renamed() {} // retained tail\r\n\t "
    assertEquals(candidateFor(draft).map(_.name), Some("renamed"))
    val original = discover(singleMethodSource).head.functions.head
    val (source, range) = rangeOf(original).replace(singleMethodSource, draft).get
    assertEquals(source.substring(range.start, range.end), draft)
    assertEquals(candidateFor("/* incomplete"), None)
  }

  test("method projection selects the last newly inserted function") {
    assertEquals(candidateFor("public void renamed() {} public void pasted() {}").map(_.name), Some("pasted"))
  }

  test("method projection rejects fields, initializer blocks and non-comment text") {
    List(
      "public void renamed() {} int field = 1;",
      "public void renamed() {} static { int value = 1; }",
      "public void renamed() {} \"not whitespace\"",
      "public void renamed() {} ???;",
      "} class Other { public void pasted() {}"
    ).foreach(draft => assertEquals(candidateFor(draft), None, clue = draft))
  }

  test("method projection requires full containment and an unambiguous class") {
    val classes = discover(singleMethodSource)
    val original = classes.head.functions.head
    assertEquals(findEditedMethod(singleMethodSource, classes, original,
      MethodRange(original.span.start, original.span.end - 1)), None)
    assertEquals(findEditedMethod(singleMethodSource, classes, original,
      MethodRange(original.span.start + 1, original.span.end)), None)
    assertEquals(findEditedMethod(singleMethodSource, classes :+ classes.head, original,
      rangeOf(original)), None)
    assertEquals(findEditedMethod(singleMethodSource, classes, original.copy(className = "Missing"),
      rangeOf(original)), None)

    val multipleClasses = singleMethodSource + "class Other { public void untouched() {} }"
    val draft = "public void renamed() {}"
    val (changed, range) = rangeOf(original).replace(multipleClasses, draft).get
    assertEquals(findEditedMethod(changed, discover(changed), original, range).map(_.name), Some("renamed"))
  }

  test("selection follows the complete signature across source reordering") {
    val source = "class Example { public int value(int n) { return n; } public int value(boolean flag) { return 1; } }"
    val classes = discover(source)
    val previous = classes.head.functions.head
    val reordered = discover("class Example { public int value(boolean flag) { return 1; } public int value(int n) { return n; } }")
    assertEquals(findSelection(reordered, previous).map(_.parameterList), Some("int n"))
    assertEquals(findSelection(reordered, previous.copy(parameterList = "String value")), None)
    assertEquals(findSelection(reordered, previous.copy(returnType = Some("void"))), None)
    assertEquals(findSelection(reordered, previous.copy(className = "Other")), None)
  }

  test("selection rejects duplicate methods and duplicate class names") {
    val classes = discover("class Example { public void edit() {} }")
    val previous = classes.head.functions.head
    val duplicateMethod = classes.head.copy(members = Vector(previous, previous.copy(span = previous.span.copy(start = previous.span.start + 1))))
    assertEquals(findSelection(Vector(duplicateMethod), previous), None)
    assertEquals(findSelection(classes :+ classes.head, previous), None)
    assertEquals(findSelection(Vector.empty, previous), None)
  }

  test("method identity ignores offsets but distinguishes signatures") {
    val original = discover(singleMethodSource).head.functions.head
    assert(sameMethod(original, original.copy(span = original.span.copy(start = 300, end = 400))))
    List(
      original.copy(className = "Other"),
      original.copy(name = "other"),
      original.copy(returnType = Some("int")),
      original.copy(parameterList = "int value")
    ).foreach(other => assert(!sameMethod(original, other)))
  }

  test("comment discovery handles LF, CRLF and CR without changing source offsets") {
    val source = "class Example {\n  // public void fake() {}\n  public void edit() { String value = \"}\"; }\n}\n"
    List("\n", "\r\n", "\r").foreach { separator =>
      val code = source.replace("\n", separator)
      val classes = discover(code)
      assertEquals(classes.map(_.name), Vector("Example"), clue = separator)
      assertEquals(classes.head.functions.map(_.name), Vector("edit"), clue = separator)
      val method = classes.head.functions.head
      assertEquals(code.substring(method.span.start, method.span.end), "public void edit() { String value = \"}\"; }")
      assert(parses(code), clue = separator)
    }
  }

  test("initial function selects the unique main and preserves its exact source range") {
    val expectedMain = "public static void main(String[] args) {\n    helper();\n  }"
    val source =
      "class Program {\n" +
        "  public static void helper() { int value = 1; }\n" +
        s"  $expectedMain\n" +
        "  private int untouched() { return 7; }\n" +
        "}\n"

    val selected = assertMainSelected(source, expectedMain, "String[] args")
    assertEquals(selected.map(function => replaceMethod(source, function, expectedMain)), Some(source))
  }

  test("initial function rejects main methods across multiple classes") {
    val source =
      "class First { public static void main(String[] args) {} }\n" +
        "class Second { public static void main(String[] args) {} }\n"

    assertEquals(initialMethod(source), None)
  }

  test("initial function rejects ambiguous class names and duplicate main signatures") {
    val duplicateClassName =
      "class Program { public static void main(String[] args) {} }\n" +
        "class Program { public void helper() {} }\n"
    val duplicateMainSignature =
      "class Program {\n" +
        "  public static void main(String[] args) {}\n" +
        "  public static void main(String[] args) {}\n" +
        "}\n"

    assertEquals(initialMethod(duplicateClassName), None)
    assertEquals(initialMethod(duplicateMainSignature), None)
  }

  test("initial function rejects missing main and structurally incomplete source") {
    List(
      "class Program { public void helper() {} }",
      "class Program { public static void main(String[] args) {}",
      "class Program { public static void main(String[] args) {} /* unclosed"
    ).foreach(source => assertEquals(initialMethod(source), None, clue = source))
  }

  test("initial function after a helper matches discovery from the complete fresh source") {
    val source =
      "class Program {\r\n" +
        "  public String helper() { return \"class Fake { void main() {} }\"; }\r\n" +
        "  public static void main(String[] args) {\r\n" +
        "    helper();\r\n" +
        "  }\r\n" +
        "}\r\n"
    val discoveredMain = discover(source).flatMap(_.functions).filter(_.name == "main")

    assertEquals(discoveredMain.length, 1)
    assertEquals(initialMethod(source), discoveredMain.headOption)
    assertEquals(
      initialMethod(source).map(function => source.substring(function.span.start, function.span.end)),
      Some("public static void main(String[] args) {\r\n    helper();\r\n  }")
    )
  }

  test("initial function rejects invalid entry point signatures and named main overloads") {
    List(
      "non-public" -> "class Program { static void main(String[] args) {} }",
      "non-static" -> "class Program { public void main(String[] args) {} }",
      "wrong return type" -> "class Program { public static int main(String[] args) { return 0; } }",
      "wrong parameters" -> "class Program { public static void main(int[] args) {} }",
      "named overload" ->
        "class Program { public static void main(String[] args) {} public void main(int value) {} }"
    ).foreach { case (label, source) =>
      assertEquals(initialMethod(source), None, clue = label)
    }
  }

  test("initial function accepts qualified String arrays, modifier order and parameter names") {
    List(
      (
        "class Program { public static void main(java.lang.String[] arguments) {} }",
        "public static void main(java.lang.String[] arguments) {}",
        "java.lang.String[] arguments"
      ),
      (
        "class Program { static final public void main(String [ ] $arguments) {} }",
        "static final public void main(String [ ] $arguments) {}",
        "String [ ] $arguments"
      ),
      (
        "class Program { static public void main(java.lang.String [ ] _args) {} }",
        "static public void main(java.lang.String [ ] _args) {}",
        "java.lang.String [ ] _args"
      )
    ).foreach { case (source, expectedMain, expectedParameters) =>
      assertMainSelected(source, expectedMain, expectedParameters)
    }
  }
}
