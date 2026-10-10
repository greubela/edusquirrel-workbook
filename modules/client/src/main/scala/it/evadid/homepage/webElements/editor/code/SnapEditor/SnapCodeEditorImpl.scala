package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.Owner
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateSnapXml
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom.html.Canvas

trait SnapCodeEditorImpl {

  /** Mount the complete interactive Snap editor and keep its Morphic world ticking. */
  def renderEditorInto(initState: ProgrammingStateSnapXml, canvas: Canvas, config: SnapCodeEditorConfig): Unit

  /** Render only the scripts as a static, tightly-sized preview. */
  def renderPreviewInto(state: ProgrammingStateSnapXml, canvas: Canvas, config: SnapCodeEditorConfig): Unit

  /** Apply an externally restored state to the retained editor without recreating the world. */
  def loadProgramIfChanged(state: ProgrammingStateSnapXml): Unit

  /** Like loadProgramIfChanged but always re-opens (fullscreen reopen / hard restore). */
  def forceLoadProgram(state: ProgrammingStateSnapXml): Unit

  /** Record that the live Snap project was just written into this state (skip lossy reload). */
  def acknowledgeProgramFromEditor(state: ProgrammingStateSnapXml): Unit

  /** Push any pending Snap XML edits into the change listener immediately. */
  def flushPendingProjectChanges(): Unit

  /** The retained IDE's exact live XML, when it has been mounted. */
  def currentProjectXml(): Option[String]

  def mount(ctx: Owner): Unit

  /** Start driving the Morphic world. Snap controls only become responsive while cycles run. */
  def startWorldCycles(): Unit

  /** Pause Morphic updates without destroying the mounted editor. */
  def pauseWorldCycles(): Unit

  /**
   * Green-flag the live (hidden) Snap stage and mirror frames onto `mirrorTarget`
   * so turtle motion animates Scratch-style in the fullscreen panel.
   */
  def runGreenFlagOnStage(mirrorTarget: Canvas): Unit

  /** Stop green-flag processes and cancel stage mirroring. */
  def stopGreenFlagOnStage(): Unit

  /** Pause between blocks during Execute, in milliseconds (>= 0). */
  def setGreenFlagStepMs(ms: Double): Unit

  /** Match canvas bitmap+CSS to the fullscreen parent and relayout Morphic. */
  def fitEditorToContainer(): Unit

  /** Register a listener for XML changes caused by edits in the mounted Snap project. */
  def setOnProjectXmlChangedListener(callback: String => Unit): Unit

  /** Remove custom tabs and, when requested, Snap's standard library too. */
  def removeAllLibraries(includeDefaultLibraries: Boolean = false): Unit

  def destroy(): Unit
}
