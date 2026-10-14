package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.laminar.api.L.{canvasTag, *}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.{SnapCodeEditorConfig, SnapCodeEditorImplDelegateToOriginal}
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateSnapXml
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState}
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
  private val renderingError = Var(Option.empty[String])
  private def createCanvas(currentState: ProgrammingStateSnapXml): Element = {
    var mountGeneration = 0
    canvasTag(
      cls := "be-program-snap-renderer__canvas",
      aria.label := "Block program preview",
      widthAttr := 1,
      heightAttr := 1,
      onMountCallback { ctx =>
        mountGeneration += 1
        val generation = mountGeneration
        val canvas: Canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
        impl.whenReady(() => {
          if generation == mountGeneration then
            renderingError.set(None)
            impl.renderPreviewInto(currentState, canvas, config)
        }, error => {
          if generation == mountGeneration then renderingError.set(Some(s"Block preview unavailable: ${error.getMessage}"))
        })
      },
      onUnmountCallback(_ => mountGeneration += 1)
    )
  }
  private lazy val previewCanvas: Element = {
    div(
      cls := "be-program-snap-renderer",
      child.maybe <-- renderingError.signal.map(_.map(message => p(role := "alert", message))),
      child <-- stateSignal.map(state => Try(state.toSnapXml)).distinctByFn { (previous, next) =>
        previous.isSuccess && previous == next && renderingError.now().isEmpty
      }.map { preview =>
        preview.map(createCanvas).getOrElse {
          renderingError.set(None)
          div("Preview unavailable for this draft.")
        }
      },
    )
  }
  override def getDomElement(): Element = previewCanvas
}
