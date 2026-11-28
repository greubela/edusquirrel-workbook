package it.evadid.core.util

import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}

/** The viewer's time zone: the JVM's zone rules. */
object LocalZone {
  def offsetAt(instant: Instant): ZoneOffset = ZoneId.systemDefault().getRules.getOffset(instant)

  def instantOf(local: LocalDateTime): Instant = local.atZone(ZoneId.systemDefault()).toInstant
}
