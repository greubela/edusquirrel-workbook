package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor.SnapCodeEditorImpl
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch.TurtleStitchToBeExpressionParser
import it.evadid.vm.BeProgram
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

case class SnapCodeEditor(
    program: Var[BeProgram],
    config: SnapCodeEditorConfig,
    impl: SnapCodeEditorImpl,
    onProgramEdited: BeProgram => Unit = _ => ()
) extends HtmlAppElement with FullscreenLifecycle {

  private var previewTarget: Option[Canvas] = None
  private var programObserversBound = false
  /** Python fingerprint of the last BeProgram written from Snap XML (skip inbound reload). */
  private var lastProgramFingerprintFromSnap: Option[String] = None

  private def programFingerprint(program: BeProgram): String =
    program.fullProgram.expressionIO.toStringInLanguage(Python, English, false)

  private def bindProgramObservers(owner: Owner): Unit =
    if programObserversBound then return
    programObserversBound = true
    // Skip the current value: renderEditorInto already loaded it. Only react to
    // later sync restores / external Var updates — not Snap→BeProgram echoes.
    program.signal.changes.foreach { next =>
      if !lastProgramFingerprintFromSnap.contains(programFingerprint(next)) then
        impl.loadProgramIfChanged(next)
      previewTarget.foreach(canvas => impl.renderPreviewInto(next, canvas, config))
    }(using owner)

  private def publishProgramFromSnapXml(xml: String): Unit =
    val next = BeProgram(TurtleStitchToBeExpressionParser.parseXml(xml))
    val nextFingerprint = programFingerprint(next)
    val currentFingerprint = programFingerprint(program.now())
    if !TurtleStitchToBeExpressionParser.hasCallableBlocks(next.fullProgram) then return
    if nextFingerprint == currentFingerprint then
      impl.acknowledgeProgramFromEditor(next)
      return
    lastProgramFingerprintFromSnap = Some(nextFingerprint)
    impl.acknowledgeProgramFromEditor(next)
    onProgramEdited(next)

  lazy val editorCanvas: L.Element = {
    div(
      cls := "be-program-snap-renderer be-program-snap-renderer--editor",
      position.relative,
      overflow.hidden,
      border := "1px solid #d0d7de",
      borderRadius := "10px",
      backgroundColor := config.visuals.ColorWorkspace,
      width := "100%",
      height := "100%",
      minHeight := "0",
      flexGrow := 1,
      flexShrink := 1,
      flexBasis := "auto",
      boxSizing.borderBox,
      canvasTag(
        cls := "be-program-snap-renderer__canvas",
        aria.label := "Block program editor",
        widthAttr := config.visuals.CanvasWidth,
        heightAttr := config.visuals.CanvasHeight,
        display.block,
        width := "100%",
        height := "100%",
        // Construct WorldMorph from the canvas' own mount callback. Besides
        // avoiding an ambiguous descendant query, this guarantees that Snap
        // installs its listeners only after this exact canvas is connected.
        onMountCallback { ctx =>
          val canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
          impl.mount(ctx.owner)
          impl.setOnProjectXmlChangedListener(publishProgramFromSnapXml)
          impl.renderEditorInto(program.now(), canvas, config)
          bindProgramObservers(ctx.owner)
        }
      ),
      onUnmountCallback { _ =>
        // The dialog reuses this lazy DOM element. Keep its WorldMorph and DOM
        // event listeners intact between openings; only stop animation work
        // while the canvas is detached.
        impl.flushPendingProjectChanges()
        impl.pauseWorldCycles()
      }
    )
  }

  lazy val previewCanvas: L.Element = {
    div(
      cls := "be-program-snap-renderer",
      position.relative,
      overflow.hidden,
      border := "1px solid #d0d7de",
      borderRadius := "10px",
      backgroundColor := config.visuals.ColorWorkspace,
      width := "fit-content",
      maxWidth := "100%",
      canvasTag(
        cls := "be-program-snap-renderer__canvas",
        aria.label := "Block program preview",
        widthAttr := config.visuals.CanvasWidth,
        heightAttr := config.visuals.CanvasHeight,
        display.block,
        onMountCallback { ctx =>
          val canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
          previewTarget = Some(canvas)
          // Only observe changes: signal.foreach would redraw the current value
          // again and races with sync restore (flash then blank).
          impl.renderPreviewInto(program.now(), canvas, config)
          program.signal.changes.foreach { next =>
            impl.renderPreviewInto(next, canvas, config)
          }(using ctx.owner)
        },
        onUnmountCallback { _ =>
          previewTarget = None
        }
      )
    )
  }

  override def getDomElement(): L.Element = {
    editorCanvas
  }

  /** Drop custom tabs, optionally removing Snap's default library as well. */
  def removeAllLibraries(includeDefaultLibraries: Boolean = false): Unit =
    impl.removeAllLibraries(includeDefaultLibraries)

  override def onFullscreenOpen(): Unit =
    impl.forceLoadProgram(program.now())
    impl.fitEditorToContainer()
    impl.startWorldCycles()

  override def onFullscreenClose(): Unit =
    // Poll is paused with the world; flush so the last edits reach the Var/sync.
    impl.flushPendingProjectChanges()
    impl.pauseWorldCycles()

}

object SnapCodeEditor {

  def apply(program: Var[BeProgram]): SnapCodeEditor =
    SnapCodeEditor(program, SnapCodeEditorConfig.Testing, SnapCodeEditorImplDelegateToOriginal())

  def apply(program: Var[BeProgram], onProgramEdited: BeProgram => Unit): SnapCodeEditor =
    SnapCodeEditor(program, SnapCodeEditorConfig.Testing, SnapCodeEditorImplDelegateToOriginal(), onProgramEdited)

  trait SnapCodeEditorImpl {

    /** Mount the complete interactive Snap editor and keep its Morphic world ticking. */
    def renderEditorInto(initProgram: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit

    /** Render only the scripts as a static, tightly-sized preview. */
    def renderPreviewInto(program: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit

    /** Apply an externally restored BeProgram to the retained editor without recreating the world. */
    def loadProgramIfChanged(program: BeProgram): Unit

    /** Like loadProgramIfChanged but always re-opens (fullscreen reopen / hard restore). */
    def forceLoadProgram(program: BeProgram): Unit

    /** Record that the live Snap project was just written into this BeProgram (skip lossy reload). */
    def acknowledgeProgramFromEditor(program: BeProgram): Unit

    /** Push any pending Snap XML edits into the change listener immediately. */
    def flushPendingProjectChanges(): Unit

    def mount(ctx: Owner): Unit

    /** Start driving the Morphic world. Snap controls only become responsive while cycles run. */
    def startWorldCycles(): Unit

    /** Pause Morphic updates without destroying the mounted editor. */
    def pauseWorldCycles(): Unit

    /** Match canvas bitmap+CSS to the fullscreen parent and relayout Morphic. */
    def fitEditorToContainer(): Unit

    /** Register a listener for XML changes caused by edits in the mounted Snap project. */
    def setOnProjectXmlChangedListener(callback: String => Unit): Unit

    /** Remove custom tabs and, when requested, Snap's standard library too. */
    def removeAllLibraries(includeDefaultLibraries: Boolean = false): Unit

    def destroy(): Unit
  }


}
