package it.evadid.homepage.webElements.editor.code

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorPlain, EvaProgrammingTab}
import munit.FunSuite

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class EvaEditorSpec extends FunSuite {
  private val testingConfig = EvaEditorConfig(snapConfig = SnapCodeEditorConfig.Testing)

  private def editorFor(source: ProgrammingState, config: EvaEditorConfig = testingConfig): EvaEditor =
    new EvaEditorPlain(Var[ProgrammingState](source), config)

  test("tab synchronization compares the converted state and ignores identical restores") {
    val snap = ProgrammingStateSnapXml.mini
    val tab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.Python, Var[ProgrammingState](snap), _ => ())
    val owner = new ManualOwner
    var changes = 0
    try {
      tab.associatedVar.signal.changes.foreach(_ => changes += 1)(using owner)
      tab.setStateTo(snap)
      tab.setStateTo(snap)
      assertEquals(changes, 0)
      val changed = ProgrammingStatePythonString("forward(25)")
      tab.setStateTo(changed)
      tab.setStateTo(changed)
      assertEquals(changes, 1)
    } finally owner.killSubscriptions()
  }

  test("Snap tab forwards edits to the parent editor") {
    val initial = ProgrammingStateSnapXml.mini
    var published: Option[ProgrammingState] = None
    val tab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.SnapLanguage, Var[ProgrammingState](initial), next => published = Some(next))
    val next = ProgrammingStateSnapXml.empty
    tab.editorElement.asInstanceOf[SnapEditor.SnapCodeEditor].onStateEdited(next)
    assertEquals(published, Some(next))
  }

  test("configured language creates a matching tab with the current editor state") {
    val python = ProgrammingStatePythonString("forward(10)")
    val pythonTab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.Python, Var[ProgrammingState](python), _ => ())
    assertEquals(pythonTab.associatedLanguage, AppLanguage.Python)
    assertEquals[ProgrammingState, ProgrammingState](pythonTab.associatedVar.now(), python)
    assert(pythonTab.editorElement.isInstanceOf[CodeMirrorEditor])

    val java = ProgrammingStateJavaString("class A {}")
    val javaTab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.Java, Var[ProgrammingState](java), _ => ())
    assertEquals(javaTab.associatedLanguage, AppLanguage.Java)
    assertEquals[ProgrammingState, ProgrammingState](javaTab.associatedVar.now(), java)
    assert(javaTab.editorElement.isInstanceOf[JavaFunctionBasedEditor])

    val snap = ProgrammingStateSnapXml.mini
    val snapTab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.SnapLanguage, Var[ProgrammingState](snap), _ => ())
    assertEquals(snapTab.associatedLanguage, AppLanguage.SnapLanguage)
    assertEquals[ProgrammingState, ProgrammingState](snapTab.associatedVar.now(), snap)
    assert(snapTab.editorElement.isInstanceOf[SnapEditor.SnapCodeEditor])
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

  test("state representation chooses its matching initial tab") {
    assertEquals(EvaEditor.tabFor(ProgrammingStatePythonString("pass")), EvaEditor.Tab.Python)
    assertEquals(EvaEditor.tabFor(ProgrammingStateJavaString("class A {}")), EvaEditor.Tab.Java)
    assertEquals(EvaEditor.tabFor(ProgrammingStateSnapXml.mini), EvaEditor.Tab.Snap)
  }

  test("unfinished sources and full Java classes open without deriving other representations") {
    val sources: List[ProgrammingState] = List(
      ProgrammingStateSnapXml.empty,
      ProgrammingStateJavaString("\n\tpublic class Drawing {\r\n  public static void main(String[] args) {\r\n"),
      ProgrammingStateJavaString("\npublic class Drawing {\r\n  public static void main(String[] args) {}\r\n}\r\n\t "),
      ProgrammingStatePythonString("\tdef draw(\r\n"),
      ProgrammingStateSnapXml("<project><scripts><script><block s=\"wait\"><l>1</l></block></script></scripts></project>")
    )
    sources.foreach { source =>
      var published = List.empty[ProgrammingState]
      val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
        next => published = published :+ next)
      assertEquals(editor.currentState(), source)
      assertEquals(editor.activeTab.now(), EvaEditor.tabFor(source))
      assertEquals(editor.conversionError.now(), None)
      assertEquals(published, Nil)
    }
  }

  test("matching unfinished Java and Python sources open with only their configured language") {
    val sources: List[(AppLanguage.ProgrammingLanguage, ProgrammingState, EvaEditor.Tab)] = List(
      (AppLanguage.Java, ProgrammingStateJavaString("\npublic class Drawing {\r\n\t"), EvaEditor.Tab.Java),
      (AppLanguage.Python, ProgrammingStatePythonString("\tdef draw(\r\n"), EvaEditor.Tab.Python)
    )
    sources.foreach { (language, source, tab) =>
      val editor = editorFor(source, testingConfig.copy(enabledLanguages = List(language)))
      assertEquals(editor.currentState(), source)
      assertEquals(editor.activeTab.now(), tab)
      assertEquals(editor.conversionError.now(), None)
    }
  }

  test("a configuration without the source language retains the original draft") {
    val source = ProgrammingStateJavaString("\npublic class Drawing {\r\n\t")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source),
      testingConfig.copy(enabledLanguages = List(AppLanguage.Python)),
      next => published = published :+ next)
    assertEquals(editor.currentState(), source)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Python)
    assertEquals(published, Nil)
  }

  test("text navigation before mounting Snap does not require its browser runtime") {
    val source = ProgrammingStatePythonString("forward(12)").toSnapXml
    val editor = editorFor(source)
    editor.select(EvaEditor.Tab.Python)
    assertEquals(editor.currentState(), source)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Python)
    assertEquals(editor.conversionError.now(), None)
  }

  test("disabled language selections and edits are ignored") {
    val source = ProgrammingStateJavaString("\npublic class Drawing {\r\n\t")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source),
      testingConfig.copy(enabledLanguages = List(AppLanguage.Java)),
      next => published = published :+ next)
    val disabledEdits: List[(EvaEditor.Tab, ProgrammingState)] = List(
      EvaEditor.Tab.Python -> ProgrammingStatePythonString("forward(99)"),
      EvaEditor.Tab.Snap -> ProgrammingStateSnapXml.empty
    )
    disabledEdits.foreach { (tab, edited) =>
      editor.select(tab)
      editor.publish(tab, edited)
      assertEquals(editor.currentState(), source)
      assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
      assertEquals(editor.conversionError.now(), None)
    }
    assertEquals(published, Nil)
  }

  test("selecting the current tab preserves an unfinished source exactly") {
    val source = ProgrammingStateJavaString("\n public class Drawing {\r\n\t\n")
    val editor = editorFor(source)
    editor.select(EvaEditor.Tab.Java)
    assertEquals(editor.currentState(), source)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
    assertEquals(editor.conversionError.now(), None)
  }

  test("failed switches retain raw unsupported Java classes and unfinished drafts") {
    val sources = List(
      ProgrammingStateJavaString("\npublic class Drawing {\r\n\tpublic static void main(String[] args) { double side = 25; }\r\n}\r\n\t "),
      ProgrammingStateJavaString("\npublic class Drawing {\r\n\t")
    )
    sources.foreach { source =>
      var published = List.empty[ProgrammingState]
      val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
        next => published = published :+ next)
      List(EvaEditor.Tab.Python, EvaEditor.Tab.Snap).foreach { tab =>
        editor.select(tab)
        assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
        assertEquals(editor.currentState(), source)
        assert(editor.conversionError.now().nonEmpty)
      }
      assertEquals(published, Nil)
    }
  }

  test("viewing converted source does not publish or normalize the stored draft") {
    val source = ProgrammingStatePythonString("\nforward(12)\n\n  ")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next)
    List(EvaEditor.Tab.Java, EvaEditor.Tab.Python).foreach { tab =>
      editor.select(tab)
      assertEquals(editor.activeTab.now(), tab)
      assertEquals(editor.currentState(), source)
      assertEquals(editor.conversionError.now(), None)
    }
    assertEquals(published, Nil)
  }

  test("only an active editor edit updates the draft and publishes once") {
    val source = ProgrammingStateJavaString("\nclass Drawing {\r\n\t")
    val edited = ProgrammingStateJavaString("\nclass Drawing {\r\n\tpublic void draw(\r\n")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next)
    editor.publish(EvaEditor.Tab.Snap, ProgrammingStateSnapXml.mini)
    editor.publish(EvaEditor.Tab.Python, ProgrammingStatePythonString("forward(99)"))
    assertEquals(editor.currentState(), source)
    assertEquals(published, Nil)

    editor.publish(EvaEditor.Tab.Java, edited)
    assertEquals(editor.currentState(), edited)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
    assertEquals(published, List(edited))
    editor.publish(EvaEditor.Tab.Java, edited)
    assertEquals(published, List(edited))

    editor.publish(EvaEditor.Tab.Python, ProgrammingStatePythonString("forward(42)"))
    editor.publish(EvaEditor.Tab.Snap, ProgrammingStateSnapXml.empty)
    assertEquals(editor.currentState(), edited)
    assertEquals(published, List(edited))
  }

  test("Snap edits retain additional floating objects and publish the retained state") {
    val source = ProgrammingStateSnapXMLWithAdditionalFloatingObjects(
      "<project/>", List("\nwatcher\n", "comment\r\n\t "))
    val edited = source.copy(snapXml = "<project name=\"changed\"/>")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditorPlain(Var[ProgrammingState](source), testingConfig,
      next => published = published :+ next)
    editor.publish(EvaEditor.Tab.Snap, ProgrammingStateSnapXml(edited.snapXml))
    assertEquals(editor.currentState(), edited)
    assertEquals(published, List(edited))
  }

  test("legacy Java snippets remain convertible without running a full Java class") {
    val source = ProgrammingStateJavaString("forward(12);")
    val editor = editorFor(source)
    assertEquals(source.toBeExpressionState.deriveTurtleCommands, List(TurtleCommand[Double]("forward", List(12.0))))
    assertEquals(editor.currentState(), source)
  }

  test("running an unsupported Java class fails without replacing its source") {
    val source = ProgrammingStateJavaString("\npublic class Drawing {\r\n\tpublic static void main(String[] args) { double side = 25; }\r\n}\r\n\t ")
    val editor = editorFor(source)
    editor.getCurrentTurtleCommands().failed.map { _ =>
      assertEquals(editor.currentState(), source)
    }
  }

  test("run uses a source restored while the editor is closed") {
    val editor = editorFor(ProgrammingStatePythonString("forward(12)"))
    val restored = ProgrammingStateJavaString("\npublic class Drawing {}\r\n\t ")
    editor.state.set(restored)
    editor.getCurrentTurtleCommands().failed.map { _ =>
      assertEquals(editor.currentState(), restored)
    }
  }
}
