package it.evadid.server

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference

/** A UTC clock that tests advance explicitly. */
final class ManualClock(start: Instant) extends Clock {
  private val current = AtomicReference(start)
  def advanceSeconds(seconds: Long): Unit = current.updateAndGet(_.plusSeconds(seconds))
  override def getZone: ZoneId = ZoneOffset.UTC
  override def withZone(zone: ZoneId): Clock = this
  override def instant(): Instant = current.get()
}
