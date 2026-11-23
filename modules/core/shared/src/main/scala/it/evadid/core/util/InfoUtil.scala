package it.evadid.core.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object InfoUtil {
  // Formatters are immutable and thread-safe. Parsing a pattern is costly, in Scala.js also because it
  // throws and catches exceptions internally, so each pattern is parsed once.
  private val filenameFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
  private val otherYearFormat = DateTimeFormatter.ofPattern("HH:mm (dd.MM.YY)")
  private val otherDayFormat = DateTimeFormatter.ofPattern("HH:mm (dd.MM)")
  private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
  private val logFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def datetimeFormattedForLog(dateTime: Option[LocalDateTime]): String = dateTime.map(datetimeFormattedForLog).getOrElse("[no time]")

  def datetimeFormattedForFilenames(dateTime: LocalDateTime = LocalDateTime.now()): String = dateTime.format(filenameFormat)

  def datetimeFormattedForHumans(dateTime: LocalDateTime = LocalDateTime.now()): String = {
    val today = LocalDateTime.now()
    if (dateTime.getYear != today.getYear) dateTime.format(otherYearFormat)
    else if (dateTime.toLocalDate != today.toLocalDate) dateTime.format(otherDayFormat)
    else dateTime.format(timeFormat)
  }

  def datetimeFormattedForLog(dateTime: LocalDateTime = LocalDateTime.now()): String = dateTime.format(logFormat)

  def datetimeFormattedForDb(dateTime: LocalDateTime = LocalDateTime.now()): String = logFormat.format(dateTime)

}
