package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.laminar.api.L.{canvasTag, *}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.{SnapCodeEditorConfig, SnapCodeEditorImplDelegateToOriginal}
import it.evadid.workbook.elements.interactionElements.programming.*
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import scala.util.Try

object SnapPreviewEditor {
  def apply(state: Var[ProgrammingState], config: SnapCodeEditorConfig): SnapPreviewEditor =
    SnapPreviewEditor(state.signal, config, SnapCodeEditorImplDelegateToOriginal())

  def apply(state: Var[ProgrammingState]): SnapPreviewEditor =
    SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, SnapCodeEditorImplDelegateToOriginal())
}

case class SnapPreviewEditor(
                              stateSignal: StrictSignal[ProgrammingState],
                              config: SnapCodeEditorConfig,
                              impl: SnapCodeEditorImpl
                            ) extends HtmlAppElement {
  private def createCanvas(currentState: ProgrammingStateSnapXml): Element = {
    canvasTag(
      cls := "be-program-snap-renderer__canvas",
      aria.label := "Block program preview",
      widthAttr := config.visuals.CanvasWidth,
      heightAttr := config.visuals.CanvasHeight,
      onMountCallback { ctx =>
        val canvas: Canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
        impl.loadProgramIfChanged(currentState)
        impl.renderPreviewInto(currentState, canvas, config)
      }
    )
  }
  private lazy val previewCanvas: Element = {
    div(
      cls := "be-program-snap-renderer",
      child <-- stateSignal.map { state =>
        Try(state.toSnapXml).map(createCanvas).getOrElse(div("Preview unavailable for this draft."))
      },
    )
  }
  override def getDomElement(): Element = previewCanvas
}
