package it.evadid.core.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object InfoUtil {


  def datetimeFormattedForFilenames(dateTime: LocalDateTime = LocalDateTime.now()): String = {
    dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
  }

  def datetimeFormattedForLog(dateTime: LocalDateTime = LocalDateTime.now()): String = {
    dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
  }


}
