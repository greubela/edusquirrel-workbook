package it.evadid.homepage.webElements.editor.code

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.workbook.elements.interactionElements.programming.*
import munit.FunSuite

class EvaEditorSpec extends FunSuite {
  test("state representation chooses its matching initial tab") {
    assertEquals(EvaEditor.tabFor(ProgrammingStatePythonString("pass")), EvaEditor.Tab.Python)
    assertEquals(EvaEditor.tabFor(ProgrammingStateJavaString("class A {}")), EvaEditor.Tab.Java)
    assertEquals(EvaEditor.tabFor(ProgrammingExerciseState.mini), EvaEditor.Tab.Snap)
  }

  test("ProgrammingState converts itself at the Snap editor boundary") {
    val snap = ProgrammingStatePythonString("forward(10)").toSnapXml
    assert(snap.snapXml.contains("<project"), clue = snap.snapXml.take(120))
    assert(snap.snapXml.contains("forward"), clue = snap.snapXml)
  }

  test("CodeMirror maps Java and Python independently") {
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Java), "java")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Python), "python")
  }
}
