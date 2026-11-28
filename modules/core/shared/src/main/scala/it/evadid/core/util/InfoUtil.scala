package it.evadid.core.util

import java.time.{Instant, LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter

object InfoUtil {
  /** `instant` in the viewer's zone, for display only; instants beyond the local range show as its bounds. */
  def localDateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    scala.util.Try(LocalDateTime.ofInstant(instant, zone)).getOrElse(if instant.isBefore(Instant.EPOCH) then LocalDateTime.MIN else LocalDateTime.MAX)

  // Formatters are immutable and thread-safe. Parsing a pattern is costly, in Scala.js also because it
  // throws and catches exceptions internally, so each pattern is parsed once.
  private val filenameFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
  private val otherYearFormat = DateTimeFormatter.ofPattern("HH:mm (dd.MM.YY)")
  private val otherDayFormat = DateTimeFormatter.ofPattern("HH:mm (dd.MM)")
  private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
  private val logFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  private val mailFormat = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")

  def datetimeFormattedForLog(dateTime: Option[Instant]): String = dateTime.map(datetimeFormattedForLog).getOrElse("[no time]")

  /** `dateTime` in the viewer's local time. */
  def datetimeFormattedForFilenames(dateTime: Instant = Instant.now()): String = localDateTime(dateTime).format(filenameFormat)

  private val transliterations = Map('ä' -> "ae", 'ö' -> "oe", 'ü' -> "ue", 'Ä' -> "Ae", 'Ö' -> "Oe", 'Ü' -> "Ue", 'ß' -> "ss",
    'å' -> "aa", 'Å' -> "Aa", 'æ' -> "ae", 'Æ' -> "Ae", 'ø' -> "oe", 'Ø' -> "Oe")

  /** A file or folder name that unpacks the same everywhere: ASCII letters, digits, `-` and `_`. */
  def fileNameSafe(text: String): String = {
    val ascii = text.trim.flatMap(c => transliterations.getOrElse(c, c.toString))
    val cleaned = ascii.map(c => if (c.isLetterOrDigit && c < 128) || c == '-' || c == '_' then c else '_')
      .replaceAll("_+", "_").stripPrefix("_").stripSuffix("_")
    if cleaned.isEmpty then "project" else cleaned
  }

  /** The name of every download, without its extension: when it was made, then what it holds
    * (for example `2026-11-27_13-05-09_brick-task`).
    */
  def downloadName(subject: String, dateTime: Instant = Instant.now()): String =
    s"${datetimeFormattedForFilenames(dateTime)}_${fileNameSafe(subject)}"

  /** `dateTime` in the viewer's local time, shortened relative to today. */
  def datetimeFormattedForHumans(dateTime: Instant = Instant.now()): String = {
    val local = localDateTime(dateTime)
    val today = localDateTime(Instant.now())
    if (local.getYear != today.getYear) local.format(otherYearFormat)
    else if (local.toLocalDate != today.toLocalDate) local.format(otherDayFormat)
    else local.format(timeFormat)
  }

  /** Logs keep UTC. */
  def datetimeFormattedForLog(dateTime: Instant = Instant.now()): String = localDateTime(dateTime, java.time.ZoneOffset.UTC).format(logFormat)

  /** The date line of a simulated mail, in the viewer's local time, like the scenario's own mails. */
  def datetimeFormattedForMail(dateTime: Instant = Instant.now()): String = localDateTime(dateTime).format(mailFormat)

}
