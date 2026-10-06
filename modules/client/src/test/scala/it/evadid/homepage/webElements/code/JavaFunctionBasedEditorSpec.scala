package it.evadid.homepage.webElements.code

import munit.FunSuite

class JavaFunctionBasedEditorSpec extends FunSuite {
  import JavaFunctionBasedEditor.*

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

    val classes = JavaFunctionBasedEditor.discover(source)

    assertEquals(classes.map(_.name), List("First", "Second"))
    assertEquals(classes.head.functions.map(_.name), List("add"))
    assertEquals(classes.head.functions.head.returnType, "int")
    assertEquals(classes.head.functions.head.parameters, "int left, int right")
    assertEquals(classes(1).functions.map(_.name), List("run"))
  }

  test("replacing and adding a function preserves a parseable full Java program") {
    val source = "class Example {\n  public int add(int left, int right) {\n    return left + right;\n  }\n  public void keep() {}\n}\n"
    val clazz = JavaFunctionBasedEditor.discover(source).head
    val original = clazz.functions.head
    val editedDraft = "\n  public int sum(int left, int right) {\n    return left + right;\n  }"
    val edited = JavaFunctionBasedEditor.replaceFunction(
      source,
      original,
      editedDraft
    )
    val afterEditClasses = JavaFunctionBasedEditor.discover(edited)
    val afterEdit = afterEditClasses.head
    val selection = JavaFunctionBasedEditor.findEditedFunction(
      edited,
      afterEditClasses,
      original,
      FunctionRange(original.start, original.start + editedDraft.length)
    )
    assertEquals(selection.map(_.name), Some("sum"))
    val withNewFunction = JavaFunctionBasedEditor.insertFunction(
      edited,
      afterEdit,
      "public void reset() {\n    \n  }"
    )

    assert(JavaFunctionBasedEditor.parses(edited))
    assert(JavaFunctionBasedEditor.parses(withNewFunction))
    assert(!JavaFunctionBasedEditor.parses("class Example {"))
    assertEquals(
      JavaFunctionBasedEditor.discover(withNewFunction).head.functions.map(_.name),
      List("sum", "keep", "reset")
    )
  }

  test("owned ranges preserve surrounding source across invalid edits and recovery") {
    val source = "class Example {\r\n  public void edit() {}\r\n  public void keep() { int value = 9; }\r\n}\r\n\t  "
    val original = discover(source).head.functions.head
    val prefix = source.substring(0, original.start)
    val suffix = source.substring(original.end)
    var currentSource = source
    var range = FunctionRange(original.start, original.end)
    val drafts = List(
      "public void edit(",
      "\n  public void edit() {\n    int value = ",
      "\n  public void repaired() {\n    int value = 8;\n  }\n  "
    )

    drafts.foreach { draft =>
      val (replaced, nextRange) = range.replace(currentSource, draft).get
      assertEquals(replaced, prefix + draft + suffix)
      assertEquals(replaced.substring(nextRange.start, nextRange.end), draft)
      assertEquals(nextRange, FunctionRange(original.start, original.start + draft.length))
      currentSource = replaced
      range = nextRange
    }

    assertEquals(findEditedFunction(currentSource, discover(currentSource), original, range).map(_.name), Some("repaired"))
    assertEquals(discover(currentSource).head.functions.map(_.name), List("repaired", "keep"))
  }

  test("owned ranges validate UTF-16 bounds and support empty insertions") {
    val source = "\ud83d\ude42 prefix method tail"
    val start = source.indexOf("method")
    val range = FunctionRange(start, start + "method".length)
    assertEquals(range.replace(source, "replacement"), Some(
      (source.substring(0, start) + "replacement" + source.substring(range.end), FunctionRange(start, start + 11))
    ))
    assertEquals(FunctionRange(0, 0).replace(source, "x").map(_._1), Some("x" + source))
    assertEquals(FunctionRange(source.length, source.length).replace(source, "x").map(_._1), Some(source + "x"))
    assertEquals(FunctionRange(0, source.length).replace(source, ""), Some(("", FunctionRange(0, 0))))
    List(
      FunctionRange(-1, 0),
      FunctionRange(0, -1),
      FunctionRange(3, 2),
      FunctionRange(0, source.length + 1),
      FunctionRange(source.length + 1, source.length + 1)
    ).foreach(invalid => assertEquals(invalid.replace(source, "x"), None))
  }

  private val singleMethodSource = "class Example {\n  public void edit() {}\n  public void keep() {}\n}\n"

  private def candidateFor(draft: String): Option[JavaFunction] = {
    val original = discover(singleMethodSource).head.functions.head
    val (source, range) = FunctionRange(original.start, original.end).replace(singleMethodSource, draft).get
    findEditedFunction(source, discover(source), original, range)
  }

  test("method projection accepts surrounding whitespace and comments without trimming the range") {
    val draft = "\r\n /* \ud83d\ude42 void fake() {} */\r\n public void renamed() {} // retained tail\r\n\t "
    assertEquals(candidateFor(draft).map(_.name), Some("renamed"))
    val original = discover(singleMethodSource).head.functions.head
    val (source, range) = FunctionRange(original.start, original.end).replace(singleMethodSource, draft).get
    assertEquals(source.substring(range.start, range.end), draft)
    assertEquals(candidateFor("/* incomplete"), None)
  }

  test("method projection rejects extra declarations and non-comment text") {
    List(
      "public void renamed() {} public void pasted() {}",
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
    assertEquals(findEditedFunction(singleMethodSource, classes, original,
      FunctionRange(original.start, original.end - 1)), None)
    assertEquals(findEditedFunction(singleMethodSource, classes, original,
      FunctionRange(original.start + 1, original.end)), None)
    assertEquals(findEditedFunction(singleMethodSource, classes :+ classes.head, original,
      FunctionRange(original.start, original.end)), None)
    assertEquals(findEditedFunction(singleMethodSource, classes, original.copy(className = "Missing"),
      FunctionRange(original.start, original.end)), None)

    val multipleClasses = singleMethodSource + "class Other { public void untouched() {} }"
    val draft = "public void renamed() {}"
    val (changed, range) = FunctionRange(original.start, original.end).replace(multipleClasses, draft).get
    assertEquals(findEditedFunction(changed, discover(changed), original, range).map(_.name), Some("renamed"))
  }

  test("selection follows the complete signature across source reordering") {
    val source = "class Example { public int value(int n) { return n; } public int value(boolean flag) { return 1; } }"
    val classes = discover(source)
    val previous = classes.head.functions.head
    val reordered = discover("class Example { public int value(boolean flag) { return 1; } public int value(int n) { return n; } }")
    assertEquals(findSelection(reordered, previous).map(_.parameters), Some("int n"))
    assertEquals(findSelection(reordered, previous.copy(parameters = "String value")), None)
    assertEquals(findSelection(reordered, previous.copy(returnType = "void")), None)
    assertEquals(findSelection(reordered, previous.copy(className = "Other")), None)
  }

  test("selection rejects duplicate methods and duplicate class names") {
    val classes = discover("class Example { public void edit() {} }")
    val previous = classes.head.functions.head
    val duplicateMethod = classes.head.copy(functions = List(previous, previous.copy(start = previous.start + 1)))
    assertEquals(findSelection(List(duplicateMethod), previous), None)
    assertEquals(findSelection(classes :+ classes.head, previous), None)
    assertEquals(findSelection(Nil, previous), None)
  }

  test("method identity ignores offsets but distinguishes signatures") {
    val original = discover(singleMethodSource).head.functions.head
    assert(sameFunction(original, original.copy(start = 300, end = 400)))
    List(
      original.copy(className = "Other"),
      original.copy(name = "other"),
      original.copy(returnType = "int"),
      original.copy(parameters = "int value")
    ).foreach(other => assert(!sameFunction(original, other)))
  }

  test("comment discovery handles LF, CRLF and CR without changing source offsets") {
    val source = "class Example {\n  // public void fake() {}\n  public void edit() { String value = \"}\"; }\n}\n"
    List("\n", "\r\n", "\r").foreach { separator =>
      val code = source.replace("\n", separator)
      val classes = discover(code)
      assertEquals(classes.map(_.name), List("Example"), clue = separator)
      assertEquals(classes.head.functions.map(_.name), List("edit"), clue = separator)
      val method = classes.head.functions.head
      assertEquals(code.substring(method.start, method.end), "public void edit() { String value = \"}\"; }")
      assert(parses(code), clue = separator)
    }
  }

  test("adding a method preserves whitespace before and after the class closing brace") {
    val source = "// unchanged\nclass Example {\r\n\tpublic void keep() {}\r\n\t \r\n}  \t\r\n\r\n"
    val clazz = discover(source).head
    val declaration = "public void reset() {}"
    val inserted = insertFunction(source, clazz, declaration)
    assert(inserted.startsWith(source.substring(0, clazz.bodyEnd)))
    assert(inserted.endsWith(source.substring(clazz.bodyEnd)))
    assertEquals(discover(inserted).head.functions.map(_.name), List("keep", "reset"))
  }
}
