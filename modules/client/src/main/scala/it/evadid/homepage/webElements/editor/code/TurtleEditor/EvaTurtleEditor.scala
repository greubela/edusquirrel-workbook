package it.evadid.homepage.webElements.editor.code.TurtleEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.{EvaEditor, EvaEditorConfig}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingState

case class EvaTurtleEditor(boundVar: Var[ProgrammingState], snapConfig: SnapCodeEditorConfig) extends HtmlAppElement with FullscreenLifecycle {

  private val evaEditor = EvaEditor(boundVar, EvaEditorConfig(snapConfig = snapConfig), _ => ())

  override def getDomElement(): Element = div()

  override def onFullscreenClose(): Unit = evaEditor.onFullscreenClose()

  override def onFullscreenOpen(): Unit = evaEditor.onFullscreenOpen()
}
