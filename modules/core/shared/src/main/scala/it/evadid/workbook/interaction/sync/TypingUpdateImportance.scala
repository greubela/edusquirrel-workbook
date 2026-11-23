package it.evadid.workbook.interaction.sync

import java.time.{Duration, LocalDateTime}

/** Importance of an answer change made in a code editor, decided from the key that produced it.
  *
  * Whitespace and structural characters end a token and commit the answer (MAJOR); other keystrokes are
  * TEMPORARY, unless the last MAJOR answer is older than [[commitInterval]]. A change without a recent key
  * (block edits, buttons, tab switches) is a deliberate edit and therefore MAJOR.
  */
object TypingUpdateImportance {
  /** The key the user pressed, as reported by the browser's `KeyboardEvent.key`. */
  final case class TypedKey(key: String, timestamp: LocalDateTime)

  val commitInterval: Duration = Duration.ofSeconds(5)
  /** A key older than this did not produce the change. */
  val keyRelevance: Duration = Duration.ofSeconds(1)
  private val committingKeys = Set(" ", "Enter", "Tab")
  private val committingCharacters = "()[]{}.,-+*;"

  def commits(key: String): Boolean = committingKeys(key) || key.length == 1 && committingCharacters.contains(key)

  def decide(key: Option[TypedKey], lastMajor: Option[LocalDateTime], now: LocalDateTime): UpdateImportance = {
    def olderThan(time: LocalDateTime, limit: Duration) = Duration.between(time, now).compareTo(limit) > 0
    key.filterNot(typed => olderThan(typed.timestamp, keyRelevance)) match {
      case None => UpdateImportance.MAJOR
      case Some(typed) if commits(typed.key) => UpdateImportance.MAJOR
      case Some(_) if lastMajor.forall(olderThan(_, commitInterval)) => UpdateImportance.MAJOR
      case Some(_) => UpdateImportance.TEMPORARY
    }
  }
}
