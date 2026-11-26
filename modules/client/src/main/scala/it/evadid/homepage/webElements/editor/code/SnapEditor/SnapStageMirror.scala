package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var
import it.evadid.facades.Snap.{IDEMorph, StageMorph}
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.Canvas

import scala.scalajs.js

/** Runs the green flag on the editor's stage at a visible pace and copies the stage onto a target canvas until
  * its processes have finished. `editor` is the session's IDE; `keepTicking` makes sure Morphic cycles run.
  */
private[SnapEditor] final class SnapStageMirror(editor: () => Option[IDEMorph], keepTicking: () => Unit) {
  import SnapStageMirror.*

  private val state = Var(MirrorState())

  /** While mirroring, the session skips its (heavy) project XML checks. */
  def isMirroring: Boolean = state.now().frame != 0

  def setStepMs(ms: Double): Unit = state.update(_.copy(stepMs = math.max(0.0, ms)))

  def run(target: Canvas): Unit = {
    // Execute can be pressed after reopen/unmount cycles; ensure Morphic is ticking.
    keepTicking()
    editor().foreach { ide =>
      stop()
      val stage = ide.stage
      if stage != null then {
        // Scratch-paced yields (not turbo), pausing `stepMs` per block.
        try {
          stage.isFastTracked = false
          enableStepping()
        } catch case _: Throwable => ()
        try ide.stopAllScripts() catch case _: Throwable => ()
        try stage.clearPenTrails() catch case _: Throwable => ()
        val started = try { ide.runScripts(); true } catch case _: Throwable => false
        if !started then restoreStepping()
        else {
          state.update(_.copy(idleFrames = 0, lastMirrorAt = 0.0))
          mirror(stage, target, force = true)
          def tick(time: Double): Unit = {
            mirror(stage, target, force = false, now = time)
            state.update(current => current.copy(idleFrames = if processesRunning(stage) then 0 else current.idleFrames + 1))
            if state.now().idleFrames < IdleSettleFrames then
              state.update(_.copy(frame = dom.window.requestAnimationFrame(next => tick(next))))
            else {
              state.update(_.copy(frame = 0))
              mirror(stage, target, force = true, now = time)
              restoreStepping()
            }
          }
          state.update(_.copy(frame = dom.window.requestAnimationFrame(time => tick(time))))
        }
      }
    }
  }

  def stop(): Unit = {
    if state.now().frame != 0 then dom.window.cancelAnimationFrame(state.now().frame)
    state.update(_.copy(frame = 0, idleFrames = 0))
    editor().foreach(ide => try ide.stopAllScripts() catch case _: Throwable => ())
    restoreStepping()
  }

  /** Snap's visible stepping: pause `stepMs` between blocks (0 = no step delay). */
  private def enableStepping(): Unit = {
    val proto = js.Dynamic.global.Process.selectDynamic("prototype")
    state.update(current => current.copy(
      savedFlashTime = current.savedFlashTime.orElse(Some(proto.selectDynamic("flashTime").asInstanceOf[Double])),
      savedSingleStepping = current.savedSingleStepping.orElse(Some(proto.selectDynamic("enableSingleStepping").asInstanceOf[Boolean]))))
    val stepMs = state.now().stepMs
    proto.updateDynamic("flashTime")(if stepMs <= 0 then 0.0 else stepMs / 1000.0)
    proto.updateDynamic("enableSingleStepping")(stepMs > 0)
  }

  private def restoreStepping(): Unit = {
    val current = state.now()
    // Closing a pending editor is valid even before threads.js defines Process.
    if current.savedFlashTime.nonEmpty || current.savedSingleStepping.nonEmpty then {
      val proto = js.Dynamic.global.Process.selectDynamic("prototype")
      current.savedFlashTime.foreach(proto.updateDynamic("flashTime")(_))
      current.savedSingleStepping.foreach(proto.updateDynamic("enableSingleStepping")(_))
    }
    state.update(_.copy(savedFlashTime = None, savedSingleStepping = None))
  }

  /** Copies the live stage onto `target`: pen trails during the animation (a cheap blit), `fullImage()` for forced
    * frames so that the final frame includes costumes and sprites.
    */
  private def mirror(stage: StageMorph, target: Canvas, force: Boolean, now: Double = dom.window.performance.now()): Unit =
    if force || now - state.now().lastMirrorAt >= MinMirrorIntervalMs then {
      state.update(_.copy(lastMirrorAt = now))
      try {
        if force || !mirrorPenTrails(stage, target) then {
          val source = stage.fullImage()
          if source != null then {
            if CanvasSize.differs(target, CanvasSize.roundedWidth(source), CanvasSize.roundedHeight(source)) then {
              target.width = CanvasSize.roundedWidth(source)
              target.height = CanvasSize.roundedHeight(source)
            }
            val context = target.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
            context.clearRect(0, 0, CanvasSize.width(target), CanvasSize.height(target))
            context.drawImage(source, 0, 0)
          }
        }
      } catch case error: Throwable =>
        println(s"Snap Execute stage mirror failed: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }
}

private[SnapEditor] object SnapStageMirror {
  /** Extra mirror frames after processes go idle, so pen trails settle. */
  private val IdleSettleFrames = 8
  /** fullImage() is too expensive for every animation frame. */
  private val MinMirrorIntervalMs = 33.0

  private final case class MirrorState(
      frame: Int = 0,
      idleFrames: Int = 0,
      lastMirrorAt: Double = 0.0,
      stepMs: Double = 20.0,
      savedFlashTime: Option[Double] = None,
      savedSingleStepping: Option[Boolean] = None)

  /** Blits only the trails canvas, the main visual of turtle programs; false when there is none. */
  private def mirrorPenTrails(stage: StageMorph, target: Canvas): Boolean =
    try {
      val trails = stage.penTrails()
      if trails == null || CanvasSize.width(trails) <= 0 || CanvasSize.height(trails) <= 0 then false
      else {
        if CanvasSize.differs(target, CanvasSize.roundedWidth(trails), CanvasSize.roundedHeight(trails)) then {
          target.width = CanvasSize.roundedWidth(trails)
          target.height = CanvasSize.roundedHeight(trails)
        }
        val context = target.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
        context.fillStyle = "#ffffff"
        context.fillRect(0, 0, CanvasSize.width(trails), CanvasSize.height(trails))
        context.drawImage(trails, 0, 0)
        true
      }
    } catch case _: Throwable => false

  private def processesRunning(stage: StageMorph): Boolean =
    try {
      val processes = stage.asInstanceOf[js.Dynamic].selectDynamic("threads").selectDynamic("processes").asInstanceOf[js.Array[js.Dynamic]]
      processes.exists { process =>
        val errorFlag = try process.selectDynamic("errorFlag").asInstanceOf[Boolean] catch case _: Throwable => false
        val isDead = try process.selectDynamic("isDead").asInstanceOf[Boolean] catch case _: Throwable => false
        val isRunning = try process.applyDynamic("isRunning")().asInstanceOf[Boolean] catch case _: Throwable => !errorFlag
        !errorFlag && !isDead && isRunning
      }
    } catch case _: Throwable =>
      try stage.threads.processes.length > 0 catch case _: Throwable => false
}
