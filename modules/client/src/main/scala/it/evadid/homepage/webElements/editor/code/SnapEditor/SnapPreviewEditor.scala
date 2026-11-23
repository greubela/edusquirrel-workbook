package it.evadid.homepage.webElements.editor.code.SnapEditor


import com.raquo.laminar.api.L.{canvasTag, *}
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import org.scalajs.dom
import com.raquo.airstream.ownership.Subscription
import org.scalajs.dom.html.Canvas
import scala.util.Try

object SnapPreviewEditor {
  def apply(state: Var[ProgrammingState], config: SnapCodeEditorConfig,
            humanLanguage: Signal[HumanLanguage]): SnapPreviewEditor =
    SnapPreviewEditor(state.signal, config, SnapMorphicSession(), humanLanguage)

  def apply(state: Var[ProgrammingState], config: SnapCodeEditorConfig): SnapPreviewEditor =
    SnapPreviewEditor(state.signal, config, SnapMorphicSession())

  def apply(state: Var[ProgrammingState]): SnapPreviewEditor =
    SnapPreviewEditor(state.signal, SnapCodeEditorConfig.Testing, SnapMorphicSession())
}

case class SnapPreviewEditor(
                              stateSignal: StrictSignal[ProgrammingState],
                              config: SnapCodeEditorConfig,
                              impl: SnapCodeEditorImpl,
                              humanLanguage: Signal[HumanLanguage] = Val(English)
                            ) extends HtmlAppElement {
  private val renderingError = Var(Option.empty[String])
  private def createCanvas(currentState: ProgrammingStateRaw, language: HumanLanguage): Element = {
    canvasTag(
      cls := "be-program-snap-renderer__canvas",
      aria.label := "Block program preview",
      widthAttr := 1,
      heightAttr := 1,
      onMountCallback { ctx =>
        val active = Var(true)
        val canvas: Canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
        // Each render blocks the page: they start one per task, once the canvas nears the viewport.
        val stopWaiting = SnapPreviewScheduler.whenVisible(canvas) { () =>
          if active.now() then impl.whenReady(language, () => {
            if active.now() then
              renderingError.set(None)
              impl.changeLanguage(language)
              impl.renderPreviewInto(currentState, canvas, config)
          }, error => {
            if active.now() then renderingError.set(Some(s"Block preview unavailable: ${error.getMessage}"))
          })
        }
        new Subscription(ctx.owner, () => { active.set(false); stopWaiting() })
      }
    )
  }
  private lazy val previewCanvas: Element = {
    div(
      cls := "be-program-snap-renderer",
      child.maybe <-- renderingError.signal.map(_.map(message => p(role := "alert", message))),
      child <-- stateSignal.map(state => Try(state.toSnapXml)).combineWith(humanLanguage).distinctByFn { (previous, next) =>
        previous._1.isSuccess && previous == next && renderingError.now().isEmpty
      }.map { (preview, language) =>
        preview.map(state => createCanvas(state, language)).getOrElse {
          renderingError.set(None)
          div("Preview unavailable for this draft.")
        }
      },
    )
  }
  override def getDomElement(): Element = previewCanvas
}
