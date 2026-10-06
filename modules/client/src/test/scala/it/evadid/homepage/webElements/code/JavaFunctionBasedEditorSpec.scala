package it.evadid.homepage.webElements.code

import munit.FunSuite

class JavaFunctionBasedEditorSpec extends FunSuite {
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
    val selection = JavaFunctionBasedEditor.findEditedFunction(afterEditClasses, original, editedDraft.length)
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
}
