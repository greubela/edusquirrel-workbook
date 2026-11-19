package it.evadid.vm.code.errors

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}

import munit.FunSuite
import it.evadid.core.datastructures.language.AppLanguage.Cpp
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateRaw}

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
    val diagnostic = intercept[ProgrammingConversionException](ProgrammingStateRaw(Java,
      "public class Drawing {\n  public static void main(String[] args) {\n    Turtle.forward(;\n  }\n}\n").toPython)
    assertEquals(diagnostic.error.kind, ProgrammingConversionKind.Unparsable)
    assert(diagnostic.error.line.exists(_ >= 3))
  }

  test("unknown conversion targets return structured diagnostics") {
    // Routing for Snap / Python / Java only; see ProgrammingLanguageConversionSpec.
    assert(ProgrammingStateRaw(Python, "forward(1)").convertToLanguage(Cpp).conversionInfo.nonEmpty)
  }

  test("Snap has no comment statements, so comments are dropped with a notice rather than failing") {
    val result = ProgrammingStateRaw(Python, "# keep\nforward(1)").convertToLanguage(SnapLanguage)
    assert(result.finalState.nonEmpty, result.conversionInfo)
    assert(result.conversionInfo.exists(_.warningMsg.contains("comments")), result.conversionInfo)
    assert(!result.hasLostFunctionality)
  }
}
