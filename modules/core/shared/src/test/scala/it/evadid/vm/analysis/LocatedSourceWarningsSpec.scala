package it.evadid.vm.analysis

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.parsing.source.{SourceParsing, SourceLines}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import munit.FunSuite

class LocatedSourceWarningsSpec extends FunSuite {
  test("identical unknown calls in nested Python blocks have distinct original positions") {
    val text = "\r\n\tdef f():\r\n\t\tmissing(1)\r\n\t\tmissing(1)\r\n"
    val source = SourceParsing.parse(text, SourceLanguage.Python)
    val warnings = PythonSourceWarnings.of(source, Set(Seq("missing")))
    assertEquals(warnings.map(w => (w.startLine, w.startColumn, w.endColumn)), Seq((3, 3, 10), (4, 3, 10)))
  }

  test("unreadable Python and adapter-unsupported expressions are marked at their occurrences") {
    val bad = ProgramAnalysis.of(ProgrammingStateRaw(Python, "def f():\n    broken =\n    broken =\n"))
    assertEquals(bad.warnings.filter(_.kind == SourceWarning.Kind.Unreadable).map(_.startLine), Seq(2, 3))
    val unsupported = ProgramAnalysis.of(ProgrammingStateRaw(Python, "values = \"abc\"\na = values[0]\nb = values[0]\n"))
    assertEquals(unsupported.warnings.filter(_.kind == SourceWarning.Kind.Unsupported).map(_.startLine), Seq(2, 3))
  }

  test("Java adapter rejections carry the exact class span, including repeated class headers") {
    val source = "class Good {}\nclass Bad extends Good { }\n"
    val result = ProgramAnalysis.of(ProgrammingStateRaw(Java, source))
    val warning = result.warnings.find(_.kind == SourceWarning.Kind.Unsupported).get
    assertEquals((warning.startLine, warning.startColumn), (2, 1))
    assertEquals(warning.endColumn, "class Bad extends Good { }".length + 1)
  }

  test("Java adapter exceptions retain locations rather than guessing source fragments") {
    val text = "class Box {\n    static int size;\n}\n"
    val source = SourceParsing.parse(text, SourceLanguage.Java)
    val error = source.parsed().swap.toOption.get
    assert(error.error.span.nonEmpty)
    assertEquals(error.error.line, Some(1))
  }
}
