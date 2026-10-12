package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorConfig
import munit.FunSuite
import upickle.default.*

class CodeMirrorEditorConfigSpec extends FunSuite {
  test("the regular editor accepts both gutter settings and exposes the passed configuration") {
    for (enabled <- List(false, true)) {
      val settings = CodeMirrorEditorConfig(showFlowchartGutter = enabled)
      val editor = CodeMirrorEditor(Var("print(1)"), editorConfig = settings)
      assertEquals(editor.config.now(), settings)
      assertEquals(read[CodeMirrorEditorConfig](write(settings)), settings)
      assertEquals(readBinary[CodeMirrorEditorConfig](writeBinary(settings)), settings)
    }
  }

  test("default and compatibility editors permit disabling the gutter") {
    assertEquals(CodeMirrorEditor(Var("")).config.now().showFlowchartGutter, false)
    assertEquals(CodeMirrorEditorFlowchart(Var("")).config.now().showFlowchartGutter, true)
    assertEquals(CodeMirrorEditorFlowchart(Var(""), editorConfig = CodeMirrorEditorConfig.Default)
      .config.now().showFlowchartGutter, false)
    assertEquals(read[CodeMirrorEditorConfig]("{}"), CodeMirrorEditorConfig.Default)
  }

  test("Eva propagates its editor settings and old serialized configurations retain defaults") {
    val configured = EvaEditorConfig(codeMirrorConfig = CodeMirrorEditorConfig.Default)
    assertEquals(read[EvaEditorConfig](write(configured)), configured)
    assertEquals(read[EvaEditorConfig]("{}").codeMirrorConfig, CodeMirrorEditorConfig.Flowchart)
  }
}
