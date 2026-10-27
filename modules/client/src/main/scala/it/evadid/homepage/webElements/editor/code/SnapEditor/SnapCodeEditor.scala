package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.facades.Snap.*
import com.raquo.airstream.ownership.{Owner, Subscription}
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage, SnapLanguage}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorTab
import it.evadid.vm.code.errors.ProgrammingConversionError
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.JavaTurtleSnapPrimitives
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.{SnapCodeEditorConfig, SnapCodeEditorImplDelegateToOriginal}
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

case class SnapCodeEditor(
                           logger: Logger,
                           state: Var[ProgrammingState],
                           config: SnapCodeEditorConfig,
                           impl: SnapCodeEditorImpl,
                           humanLanguage: Signal[HumanLanguage] = Val(English),
                           conversionMarker: Signal[Option[ProgrammingConversionError]] = Val(None),
                           loadingLabel: Signal[String] = Val("Loading editor…")
                         ) extends HtmlAppElement {

  // A typed view of the interaction Var: no second answer or synchronization store.
  private[SnapEditor] val snapState: Var[ProgrammingStateSnapScalaModel] = state.bimap {
    case model: ProgrammingStateSnapScalaModel => model.persistent
    case xml: ProgrammingStateSnapXml => SnapCodeEditor.typed(xml)
    case other if other.associatedLanguage == SnapLanguage => SnapCodeEditor.typed(other.toSnapXml)
    case _ => SnapCodeEditor.empty // inactive while Eva switches to another language
  }(identity)

  private def publishProgramFromSnapXml(xml: String): Unit = {
    if state.now().associatedLanguage == SnapLanguage then {
      val current = snapState.now()
      val next = SnapCodeEditor.typed(ProgrammingStateSnapXml(xml, current.legacyFloatingObjects))
      impl.acknowledgeProgramFromEditor(next.toSnapXml)
      if !current.meaningfullyEquals(next) then snapState.set(next)
    }
  }

  private[SnapEditor] def mountEditorInto(canvas: Canvas, owner: Owner): Unit = {
    JavaTurtleSnapPrimitives.install()
    impl.mount(owner)
    impl.setOnProjectXmlChangedListener(publishProgramFromSnapXml)
    impl.renderEditorInto(snapState.now().toSnapXml, canvas, config)
    // Mount once. Later updates only synchronize the retained project;
    // acknowledgeProgramFromEditor makes Snap-originated echoes no-ops.
    state.signal.changes.filter(_.associatedLanguage == SnapLanguage).map {
      case model: ProgrammingStateSnapScalaModel => model.persistent
      case other => SnapCodeEditor.typed(other.toSnapXml)
    }.distinctByFn((previous, next) => previous.meaningfullyEquals(next)).foreach(model => impl.loadProgramIfChanged(model.toSnapXml))(using owner)
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
      cls <-- conversionMarker.map(mark =>
        "code-editor be-program-snap-renderer be-program-snap-renderer--editor" +
          (if mark.isDefined then " is-conversion-marked" else "")),
      child.maybe <-- renderingError.signal.map(_.map(message => p(role := "alert", message))),
      child <-- editorReady.signal.combineWith(renderingError.signal, loadingLabel).map { (ready, error, label) =>
        if !ready && error.isEmpty then p(cls := "code-editor__loading", role := "status", label) else emptyNode
      },
      editorCanvas,
      // Morphic's shared textarea lives inside this element. Commit before an
      // outside control (especially a language tab) reads the central answer.
      onBlur.useCapture.filter(_.target == dom.document.getElementById("morphic_keyboard")) --> { _ =>
        if state.now().associatedLanguage == SnapLanguage then impl.flushPendingProjectChanges()
      },
      onUnmountCallback { _ =>
        if state.now().associatedLanguage == SnapLanguage then impl.flushPendingProjectChanges()
        impl.setOnProjectXmlChangedListener(_ => ())
        impl.destroy()
      }
    )
  }

  private val renderingError = Var(Option.empty[String])
  private val editorReady = Var(false)

  lazy val domElement: L.Element =
    div(
      cls := "code-editor-container be-program-snap-fullscreen",
      editorDom
    )

  override def getDomElement(): L.Element = domElement

  override def dismissOnOutsideClick: Boolean = false
}

object SnapCodeEditor {
  private def typed(xml: ProgrammingStateSnapXml): ProgrammingStateSnapScalaModel =
    xml.toSnapScalaModel.fold(message => throw IllegalArgumentException(message), _.persistent)
  private lazy val empty = typed(ProgrammingStateSnapXml.empty)

  def apply(logger: Logger, state: Var[ProgrammingState], config: SnapCodeEditorConfig,
            humanLanguage: Signal[HumanLanguage]): SnapCodeEditor =
    SnapCodeEditor(logger, state, config, SnapCodeEditorImplDelegateToOriginal(), humanLanguage)

  def apply(logger: Logger,state: Var[ProgrammingState], config: SnapCodeEditorConfig): SnapCodeEditor =
    apply(logger, state, config, Val(English))

  def apply(logger: Logger,state: Var[ProgrammingState]): SnapCodeEditor =
    SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, SnapCodeEditorImplDelegateToOriginal())

  def forEvaTab(tabLogger: Logger, config: SnapCodeEditorConfig, humanLanguage: Signal[HumanLanguage],
      tabLabel: String = "Snap!"): EvaEditorTab = new EvaEditorTab {
    override val language = SnapLanguage
    override val label = tabLabel
    override def convert(source: ProgrammingState): ProgrammingState = source match {
      case snap: ProgrammingStateSnapScalaModel => snap.persistent
      case _ => typed(source.toSnapXml)
    }
    override def create(state: Var[ProgrammingState], appearance: it.evadid.homepage.webElements.editor.code.EditorAppearance,
        marker: Signal[Option[ProgrammingConversionError]], loading: Signal[String]): HtmlAppElement =
      SnapCodeEditor(tabLogger, state, config, SnapCodeEditorImplDelegateToOriginal(), humanLanguage, marker, loading)
  }
}
