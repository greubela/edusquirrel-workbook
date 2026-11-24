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

  test("Python reads the colon before a comment and ignores colons in comments and strings") {
    assertEquals(enter("if x > 3:  # the big case|", python = true), "if x > 3:  # the big case\n    |")
    assertEquals(enter("    # Notes:|", python = true), "    # Notes:\n    |")
    assertEquals(enter("print('a:')|", python = true), "print('a:')\n|")
    assertEquals(enter("    else:|", python = true), "    else:\n        |")
  }

  test("Python dedents after pass, break, continue and raise, but not after a name starting with them") {
    assertEquals(enter("while True:\n    break|", python = true), "while True:\n    break\n|")
    assertEquals(enter("def f():\n    pass|", python = true), "def f():\n    pass\n|")
    assertEquals(enter("def f():\n    passed = 1|", python = true), "def f():\n    passed = 1\n    |")
  }

  test("brackets in strings and comments neither open blocks nor count as unclosed") {
    assertEquals(enter("    print(\"{\");|"), "    print(\"{\");\n    |")
    assertEquals(enter("    a(); // {|"), "    a(); // {\n    |")
    assertEquals(enter("    void f() {|\n    String s = \"}\";"), "    void f() {\n        |\n    }\n    String s = \"}\";")
    assertEquals(CodeIndentation.closingBrace("void f() {\n    s = \"{\";\n    ", unit), Some(""))
  }

  test("a closing brace on a blank line takes its block's indentation") {
    assertEquals(CodeIndentation.closingBrace("class Main {\n    void f() {\n        a();\n        ", unit), Some("    "))
    assertEquals(CodeIndentation.closingBrace("void f() {\n    if (x) {\n    }\n    ", unit), Some(""))
    assertEquals(CodeIndentation.closingBrace("        ", unit), Some("    "))
    assertEquals(CodeIndentation.closingBrace("    a();", unit), None)
  }
}
