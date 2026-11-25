package it.evadid.vm.parsing.source

import it.evadid.vm.code.errors.ProgrammingConversionKind
import it.evadid.vm.conversion.parsed.ParsedStatement
import it.evadid.vm.parsing.java.clean.model.JavaAST.JavaClassDef
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import munit.FunSuite

/** The single parsing entry and its later steps: syntax (2) and the parsed program (3). */
class SourceParsingSpec extends FunSuite {
  test("one source is parsed once: the entry returns the same steps for the same text and language") {
    val source = "class Main { public static void main(String[] args) { } }"
    val first = SourceParsing.parse(source, SourceLanguage.Java)
    assert(first eq SourceParsing.parse(source, SourceLanguage.Java))
    assert(first.structured eq SourceParsing.parse(source, SourceLanguage.Java).structured)
    assert(first.parsed() eq first.parsed())
  }

  test("Java syntax is the located tree of the masked source, or the position where the grammar stopped") {
    val syntax = SourceParsing.parse("class A {\n  void f() { g(); }\n}", SourceLanguage.Java).syntax.toOption.get
    assert(syntax.program.statements.head.statement.isInstanceOf[JavaClassDef])
    assertEquals(syntax.functions.map(function => (function.structured.name, syntax.sourceMap.position(function.node.range.start).map(_.line))),
      Vector(("f", Some(2))))
    // The grammar keeps unfinished code as unreadable statements; only text it cannot place at all stops it.
    assert(SourceParsing.parse("class A {\n  void f( {\n", SourceLanguage.Java).syntax.isRight)
    val failure = SourceParsing.parse("class A {\n  int x;\n} }", SourceLanguage.Java).syntax.swap.toOption.get
    assertEquals((failure.line, failure.column, failure.offset), (3, 3, 21))
  }

  test("Python syntax keeps lines it cannot read as unparsable statements") {
    import it.evadid.vm.parsing.python.clean.model.PyAST.PyUnparsableStatement
    val syntax = SourceParsing.parse("x = 1\nif x\n    y = 2\n", SourceLanguage.Python).syntax.toOption.get
    assertEquals(syntax.program.statements.map(_.statement).collect { case PyUnparsableStatement(text) => text.trim }, List("if x", "y = 2"))
  }

  test("parsed Java pairs each structured function with its syntax node and definition") {
    val parsed = SourceParsing.parse("class Box {\n  int size(int a) { return a; }\n}\nclass Main {\n  public static void main(String[] args) { }\n}",
      SourceLanguage.Java).parsed().toOption.get
    // The entry point's body is the program's main sequence, not a function definition.
    assertEquals(parsed.functions.map(function => (function.structured.className, function.structured.name, function.definition.isDefined)),
      Vector(("Box", "size", true), ("Main", "main", false)))
    assertEquals(parsed.functions.head.definition.map(_.inputs.size), Some(1))
    assert(parsed.neutral.statements.nonEmpty)
    val unreadable = SourceParsing.parse("class A { } }", SourceLanguage.Java).parsed()
    assertEquals(unreadable.swap.toOption.map(_.error.kind), Some(ProgrammingConversionKind.Unparsable))
  }

  test("parsed Python keeps the neutral program and pairs functions with their definitions") {
    val parsed = SourceParsing.parse("def twice(x):\n    return 2 * x\n\nprint(twice(2))\n", SourceLanguage.Python).parsed()
    assertEquals(parsed.functions.map(function => (function.structured.name, function.definition.map(_.inputs.size))), Vector(("twice", Some(1))))
    assert(SourceParsing.parse("def twice(x):\n    return 2 * x\n", SourceLanguage.Python).neutral.statements.exists(_.isInstanceOf[ParsedStatement.FunctionDef]))
    val unreadable = SourceParsing.parse("def (:\n", SourceLanguage.Python).parsed()
    assert(unreadable.neutral.statements.exists { case ParsedStatement.Unsupported(_, _, true) => true; case _ => false })
  }

  test("text utilities and line positions") {
    assertEquals(SourceText.remove("0123456789", Seq(4 -> 6, 2 -> 8, 9 -> 10)), "01" + "8")
    assertEquals(SourceText.dedent("    a\r\n      b\n\n    c  "), "a\n  b\n\nc")
    assertEquals(SourceText.indent("a\nb", 2), "  a\n  b")
    val lines = SourceLines("ab\ncd\r\nef\rg")
    assertEquals((0 to 10).map(lines.line).toVector, Vector(1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 4))
    assertEquals(lines.column(4), 2)
    assertEquals(lines.offset(3, 2), 8)
    assertEquals(lines.span(3, 5), SourceSpan(3, 5, 2, 2))
  }
}
