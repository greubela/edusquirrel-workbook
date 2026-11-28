package it.evadid.core.util

import java.time.{Instant, LocalDateTime, ZoneId}

class LocalDisplayTimeSpec extends munit.FunSuite {
  test("display converts instants to the viewer's zone, across DST, and keeps the open range bounds") {
    val berlin = ZoneId.of("Europe/Berlin")
    def at(text: String) = Instant.parse(text)
    assertEquals(InfoUtil.localDateTime(at("2026-07-01T10:00:00Z"), berlin), LocalDateTime.of(2026, 7, 1, 12, 0))
    assertEquals(InfoUtil.localDateTime(at("2026-12-01T10:00:00Z"), berlin), LocalDateTime.of(2026, 12, 1, 11, 0))
    // The local hour repeated when the clocks go back maps two distinct instants.
    assertEquals(InfoUtil.localDateTime(at("2026-10-25T00:30:00Z"), berlin), LocalDateTime.of(2026, 10, 25, 2, 30))
    assertEquals(InfoUtil.localDateTime(at("2026-10-25T01:30:00Z"), berlin), LocalDateTime.of(2026, 10, 25, 2, 30))
    assertEquals(InfoUtil.localDateTime(Instant.MIN, berlin), LocalDateTime.MIN)
    assertEquals(InfoUtil.localDateTime(Instant.MAX, berlin), LocalDateTime.MAX)
  }
}
