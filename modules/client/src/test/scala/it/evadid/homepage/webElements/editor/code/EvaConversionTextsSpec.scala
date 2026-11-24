package it.evadid.homepage.webElements.editor.code

import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaConversionFailure, EvaConversionTexts}
import it.evadid.vm.code.errors.{ProgrammingConversionError, ProgrammingConversionKind}
import munit.FunSuite

class EvaConversionTextsSpec extends FunSuite {
  private val german = EvaConversionTexts("Nicht lesbar", "Nicht übertragbar", "Zeile {line}", "Mit Änderungen:")

  test("a failure reads as the localized cause, its line and the technical detail") {
    assertEquals(german.describe(EvaConversionFailure(ProgrammingConversionError(ProgrammingConversionKind.Unparsable, Some(3)), "expected ':'")),
      "Nicht lesbar (Zeile 3): expected ':'")
    assertEquals(german.describe(EvaConversionFailure(ProgrammingConversionError(ProgrammingConversionKind.UnsupportedConstruct), "")),
      "Nicht übertragbar")
  }
}
