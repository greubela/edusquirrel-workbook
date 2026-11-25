package it.evadid.homepage.webElements.editor.code.SnapEditor

import org.scalajs.dom
import scala.scalajs.js

/** Morphic places its shared keyboard textarea in document coordinates, which only hold while the textarea is a
  * child of document.body. The editor moves it below the mounted canvas (see keepKeyboardHandlerInEditor), where
  * `position: absolute` refers to the positioned editor element instead. The document coordinates then pointed far
  * below the canvas, and every keystroke scrolled the clipped editor to reveal the caret: the canvas jumped and the
  * lower part of the editor turned blank until the next keystroke scrolled it back.
  *
  * Both Morphic methods that position the textarea are wrapped once; the wrapper converts the document coordinates
  * into the textarea's containing block. Under document.body nothing changes.
  */
private[SnapEditor] object SnapKeyboardHandlerPosition:
  private val PatchedMarker = "evaContainerRelative"

  def install(): Unit =
    wrap(js.Dynamic.global.WorldMorph.selectDynamic("prototype"), "resetKeyboardHandler", _.selectDynamic("keyboardHandler"))
    wrap(js.Dynamic.global.CursorMorph.selectDynamic("prototype"), "updateTextAreaPosition", _.selectDynamic("textarea"))

  private def wrap(prototype: js.Dynamic, method: String, textareaOf: js.Dynamic => js.Dynamic): Unit =
    val original = prototype.selectDynamic(method)
    if js.typeOf(original) == "function" && js.isUndefined(original.selectDynamic(PatchedMarker)) then
      val wrapped: js.ThisFunction1[js.Dynamic, js.Any, js.Any] = (self, argument) =>
        val result = original.call(self, argument)
        val textarea = textareaOf(self)
        if !js.isUndefined(textarea) && textarea != null then
          toContainingBlock(textarea.asInstanceOf[dom.HTMLElement])
        result
      wrapped.asInstanceOf[js.Dynamic].updateDynamic(PatchedMarker)(true)
      prototype.updateDynamic(method)(wrapped)

  private def toContainingBlock(textarea: dom.HTMLElement): Unit =
    textarea.offsetParent match
      case parent: dom.HTMLElement if parent ne dom.document.body =>
        val rect = parent.getBoundingClientRect()
        // Document position of the padding box origin that `top`/`left` refer to.
        val originX = rect.left + parent.clientLeft - parent.scrollLeft + dom.window.scrollX
        val originY = rect.top + parent.clientTop - parent.scrollTop + dom.window.scrollY
        shift(textarea, "left", originX)
        shift(textarea, "top", originY)
      case _ => ()

  private def shift(textarea: dom.HTMLElement, property: String, origin: Double): Unit =
    val documentValue = js.Dynamic.global.parseFloat(textarea.style.getPropertyValue(property)).asInstanceOf[Double]
    if !documentValue.isNaN then textarea.style.setProperty(property, s"${math.ceil(documentValue - origin).toInt}px")
