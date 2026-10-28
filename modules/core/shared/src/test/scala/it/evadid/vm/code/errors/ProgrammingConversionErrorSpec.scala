package it.evadid.vm.code.errors

import munit.FunSuite
import it.evadid.core.datastructures.language.AppLanguage.Cpp
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateJavaString, ProgrammingStatePythonString}

class ProgrammingConversionErrorSpec extends FunSuite {
  test("parser failures and java diagnostics report a 1-based position") {
    assertEquals(ProgrammingConversionLocation.lineColumn("a\nbc", 2), (2, 1))
    assertEquals(ProgrammingConversionLocation.inTrimmed("\nclass X", "class X", 0), (2, 1))
    val java = JavaParser.parse("}").left.getOrElse(fail("expected a java parse failure")).asInstanceOf[ProgrammingConversionException]
    assertEquals(java.error.kind, ProgrammingConversionKind.Unparsable)
    assertEquals(java.error.line, Some(1))
    assert(java.error.column.exists(_ >= 1))
    // The simple Python grammar recovers many broken inputs as statements. A fastparse failure still
    // carries the same 1-based position as the Java parser, computed on the trimmed source.
    assertEquals(ProgrammingConversionLocation.inTrimmed("\n(\n(", "(\n(", 1), (2, 2))
    val diagnostic = intercept[ProgrammingConversionException](ProgrammingStateJavaString(
      "public class Drawing {\n  public static void main(String[] args) {\n    String value = \"hi\";\n  }\n}\n").toPython)
    assertEquals(diagnostic.error.kind, ProgrammingConversionKind.Unparsable)
    assert(diagnostic.error.line.exists(_ >= 3))
  }

  test("unknown toLanguage targets are not ProgrammingConversionException") {
    // Routing for Snap / Python / Java only; see ProgrammingLanguageConversionSpec.
    intercept[UnsupportedOperationException](ProgrammingStatePythonString("forward(1)").toLanguage(Cpp))
  }

  test("a python comment that cannot become blocks is reported as unsupported") {
    val failed = intercept[ProgrammingConversionException](ProgrammingStatePythonString("# keep\nforward(1)").toSnapXml)
    assertEquals(failed.error.kind, ProgrammingConversionKind.UnsupportedConstruct)
  }
}
