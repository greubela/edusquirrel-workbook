package it.evadid.vm.conversion.structured

import it.evadid.vm.conversion.structured.SourceStructure.*
import it.evadid.vm.naming.BeEntityName
import munit.FunSuite

class SourceStructureSpec extends FunSuite {
  test("balance checks brackets, strings and comments in both languages") {
    assert(balanced("class A { void f() { g(\"}\"); /* ) */ } }"))
    assert(!balanced("class A { void f() {"))
    assert(!balanced("f(\"open)"))
    assert(!balanced("/* open"))
    assert(!balanced("f())"))
    assert(balanced("x = ['#', \"\"\"\n)\n\"\"\"]  # )", python = true))
    assert(!balanced("x = '''open", python = true))
  }

  test("masking blanks comments and string contents but keeps offsets and line breaks") {
    val source = "a = \"x\" // c\nb"
    val masked = mask(source, python = false)
    assertEquals(masked.length, source.length)
    assertEquals(masked, "a =" + " " * 9 + "\nb")
    assertEquals(mask("x = 'a'  # c\ny", python = true), "x =" + " " * 9 + "\ny")
  }

  test("remove drops nested ranges once, dedent and indent normalise blocks") {
    assertEquals(remove("0123456789", Seq(4 -> 6, 2 -> 8, 9 -> 10)), "01" + "8")
    assertEquals(dedent("    a\r\n      b\n\n    c  "), "a\n  b\n\nc")
    assertEquals(dedent(""), "")
    assertEquals(indent("a\nb", 2), "  a\n  b")
  }

  test("Python code lines join bracketed continuation lines and skip comments") {
    val lines = pythonCodeLines("x = (1,\n     2)  # pair\n\n# only comment\n  y = 3\n")
    assertEquals(lines.map(line => (line.row, line.indent, line.code)), Vector((1, 0, "x = (1,\n     2)"), (5, 2, "y = 3")))
  }

  test("Python blocks of a kind keep header, dedented body and indentation") {
    val source = "class A:\n    x = 1\n\n    async def run(self):\n        return 2\nprint(1)\n"
    val Vector(block) = pythonBlocks(source, "class"): @unchecked
    assertEquals((block.name, block.header, block.indent), ("A", "class A:", 0))
    assert(block.body.startsWith("x = 1"), block.body)
    assert(!block.source.contains("print"), block.source)
    val Vector(method) = pythonBlocks(source, "def"): @unchecked
    assertEquals((method.name, method.indent, method.body), ("run", 4, "return 2"))
  }

  test("Python class extraction hoists nested classes and repairs emptied blocks") {
    val warnings = Vector.newBuilder[String]
    val parts = python("class A:\n    class B:\n        pass\nclass A:\n    pass\nif x:\n    class C:\n        pass\n", warnings += _)
    assertEquals(parts.definedClasses.keySet, Set("A", "B", "C").map(BeEntityName.fromCodeString))
    assertEquals(parts.definedClasses(BeEntityName.fromCodeString("A")), "class A:\n    pass")
    assertEquals(parts.remaining, "if x:\n    pass")
    assertEquals(warnings.result().size, 1)
  }

  test("Python blocks without a body stay as typed; only blocks emptied by hoisting receive pass") {
    assertEquals(python("def main():\n    if x > 1:\n        \n").remaining, "def main():\n    if x > 1:")
    assertEquals(python("if x:\nclass A:\n    pass\n").remaining, "if x:")
    assertEquals(python("def f():\n    y = 1\n    class B:\n        pass\n").remaining, "def f():\n    y = 1")
  }

  test("the parser reads Python headers without a body as empty blocks, keeping line numbers where possible") {
    assertEquals(withEmptyBodies("def main():\n    if x:  # check\n        \nmain()"), "def main():\n    if x:  # check\n        pass\nmain()")
    assertEquals(withEmptyBodies("def draw(size):"), "def draw(size):\n    pass")
    assertEquals(withEmptyBodies("if x:\n    y = 1"), "if x:\n    y = 1")
  }

  test("Java classes are found with their modifiers, nested classes and unfinished bodies") {
    val source = "public final class Outer { static class Inner { } }\nclass Open { void f() {"
    val blocks = javaClasses(source)
    assertEquals(blocks.map(_.name), Vector("Outer", "Inner", "Open"))
    assertEquals(blocks.head.header, "public final class Outer ")
    assertEquals(blocks(1).body, " ")
    assertEquals(blocks(2).end, source.length)
    assertEquals(javaClasses("class Forward;").map(b => (b.name, b.body)), Vector(("Forward", "")))
  }

  test("Java extraction keeps imports with the first class and leaves other code remaining") {
    val parts = java("package demo;\nimport java.util.List;\nclass A { }\nint x = 1;")
    assertEquals(parts.remaining, "int x = 1;")
    val first = parts.definedClasses(BeEntityName.fromCodeString("A"))
    assert(first.startsWith("package demo;\nimport java.util.List;"), first)
    val onlyImports = java("import x.Y;\n// trailing")
    assertEquals(onlyImports.remaining, "")
    assert(onlyImports.definedClasses(BeEntityName.fromCodeString("Main")).contains("import x.Y;"))
    assertEquals(java("class A { }").definedClasses.values.toSeq, Seq("class A { }"))
  }

  test("Java members split on semicolons and bodies but keep initializer braces") {
    val members = javaMembers("class A { int[] xs = {1, 2}; // c\n void f() { g(); } static int y; int z }")
    assertEquals(members, Vector("int[] xs = {1, 2};", "void f() { g(); }", "static int y;", "int z"))
    assertEquals(javaMembers("int x;"), Vector.empty)
  }

  test("Java class spans locate closed method bodies and their signatures") {
    val source = "class A {\n  public static List<String> names(int a, int b) { return null; }\n  int x;\n  A() { }\n  void open() {\n}\n}\nclass B;"
    val Vector(span) = javaClassSpans(source): @unchecked
    assertEquals(span.name, "A")
    assertEquals(source.charAt(span.bodyOpen), '{')
    assertEquals(source.charAt(span.bodyClose), '}')
    val Vector(names, open) = span.methods: @unchecked
    assertEquals((names.name, names.returnType, names.parameters), ("names", "List<String>", "int a, int b"))
    assertEquals(source.substring(names.start, names.end), "public static List<String> names(int a, int b) { return null; }")
    assertEquals(open.name, "open")
  }

  test("member helpers classify scope, method names, bodies and declarations") {
    assertEquals(scope("static int x;"), MemberScope.Static)
    assertEquals(scope("int x;"), MemberScope.Instance)
    assertEquals(methodName("int[] values() { }"), Some("values"))
    assertEquals(methodName("Map<K, V> map() { }"), Some("map"))
    assertEquals(methodName("foo();"), None)
    assertEquals(methodName("int x = f();"), None)
    assertEquals(body("void f() {\n    a();\n    b();\n}"), Some("a();\nb();"))
    assertEquals(body("void f();"), None)
    assertEquals(declaration("private static final java.util.List<String> names = new ArrayList<>();"),
      Some(Declaration("java.util.List<String>", "names", Some("new ArrayList<>()"))))
    assertEquals(declaration("int[] xs;"), Some(Declaration("int[]", "xs", None)))
    assertEquals(declaration("return value;"), None)
    assertEquals(declaration("x = 1;"), None)
    assertEquals(declaration("a.b c;"), Some(Declaration("a.b", "c", None)))
  }
}
