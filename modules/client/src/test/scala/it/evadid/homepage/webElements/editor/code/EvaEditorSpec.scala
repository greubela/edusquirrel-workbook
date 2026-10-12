package it.evadid.homepage.webElements.editor.code

import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingStateSnapXmlHelper

import it.evadid.core.datastructures.language.AppLanguage
import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorConfig, EvaEditorSync, EvaProgrammingTab}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
import munit.FunSuite

class EvaEditorSpec extends FunSuite {
  test("tab synchronization compares the converted state and ignores identical restores") {
    val snap = ProgrammingStateSnapXmlHelper.mini
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
    val initial = ProgrammingStateSnapXmlHelper.mini
    var published: Option[ProgrammingState] = None
    val tab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.SnapLanguage, Var[ProgrammingState](initial), next => published = Some(next))
    val next = ProgrammingStateSnapXmlHelper.empty
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
    assert(javaTab.editorElement.isInstanceOf[CodeMirrorEditor])

    val snap = ProgrammingStateSnapXmlHelper.mini
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

  test("language switch commits the editor being left before reading shared state") {
    val state = Var[ProgrammingState](ProgrammingStatePythonString("forward(10)"))
    val pythonTab = EvaProgrammingTab.tabFor(EvaEditorConfig.Default, AppLanguage.Python, state, _ => ())
    var events = List.empty[String]
    val switched = EvaEditorSync.showLanguage(
      commitLeaving = () => {
        events = events :+ "commit"
        state.set(ProgrammingStatePythonString("forward(25)"))
      },
      destinationAccepts = candidate => pythonTab.canSetStateTo(candidate).nonEmpty,
      applyDestination = pythonTab.setStateTo,
      revealDestination = () => events = events :+ "reveal",
      afterReveal = () => (),
      state = () => {
        events = events :+ "read"
        state.now()
      }
    )
    assert(switched)
    assertEquals(events, List("commit", "read", "reveal"))
    assertEquals[ProgrammingState, ProgrammingState](pythonTab.associatedVar.now(), ProgrammingStatePythonString("forward(25)"))
  }

  test("language switch stays put when the committed program cannot convert") {
    val state = Var[ProgrammingState](ProgrammingStatePythonString("forward(10)"))
    var revealed = false
    val switched = EvaEditorSync.showLanguage(
      commitLeaving = () => state.set(ProgrammingStatePythonString("still here")),
      destinationAccepts = _ => false,
      applyDestination = _ => fail("destination must not receive an unacceptable program"),
      revealDestination = () => revealed = true,
      afterReveal = () => revealed = true,
      state = () => state.now()
    )
    assert(!switched)
    assert(!revealed)
    assertEquals(state.now(), ProgrammingStatePythonString("still here"))
  }

  test("CodeMirror maps Java and Python independently") {
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Java), "java")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Python), "python")
  }
}
