package it.evadid.facades.internal

class CodeIndentationSpec extends munit.FunSuite {
  private val unit = "    "

  /** The document after Enter at `|`, with the cursor shown as `|`. */
  private def enter(source: String, python: Boolean = false): String = {
    val (before, after) = source.splitAt(source.indexOf('|'))
    val edit = CodeIndentation.enter(before, after.drop(1), unit, python)
    val kept = before.dropRight(edit.deleteBefore)
    kept + edit.insert.take(edit.cursor) + "|" + edit.insert.drop(edit.cursor) + after.drop(1)
  }

  test("a new line keeps the indentation of the previous one") {
    assertEquals(enter("void f() {\n    forward(1);|\n}"), "void f() {\n    forward(1);\n    |\n}")
  }

  test("after an opening brace the line is indented one level more") {
    assertEquals(enter("class Main {\n    void f() {|\n    }\n}"), "class Main {\n    void f() {\n        |\n    }\n}")
  }

  test("an unclosed block gets its closing brace on the line below") {
    assertEquals(enter("    void f() {|"), "    void f() {\n        |\n    }")
    assertEquals(enter("class Main {\n    void f() {|\n}"), "class Main {\n    void f() {\n        |\n    }\n}")
  }

  test("Enter between a bracket pair puts the closing bracket on its own line") {
    assertEquals(enter("    void f() {|}"), "    void f() {\n        |\n    }")
    assertEquals(enter("    draw(|)"), "    draw(\n        |\n    )")
  }

  test("a blank line keeps no trailing indentation") {
    assertEquals(enter("    a();\n    |"), "    a();\n\n    |")
  }

  test("Python indents after a colon and dedents after return") {
    assertEquals(enter("for i in range(4):|", python = true), "for i in range(4):\n    |")
    assertEquals(enter("def f():\n    return 1|", python = true), "def f():\n    return 1\n|")
    assertEquals(enter("    x = {|}", python = true), "    x = {\n        |\n    }")
  }

  test("a closing brace on a blank line takes its block's indentation") {
    assertEquals(CodeIndentation.closingBrace("class Main {\n    void f() {\n        a();\n        ", unit), Some("    "))
    assertEquals(CodeIndentation.closingBrace("void f() {\n    if (x) {\n    }\n    ", unit), Some(""))
    assertEquals(CodeIndentation.closingBrace("        ", unit), Some("    "))
    assertEquals(CodeIndentation.closingBrace("    a();", unit), None)
  }
}
