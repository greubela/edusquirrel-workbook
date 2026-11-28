package it.evadid.core.util

import java.time.{LocalDateTime, ZoneId, ZoneOffset}
import scala.util.Try

/** Every saved, exchanged or compared `LocalDateTime` is UTC, so devices in other zones and DST changes keep event
  * order. Only display converts to the viewer's zone.
  */
object UtcTime {
  def now(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

  /** `utc` in `zone`, for display; the open range bounds `LocalDateTime.MIN`/`MAX` stay as they are. */
  def toLocal(utc: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    if utc == LocalDateTime.MIN || utc == LocalDateTime.MAX then utc
    else Try(utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime).getOrElse(utc)
}
