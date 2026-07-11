package it.evadid.core.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object InfoUtil {

  def datetimeFormattedForLog(dateTime: Option[LocalDateTime]): String = dateTime.map(datetimeFormattedForLog).getOrElse("[no time]")

  def datetimeFormattedForFilenames(dateTime: LocalDateTime = LocalDateTime.now()): String = {
    dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
  }

  def datetimeFormattedForHumans(dateTime: LocalDateTime = LocalDateTime.now()): String = {
    val today = LocalDateTime.now()
    if (dateTime.getYear != today.getYear) dateTime.format(DateTimeFormatter.ofPattern("HH:mm (dd.MM.YY)"))
    else if (dateTime.toLocalDate != today.toLocalDate) dateTime.format(DateTimeFormatter.ofPattern("HH:mm (dd.MM)"))
    else dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
  }

  def datetimeFormattedForLog(dateTime: LocalDateTime = LocalDateTime.now()): String = {
    dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
  }

  def datetimeFormattedForDb(dateTime: LocalDateTime = LocalDateTime.now()): String = {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    formatter.format(dateTime)
  }

}
