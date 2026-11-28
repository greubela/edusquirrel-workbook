package it.evadid.server.events.submission

import munit.FunSuite
import it.evadid.server.ManualClock
import java.time.{Duration, Instant}

class RateLimitSpec extends FunSuite {
  test("attempts beyond a key's limit are refused until the window has passed, without recording them") {
    val clock = ManualClock(Instant.parse("2026-11-28T10:00:00Z"))
    val limit = RateLimit[String](Duration.ofMinutes(1), key => if key == "shared" then 2 else 1, clock)
    assert(limit.tryAcquire(Set("a", "shared")))
    assert(!limit.tryAcquire(Set("a", "shared")), "a is exhausted")
    assert(limit.tryAcquire(Set("b", "shared")))
    assert(!limit.tryAcquire(Set("c", "shared")), "shared is exhausted")
    assert(limit.tryAcquire(Set("c")), "the refused attempt did not use c")
    clock.advanceSeconds(60)
    assert(limit.tryAcquire(Set("a", "shared")))
  }

  test("concurrent attempts never exceed the limit") {
    val limit = RateLimit[String](Duration.ofMinutes(1), _ => 5)
    val accepted = (1 to 200).par(limit)
    assertEquals(accepted, 5)
  }

  extension (attempts: Range) private def par(limit: RateLimit[String]): Int = {
    import scala.concurrent.{Await, Future}
    import scala.concurrent.duration.*
    given scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    Await.result(Future.traverse(attempts.toList)(_ => Future(limit.tryAcquire(Set("key")))), 10.seconds).count(identity)
  }
}
