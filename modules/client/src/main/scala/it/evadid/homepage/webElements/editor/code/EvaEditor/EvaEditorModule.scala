package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.Element
import it.evadid.homepage.webElements.FullscreenLifecycle
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

trait EvaEditorModule extends FullscreenLifecycle {
  def bind(context: EvaEditorModule.Context): Unit = ()
  def getDomElement: Option[Element] = None
  override def onFullscreenClose(): Unit = ()
}

object EvaEditorModule {
  final case class Context(state: Var[ProgrammingState], captureSource: () => ProgrammingState,
      modules: Seq[EvaEditorModule])
}
