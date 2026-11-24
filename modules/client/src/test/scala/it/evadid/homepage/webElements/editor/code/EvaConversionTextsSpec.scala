package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaConversionFailure, EvaConversionTexts, EvaEditorLabels}
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

  test("editors mark a failed conversion's position with its localized explanation") {
    val owner = ManualOwner()
    val failure = Var(Option.empty[EvaConversionFailure])
    val labels = EvaEditorLabels.fromContent(id => Val(id.entryKey match {
      case "conversionunsupported" => "Nicht übertragbar"
      case "conversionline" => "Zeile {line}"
      case other => other
    }))
    val diagnostics = EvaTestContext(labels, failure.signal).conversionDiagnostics.observe(using owner)
    assertEquals(diagnostics.now(), Nil)
    failure.set(Some(EvaConversionFailure(ProgrammingConversionError(ProgrammingConversionKind.UnsupportedConstruct, Some(2), Some(5)), "while-else")))
    assertEquals(diagnostics.now().map(d => (d.line, d.fromCh, d.toCh, d.severity, d.message)),
      Seq((2, Some(4), Some(5), "error", "Nicht übertragbar (Zeile 2): while-else")))
    owner.killSubscriptions()
  }
}
