package it.evadid.workbook.interaction.sync

import java.time.{Duration, LocalDateTime}

/** Importance of an answer change made in a code editor, decided from the key that produced it.
  *
  * Enter ends a line and commits the answer (MAJOR); other keystrokes are TEMPORARY. Once typing has paused
  * for [[idleCommitDelay]], the client commits the current TEMPORARY answer (`InteractionVariable.commitCurrentState`).
  * Each MAJOR answer stays in the history, so committing while typing would grow it by a full program per word.
  * A change without a recent key (block edits, buttons, tab switches) is a deliberate edit and therefore MAJOR.
  */
object TypingUpdateImportance {
  /** The key the user pressed, as reported by the browser's `KeyboardEvent.key`. */
  final case class TypedKey(key: String, timestamp: LocalDateTime)

  /** A pause in typing of this length commits the current answer. */
  val idleCommitDelay: Duration = Duration.ofSeconds(3)
  /** A key older than this did not produce the change. */
  val keyRelevance: Duration = Duration.ofSeconds(1)
  private val committingKeys = Set("Enter")

  def commits(key: String): Boolean = committingKeys(key)

  def decide(key: Option[TypedKey], now: LocalDateTime): UpdateImportance =
    key.filter(typed => Duration.between(typed.timestamp, now).compareTo(keyRelevance) <= 0) match {
      case None => UpdateImportance.MAJOR
      case Some(typed) if commits(typed.key) => UpdateImportance.MAJOR
      case Some(_) => UpdateImportance.TEMPORARY
    }
}
