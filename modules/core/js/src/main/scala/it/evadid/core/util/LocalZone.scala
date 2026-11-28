package it.evadid.core.util

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.scalajs.js

/** The viewer's time zone as the browser applies it, so no time zone database is bundled. */
object LocalZone {
  def offsetAt(instant: Instant): ZoneOffset =
    ZoneOffset.ofTotalSeconds(math.round(-new js.Date(instant.toEpochMilli.toDouble).getTimezoneOffset() * 60).toInt)

  def instantOf(local: LocalDateTime): Instant = {
    val date = new js.Date(2000, 0, 1)
    date.setFullYear(local.getYear, local.getMonthValue - 1, local.getDayOfMonth)
    date.setHours(local.getHour, local.getMinute, local.getSecond, 0)
    Instant.ofEpochMilli(date.getTime().toLong).plusNanos(local.getNano)
  }
}
