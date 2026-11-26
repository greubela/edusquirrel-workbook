package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.code.errors.BeSourcePlaceholder
import it.evadid.vm.parsing.source.{SourceIdentity, SourceParsing}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

class BoundSourceOriginsSpec extends FunSuite {
  test("identical Java placeholders bind to separate syntax occurrences") {
    val text = "class Main { static void main(String[] args) {\n Turtle.forward(Math.ceil(16));\n Turtle.forward(Math.ceil(16));\n} }"
    val parsed = SourceParsing.parse(text, SourceLanguage.Java).parsed().toOption.get
    val placeholders = parsed.expression.expression.subtree.collect { case value: BeSourcePlaceholder => value }
    val spans = placeholders.flatMap(value => parsed.sourceOrigins.get(SourceIdentity[it.evadid.vm.code.abstractions.BeExpression](value)))
    assertEquals(spans.map(_.startLine).distinct.sorted, Seq(2, 3))
  }

  test("a hoisted Python class reports its own repeated rejection at the original location") {
    val text = "values = \"abc\"\nx = values[0]\nclass Box:\n    def f(self):\n        x = values[0]\n"
    val state = ProgrammingStateRaw(Python, text)
    val failure = state.convertToLanguage(Java).conversionInfo.flatMap(_.sourceError).head
    assertEquals(failure.line, Some(5))
    assertEquals(failure.column, Some(13))
    assertEquals(failure.span.map(_.text(text)), Some("values[0]"))
  }

  test("nested class hoisting maps tabs and CRLF back to the original body") {
    val text = "def outer():\r\n\tclass Box:\r\n\t\tdef f(self):\r\n\t\t\treturn \"abc\"[0]\r\n"
    val failure = ProgrammingStateRaw(Python, text).convertToLanguage(Java).conversionInfo.flatMap(_.sourceError).head
    assertEquals(failure.line, Some(4))
    assertEquals(failure.span.map(_.text(text)), Some("\"abc\"[0]"))
  }

}
