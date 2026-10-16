package it.evadid.homepage.webElements.editor.code

import munit.FunSuite
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.elements.interactionElements.programming.state.*

class EvaEditorTabSpec extends FunSuite {
  private val logger = Logger.withNameAndPrefixes(Some("adapter-test"), PrintToStdLogger.printNothing)
  test("Java adapter retains raw incomplete Java and has a Java label") {
    val source = ProgrammingStateJavaString("\r\n class Drawing {\t")
    val tab = JavaFunctionBasedEditor.forEvaTab(logger, CodeMirrorEditorConfig.Default, source)
    assertEquals(tab.captureSource(), source)
    assertEquals(tab.label, "Java")
    val restored = ProgrammingStateJavaString("\n class Restored {\r\n")
    tab.setWith(restored)
    assertEquals(tab.captureSource(), restored)
  }
  test("inactive adapters do not attempt lossy conversion of another language") {
    val unfinished = ProgrammingStatePythonString("def unfinished(")
    val java = JavaFunctionBasedEditor.forEvaTab(logger, CodeMirrorEditorConfig.Default, unfinished)
    val snap = SnapCodeEditor.forEvaTab(logger, SnapCodeEditorConfig.Testing, unfinished)
    assertEquals(java.captureSource(), ProgrammingStateJavaString(""))
    assertEquals(snap.captureSource(), ProgrammingStateSnapXml.empty)
  }
  test("Python adapter stores invalid and restored drafts without parsing them") {
    val tab = CodeMirrorEditor.pythonEditorForEva(logger)
    val source = ProgrammingStatePythonString("\t def invalid(\r\n")
    tab.setWith(source)
    assertEquals(tab.captureSource(), source)
  }
  test("Snap adapter captures typed projects and opaque legacy data before native mounting") {
    val source = ProgrammingStateSnapXml("<project/>", List("watcher\n", "comment\r\n")).toSnapScalaModel.toOption.get
    val tab = SnapCodeEditor.forEvaTab(logger, SnapCodeEditorConfig.Testing, source)
    assertEquals(tab.captureSource(), source)
    val restored = source.copy(model = source.model.transform(_.withAttribute("name", "restored")))
    tab.setWith(restored)
    assertEquals(tab.captureSource(), restored)
  }
}
