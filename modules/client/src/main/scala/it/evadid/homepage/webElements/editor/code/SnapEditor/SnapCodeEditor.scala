package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}

import it.evadid.facades.Snap.*
import com.raquo.airstream.ownership.{Owner, Subscription}
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage, SnapLanguage}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorContext, EvaEditorLabels, EvaEditorTab}
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.JavaTurtleSnapPrimitives
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

case class SnapCodeEditor(
                           logger: Logger,
                           state: Var[ProgrammingState],
                           config: SnapCodeEditorConfig,
                           impl: SnapCodeEditorImpl,
                           loadingLabel: Signal[String],
                           humanLanguage: Signal[HumanLanguage] = Val(English),
                           conversionMarked: Signal[Boolean] = Val(false)
                         ) extends HtmlAppElement {

  /** The answer as the Snap project this editor shows; `None` while the answer is not shown as Snap (e.g. after a
    * language switch, before Laminar unmounts this editor), and writes are then dropped.
    */
  private[SnapEditor] val project: Var[Option[ProgrammingStateSnapScalaModel]] =
    state.zoomLazy(SnapCodeEditor.shown)((current, value) => value.filter(_ => SnapCodeEditor.shownAsSnap(current)).getOrElse(current))

  private[SnapEditor] def mountEditorInto(canvas: Canvas, owner: Owner): Unit = {
    JavaTurtleSnapPrimitives.install()
    impl.renderEditorInto(project, canvas, config)(using owner)
  }

  lazy val editorCanvas: Element = {
    canvasTag(
      cls := "be-program-snap-renderer__canvas",
      aria.label := "Block program editor",
      widthAttr := config.visuals.CanvasWidth,
      heightAttr := config.visuals.CanvasHeight,
      // Construct WorldMorph from the canvas' own mount callback. Besides
      // avoiding an ambiguous descendant query, this guarantees that Snap
      // installs its listeners only after this exact canvas is connected.
      onMountCallback { ctx =>
        val active = Var(true)
        val mounted = Var(false)
        val request = Var(Option.empty[(HumanLanguage, AnyRef)])
        new Subscription(ctx.owner, () => active.set(false))
        val canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
        // Observe the committed request so immediately ready implementations
        // see the same lifecycle guard as asynchronous dictionary loads.
        request.signal.changes.collect { case Some(value) => value }.foreach { (language, token) =>
          impl.whenReady(language, () => {
            if active.now() && request.now().contains(language -> token) then
              renderingError.set(None)
              impl.changeLanguage(language)
              if !mounted.now() then
                mountEditorInto(canvas, ctx.owner)
                mounted.set(true)
                editorReady.set(true)
          }, error => {
            if active.now() && request.now().contains(language -> token) then
              renderingError.set(Some(s"Block editor unavailable: ${error.getMessage}"))
          })
        }(using ctx.owner)
        humanLanguage.distinct.foreach(language => request.set(Some(language -> new Object)))(using ctx.owner)
      }
    )
  }

  lazy val editorDom: L.Element = {
    div(
      cls <-- conversionMarked.map(mark =>
        "code-editor be-program-snap-renderer be-program-snap-renderer--editor" +
          (if mark then " is-conversion-marked" else "")),
      child.maybe <-- renderingError.signal.map(_.map(message => p(role := "alert", message))),
      child <-- editorReady.signal.combineWith(renderingError.signal, loadingLabel).map { (ready, error, label) =>
        if !ready && error.isEmpty then p(cls := "code-editor__loading", role := "status", label) else emptyNode
      },
      editorCanvas,
      // Snap edits come only from input here, so the heavy project comparison runs once the input pauses.
      onPointerUp.useCapture.mapToUnit --> inputEnded.writer,
      onKeyUp.useCapture.mapToUnit --> inputEnded.writer,
      inputEnded.events.debounce(SnapCodeEditor.inputPauseMillis) --> { _ =>
        if SnapCodeEditor.shownAsSnap(state.now()) then impl.publishProjectChanges()
      },
      // Morphic's shared textarea lives inside this element. Commit before an
      // outside control (especially a language tab) reads the central answer.
      onBlur.useCapture.filter(_.target == dom.document.getElementById("morphic_keyboard")) --> { _ =>
        impl.flushPendingProjectChanges()
      },
      onUnmountCallback { _ =>
        impl.flushPendingProjectChanges()
        impl.destroy()
      }
    )
  }

  private val renderingError = Var(Option.empty[String])
  private val editorReady = Var(false)
  private val inputEnded = new EventBus[Unit]

  lazy val domElement: L.Element =
    div(
      cls := "code-editor-container be-program-snap-fullscreen",
      editorDom
    )

  override def getDomElement(): L.Element = domElement

  override def dismissOnOutsideClick: Boolean = false
}

object SnapCodeEditor {
  /** Input pause after which Snap edits become the bound answer. */
  val inputPauseMillis: Int = 400

  /** Snap projects, and expression answers whose Snap view the editor shows (e.g. in previews). */
  private[SnapEditor] def shownAsSnap(state: ProgrammingState): Boolean =
    state.associatedLanguage == SnapLanguage || state.isInstanceOf[ProgrammingStateBeExpression]
  private def shown(state: ProgrammingState): Option[ProgrammingStateSnapScalaModel] = state match {
    case model: ProgrammingStateSnapScalaModel => Some(model.persistent)
    case other if shownAsSnap(other) => Some(SnapProjectBinding.typed(other.toSnapXml.code))
    case _ => None
  }

  def forEvaTab(config: SnapCodeEditorConfig): EvaEditorTab = new EvaEditorTab {
    override val language = SnapLanguage
    override def icon = Some(it.evadid.homepage.webElements.editor.code.EvaEditor.EvaLanguageIcons.snap)
    override def label(labels: EvaEditorLabels): Signal[String] = Val("Snap!")
    override def create(state: Var[ProgrammingState], context: EvaEditorContext): HtmlAppElement =
      SnapCodeEditor(context.logger, state, config, SnapMorphicSession(), context.loading,
        context.environment.humanLanguage, context.conversionMarker.map(_.isDefined))
  }
}
