package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

import scala.scalajs.js

/** Sizes the editor canvas to its container and follows container resizes.
  *
  * `mounted` names the current canvas and configuration; `relayout` lays the IDE out again after the canvas
  * bitmap changed. Fits are coalesced: one per animation frame after layout, one per debounce after resizes.
  */
private[SnapEditor] final class SnapCanvasLayout(mounted: () => Option[(Canvas, SnapCodeEditorConfig)], relayout: Canvas => Unit) {
  import SnapCanvasLayout.*

  private val handles = Var(Handles())

  /** Fits the canvas now and once more after the dialog layout has settled. */
  def fit(): Unit = {
    apply()
    fitAfterLayout()
  }

  def fitAfterLayout(): Unit = {
    if handles.now().frame != 0 then dom.window.cancelAnimationFrame(handles.now().frame)
    handles.update(_.copy(frame = dom.window.requestAnimationFrame { _ =>
      handles.update(_.copy(frame = 0))
      apply()
    }))
  }

  private def fitAfterResize(): Unit = {
    if handles.now().debounce != 0 then dom.window.clearTimeout(handles.now().debounce)
    handles.update(_.copy(debounce = dom.window.setTimeout(() => {
      handles.update(_.copy(debounce = 0))
      apply()
    }, FitDebounceMs)))
  }

  private def apply(): Unit = mounted() match {
    case Some((canvas, config)) if canvas.isConnected => if size(canvas, config) then relayout(canvas)
    case _ => ()
  }

  def observe(canvas: Canvas): Unit = {
    disconnect()
    Option(canvas.parentElement).foreach { parent =>
      val callback: js.Function0[Unit] = () => fitAfterResize()
      val observer = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(callback).asInstanceOf[dom.ResizeObserver]
      observer.observe(parent)
      handles.update(_.copy(observer = Some(observer)))
    }
  }

  def disconnect(): Unit = {
    val current = handles.now()
    current.observer.foreach(_.disconnect())
    if current.frame != 0 then dom.window.cancelAnimationFrame(current.frame)
    if current.debounce != 0 then dom.window.clearTimeout(current.debounce)
    handles.set(Handles())
  }
}

private[SnapEditor] object SnapCanvasLayout {
  private val FitDebounceMs = 32.0

  private final case class Handles(observer: Option[dom.ResizeObserver] = None, frame: Int = 0, debounce: Int = 0)

  /** Gives the canvas its container's size; true when the bitmap changed (and thus was cleared).
    *
    * CSS and bitmap coordinates stay identical: CSS-only scaling in the fullscreen dialog produced independent X/Y
    * factors and distorted hits. Assigning canvas.width/height always clears the bitmap, even with an unchanged
    * value, which left a blank IDE until the next click, so unchanged sizes are not assigned.
    */

  /** Retina may pin CSS dimensions during initialization; the containing layout owns those dimensions. */
  def releaseRetinaCssSize(canvas: Canvas): Unit = {
    canvas.style.removeProperty("width")
    canvas.style.removeProperty("height")
  }

  def size(canvas: Canvas, config: SnapCodeEditorConfig): Boolean = {
    val parent = Option(canvas.parentElement)
    val parentWidth = parent.map(_.clientWidth).getOrElse(0)
    val parentHeight = parent.map(_.clientHeight).getOrElse(0)
    val width = math.max(1, if parentWidth > 0 then parentWidth else config.visuals.CanvasWidth)
    val height = math.max(1, if parentHeight > 0 then parentHeight else config.visuals.CanvasHeight)
    val bitmapChanged = CanvasSize.differs(canvas, width, height)
    if bitmapChanged then {
      canvas.width = width
      canvas.height = height
    }
    releaseRetinaCssSize(canvas)
    // WorldMorph registers mouse/touch listeners synchronously in its constructor. Make this exact mounted canvas an
    // explicit input target; a second canvas would only copy pixels, not those listeners or the world behind them.
    canvas.tabIndex = 0
    bitmapChanged
  }
}
