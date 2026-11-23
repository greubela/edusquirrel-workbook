package it.evadid.workbook.interaction.sync

import it.evadid.workbook.interaction.sync.TypingUpdateImportance.TypedKey

import java.time.{Duration, LocalDateTime}

/** A local edit of a typing editor and the importance decided when it was typed. */
final case class TypingEdit[T](value: T, importance: UpdateImportance, timestamp: LocalDateTime)

/** A typing editor's local state: the value it last committed to the answer and the TEMPORARY edits since.
  *
  * An edit that commits by [[TypingUpdateImportance]] (a token-ending key, five seconds since the last
  * commit, a change without a recent key) is committed at once. A TEMPORARY edit is committed only when it
  * is still the latest edit after [[TypingDraft.quietPeriod]] ([[settle]]); keystrokes in between stay local.
  */
final case class TypingDraft[T](committed: T, committedAt: LocalDateTime, pending: Seq[TypingEdit[T]]) {
  /** The value the editor shows. */
  def value: T = pending.lastOption.fold(committed)(_.value)

  def latestEdit: Option[TypingEdit[T]] = pending.lastOption

  def edit(newValue: T, key: Option[TypedKey], now: LocalDateTime): TypingDraft[T] =
    if newValue == value then this
    else TypingUpdateImportance.decide(key, Some(committedAt), now) match {
      case UpdateImportance.TEMPORARY => copy(pending = pending :+ TypingEdit(newValue, UpdateImportance.TEMPORARY, now))
      case _ => commit(newValue, now)
    }

  /** Ends the quiet period of `edit`: it is committed unless a newer edit or a commit superseded it. */
  def settle(edit: TypingEdit[T], now: LocalDateTime): TypingDraft[T] =
    if latestEdit.contains(edit) then commit(edit.value, now) else this

  /** Commits the pending edits, e.g. when the editor closes. */
  def flush(now: LocalDateTime): TypingDraft[T] = if pending.isEmpty then this else commit(value, now)

  /** Commits `newValue` at once, discarding pending edits (a deliberate change such as a language switch). */
  def commit(newValue: T, now: LocalDateTime): TypingDraft[T] = TypingDraft(newValue, now, Seq.empty)

  /** The answer changed. A value other than the last commit (a restored or reset answer) replaces the draft. */
  def received(answer: T, now: LocalDateTime): TypingDraft[T] = if answer == committed then this else commit(answer, now)
}

object TypingDraft {
  /** How long a TEMPORARY edit must remain the latest before it is committed. */
  val quietPeriod: Duration = Duration.ofSeconds(1)
}
