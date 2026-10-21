package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.homepage.webElements.facades.ExternalDependencies.Snap.*
import com.raquo.airstream.ownership.{Owner, Subscription}
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.SnapLanguage
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorTab
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
                           impl: SnapCodeEditorImpl
                         ) extends HtmlAppElement {


  private def publishProgramFromSnapXml(xml: String): Unit = {
    val next: ProgrammingState = state.now() match {
      case snap: ProgrammingStateSnapXml => snap.withProjectXml(xml).removeBloatFromXml
      case snap: ProgrammingStateSnapScalaModel =>
        snap.toSnapXml.withProjectXml(xml).removeBloatFromXml.toSnapScalaModel.fold(
          message => throw IllegalArgumentException(message), identity)
      case _ => ProgrammingStateSnapXml(xml).removeBloatFromXml
    }
    // Mark the retained project before publishing: the Var observers run as
    // part of this update and must not reload Snap's own serialized project.
    impl.acknowledgeProgramFromEditor(next.toSnapXml)
    if state.now().associatedLanguage == SnapLanguage && state.now() != next then
      state.set(next)
  }

  private[SnapEditor] def mountEditorInto(canvas: Canvas, owner: Owner): Unit = {
    JavaTurtleSnapPrimitives.install()
    impl.mount(owner)
    impl.setOnProjectXmlChangedListener(publishProgramFromSnapXml)
    impl.renderEditorInto(state.now().toSnapXml, canvas, config)
    // Mount once. Later updates only synchronize the retained project;
    // acknowledgeProgramFromEditor makes Snap-originated echoes no-ops.
    state.signal.changes.filter(_.associatedLanguage == SnapLanguage).map(_.toSnapXml).distinct.foreach(impl.loadProgramIfChanged)(using owner)
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
        new Subscription(ctx.owner, () => active.set(false))
        val canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
        impl.whenReady(() => {
          if active.now() then
            renderingError.set(None)
            mountEditorInto(canvas, ctx.owner)
        }, error => {
          if active.now() then renderingError.set(Some(s"Block editor unavailable: ${error.getMessage}"))
        })
      }
    )
  }

  lazy val editorDom: L.Element = {
    div(
      cls := "code-editor be-program-snap-renderer be-program-snap-renderer--editor",
      child.maybe <-- renderingError.signal.map(_.map(message => p(role := "alert", message))),
      editorCanvas,
      onUnmountCallback { _ =>
        if state.now().associatedLanguage == SnapLanguage then impl.flushPendingProjectChanges()
        impl.setOnProjectXmlChangedListener(_ => ())
        impl.destroy()
      }
    )
  }

  private val renderingError = Var(Option.empty[String])

  lazy val domElement: L.Element =
    div(
      cls := "code-editor-container be-program-snap-fullscreen",
      editorDom
    )

  override def getDomElement(): L.Element = domElement

  override def dismissOnOutsideClick: Boolean = false
}


object SnapCodeEditor {

  def apply(logger: Logger,state: Var[ProgrammingState], config: SnapCodeEditorConfig): SnapCodeEditor =
    SnapCodeEditor(logger, state, config, SnapCodeEditorImplDelegateToOriginal())

  def apply(logger: Logger,state: Var[ProgrammingState]): SnapCodeEditor =
    SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, SnapCodeEditorImplDelegateToOriginal())


  def forEvaTab(tabLogger: Logger, config: SnapCodeEditorConfig): EvaEditorTab = new EvaEditorTab {
    override val language = SnapLanguage
    override val label = "Snap!"
    override def convert(source: ProgrammingState): ProgrammingState = source match {
      case snap: ProgrammingStateSnapScalaModel => snap
      case _ => source.toSnapXml
    }
    override def create(state: Var[ProgrammingState], appearance: it.evadid.homepage.webElements.editor.code.EditorAppearance): HtmlAppElement =
      SnapCodeEditor(tabLogger, state, config)
  }
}
