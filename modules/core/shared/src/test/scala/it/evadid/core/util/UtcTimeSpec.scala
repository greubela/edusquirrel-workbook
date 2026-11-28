package it.evadid.core.util

import java.time.{Duration, Instant, LocalDateTime, ZoneId, ZoneOffset}

class UtcTimeSpec extends munit.FunSuite {
  test("now is UTC, independent of the device's zone") {
    val utcNow = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
    assert(Duration.between(UtcTime.now(), utcNow).abs.getSeconds < 5)
  }

  test("display converts to the viewer's zone, across DST, and keeps the open range bounds") {
    val berlin = ZoneId.of("Europe/Berlin")
    assertEquals(UtcTime.toLocal(LocalDateTime.of(2026, 7, 1, 10, 0), berlin), LocalDateTime.of(2026, 7, 1, 12, 0))
    assertEquals(UtcTime.toLocal(LocalDateTime.of(2026, 12, 1, 10, 0), berlin), LocalDateTime.of(2026, 12, 1, 11, 0))
    // The repeated local hour when the clocks go back stays ordered in UTC.
    assertEquals(UtcTime.toLocal(LocalDateTime.of(2026, 10, 25, 0, 30), berlin), LocalDateTime.of(2026, 10, 25, 2, 30))
    assertEquals(UtcTime.toLocal(LocalDateTime.of(2026, 10, 25, 1, 30), berlin), LocalDateTime.of(2026, 10, 25, 2, 30))
    assertEquals(UtcTime.toLocal(LocalDateTime.MIN, berlin), LocalDateTime.MIN)
    assertEquals(UtcTime.toLocal(LocalDateTime.MAX, berlin), LocalDateTime.MAX)
  }
}
