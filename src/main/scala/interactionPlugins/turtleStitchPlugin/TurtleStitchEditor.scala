package interactionPlugins.turtleStitchPlugin

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.Promise as JsPromise
import scala.scalajs.js.annotation.JSGlobal

/**
 * Instance-local TurtleStitch editor lifecycle wrapper.
 *
 * Each Scala class instance owns exactly one JS editor handle between mount/unmount.
 * No global/shared handle cache is used.
 */
final class TurtleStitchEditor(
    private val parentNode: dom.Node,
    private val width: Int = 1400,
    private val height: Int = 1000,
    private val hidden: Boolean = true
) {

  private var editorHandle: Option[TurtleStitchEditor.JsEditorHandle] = None

  def mount(): JsPromise[TurtleStitchEditor.JsEditorHandle] = {
    editorHandle match {
      case Some(handle) => JsPromise.resolve(handle)
      case None =>
        val options = js.Dynamic.literal(
          parentNode = parentNode,
          width = width,
          height = height,
          hidden = hidden
        )

        TurtleStitchEditor.TurtleStitchPoCNative
          .createEditor(options.asInstanceOf[js.Object])
          .`then`[TurtleStitchEditor.JsEditorHandle]({ handle =>
            editorHandle = Some(handle)
            handle
          })
    }
  }

  def unmount(): Unit = {
    editorHandle.foreach(_.destroy())
    editorHandle = None
  }
}

object TurtleStitchEditor {

  @js.native
  @JSGlobal("TurtleStitchPoC")
  object TurtleStitchPoCNative extends js.Object {
    def createEditor(options: js.Object): JsPromise[JsEditorHandle] = js.native
  }

  @js.native
  trait JsEditorHandle extends js.Object {
    def destroy(): Unit = js.native
  }
}
