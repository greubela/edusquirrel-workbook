package it.evadid.server.events.submission

import java.time.{Clock, Duration, Instant}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/** Accepts at most `limit(key)` attempts per key within each sliding `window`. The accepted attempts are an
  * immutable map replaced atomically; entries older than the window are dropped on every attempt.
  */
final class RateLimit[K](window: Duration, limit: K => Int, clock: Clock = Clock.systemUTC) {
  private val accepted = AtomicReference(Map.empty[K, Seq[Instant]])

  /** Records an attempt for all `keys` and returns true, unless one of them already reached its limit. */
  def tryAcquire(keys: Set[K]): Boolean = {
    val now = clock.instant()
    @tailrec def attempt(): Boolean = {
      val current = accepted.get()
      val recent = current.view.mapValues(_.filter(_.isAfter(now.minus(window)))).filter(_._2.nonEmpty).toMap
      val allowed = keys.forall(key => recent.getOrElse(key, Nil).size < limit(key))
      val next = if allowed then keys.foldLeft(recent)((times, key) => times.updated(key, times.getOrElse(key, Nil) :+ now)) else recent
      if accepted.compareAndSet(current, next) then allowed else attempt()
    }
    attempt()
  }
}
