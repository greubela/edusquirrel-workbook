package it.evadid.homepage.webElements.editor.code

import it.evadid.core.datastructures.language.AppLanguage
import com.raquo.airstream.state.Var
import it.evadid.workbook.elements.interactionElements.programming.*
import munit.FunSuite

class EvaEditorSpec extends FunSuite {
  test("language tabs retain their matching source representation") {
    val pythonState = ProgrammingStatePythonString("pass")
    val javaState = ProgrammingStateJavaString("class A {}")
    val python = EvaEditor.tabFor(EvaEditorConfig.Default, AppLanguage.Python, Var[ProgrammingState](pythonState), _ => ())
    val java = EvaEditor.tabFor(EvaEditorConfig.Default, AppLanguage.Java, Var[ProgrammingState](javaState), _ => ())
    assertEquals(python.associatedLanguage, AppLanguage.Python)
    assertEquals(java.associatedLanguage, AppLanguage.Java)
    assertEquals(python.associatedVar.now(): ProgrammingState, pythonState: ProgrammingState)
    assertEquals(java.associatedVar.now(): ProgrammingState, javaState: ProgrammingState)
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
