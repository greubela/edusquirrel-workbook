package it.evadid.core.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object InfoUtil {
  // Formatters are immutable and thread-safe. Parsing a pattern is costly, in Scala.js also because it
  // throws and catches exceptions internally, so each pattern is parsed once.
  private val filenameFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
  private val otherYearFormat = DateTimeFormatter.ofPattern("HH:mm (dd.MM.YY)")
  private val otherDayFormat = DateTimeFormatter.ofPattern("HH:mm (dd.MM)")
  private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
  private val logFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def datetimeFormattedForLog(dateTime: Option[LocalDateTime]): String = dateTime.map(datetimeFormattedForLog).getOrElse("[no time]")

  /** `dateTime` (UTC) in the viewer's local time. */
  def datetimeFormattedForFilenames(dateTime: LocalDateTime = UtcTime.now()): String = UtcTime.toLocal(dateTime).format(filenameFormat)

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
  def downloadName(subject: String, dateTime: LocalDateTime = UtcTime.now()): String =
    s"${datetimeFormattedForFilenames(dateTime)}_${fileNameSafe(subject)}"

  /** `dateTime` (UTC) in the viewer's local time, shortened relative to today. */
  def datetimeFormattedForHumans(dateTime: LocalDateTime = UtcTime.now()): String = {
    val local = UtcTime.toLocal(dateTime)
    val today = UtcTime.toLocal(UtcTime.now())
    if (local.getYear != today.getYear) local.format(otherYearFormat)
    else if (local.toLocalDate != today.toLocalDate) local.format(otherDayFormat)
    else local.format(timeFormat)
  }

  /** Logs keep UTC. */
  def datetimeFormattedForLog(dateTime: LocalDateTime = UtcTime.now()): String = dateTime.format(logFormat)

  def datetimeFormattedForDb(dateTime: LocalDateTime = UtcTime.now()): String = logFormat.format(dateTime)

}
