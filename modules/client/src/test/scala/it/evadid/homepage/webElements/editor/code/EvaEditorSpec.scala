package it.evadid.homepage.webElements.editor.code

import it.evadid.core.datastructures.language.AppLanguage
import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig
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

  private def editorFor(source: ProgrammingState): EvaEditor =
    new EvaEditor(Var(source), SnapCodeEditorConfig.Testing)

  test("unfinished sources open without deriving other representations") {
    val sources: List[ProgrammingState] = List(
      ProgrammingExerciseState.empty,
      ProgrammingStateJavaString("public class Drawing {\n  public static void main(String[] args) {\n"),
      ProgrammingStatePythonString("def draw(\n"),
      ProgrammingStateSnapXml("<project><scripts><script><block s=\"wait\"><l>1</l></block></script></scripts></project>")
    )
    sources.foreach { source =>
      val editor = editorFor(source)
      assertEquals(editor.currentState(), source)
      assertEquals(editor.activeTab.now(), EvaEditor.tabFor(source))
    }
  }

  test("selecting the current tab preserves an unfinished source exactly") {
    val source = ProgrammingStateJavaString("\n public class Drawing {\n\t\n")
    val editor = editorFor(source)
    editor.select(EvaEditor.Tab.Java)
    assertEquals(editor.currentState(), source)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
    assertEquals(editor.conversionError.now(), None)
  }

  test("failed switches retain the original tab and source") {
    val source = ProgrammingStateJavaString("public class Drawing {\n")
    val editor = editorFor(source)
    List(EvaEditor.Tab.Python, EvaEditor.Tab.Snap).foreach { tab =>
      editor.select(tab)
      assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
      assertEquals(editor.currentState(), source)
      assert(editor.conversionError.now().nonEmpty)
    }
  }

  test("viewing converted source does not publish or normalize the stored draft") {
    val source = ProgrammingStatePythonString("\nforward(12)\n\n  ")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditor(Var[ProgrammingState](source), SnapCodeEditorConfig.Testing,
      next => published = published :+ next)
    List(EvaEditor.Tab.Java, EvaEditor.Tab.Python).foreach { tab =>
      editor.select(tab)
      assertEquals(editor.activeTab.now(), tab)
      assertEquals(editor.currentState(), source)
      assertEquals(editor.conversionError.now(), None)
    }
    assertEquals(published, Nil)
  }

  test("late edits from an inactive editor cannot replace the current draft") {
    val source = ProgrammingStateJavaString("class Drawing {\n")
    val editor = editorFor(source)
    editor.publish(EvaEditor.Tab.Snap, ProgrammingExerciseState.mini)
    editor.publish(EvaEditor.Tab.Python, ProgrammingStatePythonString("forward(99)"))
    assertEquals(editor.currentState(), source)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
  }

  test("Snap edits retain additional floating objects") {
    val source = ProgrammingStateSnapXMLWithAdditionalFloatingObjects(
      "<project/>", List("\nwatcher\n", "comment"))
    val editor = editorFor(source)
    editor.publish(EvaEditor.Tab.Snap, ProgrammingStateSnapXml("<project name=\"changed\"/>"))
    assertEquals(editor.currentState(), source.copy(snapXml = "<project name=\"changed\"/>"))
  }

  test("Java run cannot return commands from a previous Snap view") {
    val editor = editorFor(ProgrammingStateJavaString("forward(12);"))
    intercept[IllegalStateException](throw editor.getCurrentTurtleCommands().value.get.failed.get)
  }

  test("run uses a source restored while the editor is closed") {
    val editor = editorFor(ProgrammingStatePythonString("forward(12)"))
    editor.state.set(ProgrammingStateJavaString("public class Drawing {}\n"))
    intercept[IllegalStateException](throw editor.getCurrentTurtleCommands().value.get.failed.get)
    assertEquals(editor.currentState(), editor.state.now())
  }
}
