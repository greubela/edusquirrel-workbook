package it.evadid.vm.parsing.source

import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import munit.FunSuite

/** Step 1 for Python: logical lines, classes and functions of any text. */
class PythonStructuredSpec extends FunSuite {
  private def structured(source: String): PythonSourceStructured = SourceParsing.parse(source, SourceLanguage.Python).structured

  test("logical lines join bracketed continuations, skip comments and record indentation, rows and block openers") {
    val result = structured("x = (1,\n     2)  # pair\n\n# only comment\n  y = 3\nif x:\n")
    assertEquals(result.codeLines.map(line => (line.row, line.indent, line.code, line.opensBlock)),
      Vector((1, 0, "x = (1,\n     2)", false), (5, 2, "y = 3", false), (6, 0, "if x:", true)))
    assertEquals(result.lines.map(_.row), Vector(1, 3, 4, 5, 6, 7))
  }

  test("classes keep bases, header, dedented body and code, indentation and span") {
    val source = "class A(Base):\n    x = 1\n\n    class B:\n        pass\nprint(1)\n"
    val Vector(a, b) = structured(source).classes: @unchecked
    assertEquals((a.name, a.bases, a.header, a.indent), ("A", "Base", "class A(Base):", 0))
    assert(a.body.startsWith("x = 1"), a.body)
    assert(!a.code.contains("print"), a.code)
    assertEquals((a.span.startLine, a.span.endLine), (1, 5))
    assertEquals((b.name, b.indent, b.body), ("B", 4, "pass"))
  }

  test("functions keep decorators, parameters, return type, owner, header line, body and span") {
    val source = "class A:\n    @staticmethod\n    @cached(3)\n    async def run(self, count: int = 3, *rest, **named) -> bool:\n" +
      "        return count > 0\n\ndef helper(x):\n    pass\n"
    val Vector(run, helper) = structured(source).functions: @unchecked
    assertEquals((run.name, run.owner, run.decorators, run.returnType, run.indent), ("run", Some("A"), Vector("@staticmethod", "@cached(3)"), Some("bool"), 4))
    assertEquals(run.parameters.map(parameter => (parameter.name, parameter.annotation, parameter.default)),
      Vector(("self", None, None), ("count", Some("int"), Some("3")), ("rest", None, None), ("named", None, None)))
    assertEquals(run.parameterList, "self, count: int = 3, *rest, **named")
    assertEquals((run.headerLine, run.span.startLine, run.span.endLine), (4, 2, 6))
    assertEquals(run.body, "return count > 0")
    assert(run.code.startsWith("@staticmethod\n@cached(3)\nasync def run"), run.code)
    assertEquals((helper.owner, helper.parameters.map(_.name), helper.body, helper.headerLine), (None, Vector("x"), "pass", 7))
  }

  test("verbatim library definitions are blanked in the masked source") {
    val source = "import turtle\nforward(10)\n"
    val result = structured(source)
    assertEquals(result.masked.length, source.length)
    assertEquals(result.masked.linesIterator.toVector.map(_.trim), Vector("", "forward(10)"))
  }

  test("balance covers brackets and every string form") {
    assert(structured("x = ['#', \"\"\"\n)\n\"\"\"]  # )").balanced)
    assert(!structured("x = '''open").balanced)
    assert(!structured("f(").balanced)
  }

  test("any text is accepted: unfinished and invalid code yields what can be recognised") {
    List("", "def", "def f(", "class :", "if x:\n", "   \t\n", "x = '''", ")))").foreach { source =>
      val result = structured(source)
      assertEquals(result.masked.length, source.length)
    }
    assertEquals(structured("def f(a, b").functions.map(function => (function.name, function.parameters.map(_.name))), Vector(("f", Vector("a", "b"))))
  }
}
