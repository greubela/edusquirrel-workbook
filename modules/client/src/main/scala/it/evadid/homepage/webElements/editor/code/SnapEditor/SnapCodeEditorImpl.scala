package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom.html.Canvas

trait SnapCodeEditorImpl {

  /** DOM consumers must wait for the asynchronously loaded Snap scripts. */
  def whenReady(render: () => Unit, onError: Throwable => Unit): Unit =
    scala.util.Try(render()).fold(onError, _ => ())

  /** Await the requested dictionary as well as the native runtime. */
  def whenReady(language: HumanLanguage, render: () => Unit, onError: Throwable => Unit): Unit =
    whenReady(render, onError)

  def changeLanguage(humanLanguage: HumanLanguage): Unit

  /** Mount the complete interactive Snap editor bound to `project` ([[SnapProjectBinding]]) until `owner` ends, and
    * keep its Morphic world ticking. A `None` project is not shown as Snap: Snap keeps its project and writes nothing.
    */
  def renderEditorInto(project: Var[Option[ProgrammingStateSnapScalaModel]], canvas: Canvas, config: SnapCodeEditorConfig)(
      using owner: Owner): Unit

  /** Render only the scripts as a static, tightly-sized preview. */
  def renderPreviewInto(state: ProgrammingStateRaw, canvas: Canvas, config: SnapCodeEditorConfig): Unit

  /** Write pending Snap edits, including an unfinished slot text, into the bound project now. */
  def flushPendingProjectChanges(): Unit

  /** Start driving the Morphic world. Snap controls only become responsive while cycles run. */
  def startWorldCycles(): Unit

  /** Pause Morphic updates without destroying the mounted editor. */
  def pauseWorldCycles(): Unit

  /**
   * Green-flag the live (hidden) Snap stage and mirror frames onto `mirrorTarget`
   * so turtle motion animates Scratch-style in the run panel.
   */
  def runGreenFlagOnStage(mirrorTarget: Canvas): Unit

  /** Stop green-flag processes and cancel stage mirroring. */
  def stopGreenFlagOnStage(): Unit

  /** Pause between blocks during Execute, in milliseconds (>= 0). */
  def setGreenFlagStepMs(ms: Double): Unit

  /** Match the canvas bitmap and CSS size to its container and relayout Morphic. */
  def fitEditorToContainer(): Unit

  /** Remove custom tabs and, when requested, Snap's standard library too. */
  def removeAllLibraries(includeDefaultLibraries: Boolean = false): Unit

  def destroy(): Unit
}
