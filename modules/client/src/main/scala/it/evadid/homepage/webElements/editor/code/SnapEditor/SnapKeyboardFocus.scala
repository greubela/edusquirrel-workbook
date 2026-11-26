package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.facades.Snap.WorldMorph
import org.scalajs.dom.html.Canvas

/** Keeps Morphic's keyboard input reachable inside a modal dialog. */
private[SnapEditor] object SnapKeyboardFocus {
  /** Morphic creates one hidden textarea on document.body and focuses it when an input slot is edited. A modal
    * `<dialog>` makes body siblings inert, so mouse events still reach the canvas but the textarea cannot receive
    * keys. Moving the shared handler below the mounted canvas keeps it in the same focus scope without changing
    * Morphic's keyboard/IME event pipeline.
    */
  def keepInEditor(world: WorldMorph, canvas: Canvas): Unit = {
    SnapKeyboardHandlerPosition.install()
    Option(canvas.parentElement).foreach(_.appendChild(world.keyboardHandler))
    world.keyboardHandler.setAttribute("aria-hidden", "true")
    world.keyboardHandler.tabIndex = -1
    world.keyboardHandler.classList.add("snap-keyboard-handler")
  }
}
