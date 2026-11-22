package it.evadid.homepage.control.info

import com.raquo.laminar.api.L.*
import it.evadid.workbook.interaction.sync.TypingUpdateImportance.TypedKey
import org.scalajs.dom

import java.time.LocalDateTime

/** The latest key typed anywhere on the page, recorded before editors handle it.
  *
  * Code editors derive each answer change's importance from it (see `TypingUpdateImportance`), reading the
  * key from the event itself rather than from a difference of states.
  */
final class KeyboardInput private (val lastKey: StrictSignal[Option[TypedKey]])

object KeyboardInput {
  /** One listener per page; the capture phase sees keys before editors stop their propagation. */
  lazy val installed: KeyboardInput = {
    val latest = Var(Option.empty[TypedKey])
    dom.document.addEventListener("keydown", (event: dom.KeyboardEvent) =>
      latest.set(Some(TypedKey(event.key, LocalDateTime.now()))), useCapture = true)
    new KeyboardInput(latest.signal)
  }
}
