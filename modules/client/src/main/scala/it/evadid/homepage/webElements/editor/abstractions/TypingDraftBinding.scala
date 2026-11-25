package it.evadid.homepage.webElements.editor.abstractions

import com.raquo.laminar.api.L.*
import it.evadid.workbook.interaction.sync.TypingDraft
import it.evadid.workbook.interaction.sync.TypingUpdateImportance.TypedKey

import java.time.LocalDateTime

/** Binds a typing editor to an answer while keeping its keystrokes local (see [[TypingDraft]]).
  *
  * The editor reads and writes `draft`; `answer` receives only committed values. Add `binders` to the
  * editor's root element: they record the keys typed inside it, commit TEMPORARY edits after their quiet
  * period, write commits to the answer and adopt outside answer changes. Pending edits are committed when
  * focus leaves the editor's content (e.g. a click on a run or check button) and when the editor unmounts.
  */
final class TypingDraftBinding[T](answer: Var[T]) {
  private val state = Var(TypingDraft(answer.now(), LocalDateTime.now(), Seq.empty))
  private val lastKey = Var(Option.empty[TypedKey])

  /** The editor's value; each change is an edit whose importance follows the latest key typed in the editor. */
  val draft: Var[T] = state.zoomLazy(_.value)((current, value) => current.edit(value, lastKey.now(), LocalDateTime.now()))

  /** Commits `value` at once, e.g. the result of a language conversion. */
  def commit(value: T): Unit = transition(_.commit(value, LocalDateTime.now()))

  /** Applies a transition only if it changes the draft: the editor treats every draft emission as an edit
    * (which cancels pending conversions), so no-op checks and flushes must stay silent. */
  private def transition(change: TypingDraft[T] => TypingDraft[T]): TypingDraft[T] = {
    val current = state.now()
    val next = change(current)
    if next != current then state.set(next)
    next
  }

  private def write(value: T): Unit = if value != answer.now() then answer.set(value)

  private def flush(): Unit = write(transition(_.flush(LocalDateTime.now())).committed)

  val binders: Seq[Modifier[HtmlElement]] = Seq(
    onKeyDown.useCapture --> (event => lastKey.set(Some(TypedKey(event.key, LocalDateTime.now())))),
    // One delayed check per TEMPORARY edit; it commits only an edit that is still the latest.
    state.signal.map(_.latestEdit).distinct.changes.collectSome.delay(TypingDraft.quietPeriod.toMillis.toInt) -->
      (edit => transition(_.settle(edit, LocalDateTime.now()))),
    state.signal.map(_.committed).changes --> (value => write(value)),
    answer.signal.changes --> (value => transition(_.received(value, LocalDateTime.now()))),
    // focusout bubbles after the blur that nested editors capture to hand over their typed text.
    TypingDraftBinding.onFocusOut --> (_ => flush()),
    // Nested editors hand over their typed text in their own unmount callbacks; the flush follows them.
    onUnmountCallback(_ => scala.concurrent.Future.unit.foreach(_ => flush())(using TypingDraftBinding.afterCurrentTask)))
}

object TypingDraftBinding {
  private val onFocusOut = new com.raquo.laminar.keys.EventProp[org.scalajs.dom.FocusEvent]("focusout")
  private val afterCurrentTask: scala.concurrent.ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
}
