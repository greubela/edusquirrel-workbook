package it.evadid.util

import it.evadid.core.util.{InfoUtil, LocalZone}
import java.time.Instant
import munit.FunSuite
import scala.scalajs.js

class JavaTimePlatformSpec extends FunSuite {
  test("display times agree with the JavaScript platform clock in the local zone") {
    val before = js.Date.now()
    val current = InfoUtil.localDateTime(Instant.now())
    val after = js.Date.now()
    val platformTime = new js.Date(current.getYear, current.getMonthValue - 1, current.getDayOfMonth,
      current.getHour, current.getMinute, current.getSecond, current.getNano / 1000000).getTime()
    assert(platformTime >= before - 1 && platformTime <= after,
      s"Local display time $current differs from the platform clock: $platformTime outside [$before, $after]")
  }

  test("display follows the browser's zone in summer and winter without a time zone database") {
    for (text <- List("2026-07-01T10:00:00.123Z", "2026-12-01T10:00:00Z", "2026-10-25T00:30:00Z", "2026-10-25T01:30:00Z")) {
      val instant = Instant.parse(text)
      val local = InfoUtil.localDateTime(instant)
      val date = new js.Date(instant.toEpochMilli.toDouble)
      assertEquals((local.getYear, local.getMonthValue, local.getDayOfMonth, local.getHour, local.getMinute, local.getNano / 1000000),
        (date.getFullYear().toInt, date.getMonth().toInt + 1, date.getDate().toInt, date.getHours().toInt, date.getMinutes().toInt, date.getMilliseconds().toInt), text)
    }
    val summer = java.time.LocalDateTime.of(2026, 7, 1, 12, 0, 0, 5)
    assertEquals(InfoUtil.localDateTime(LocalZone.instantOf(summer)), summer)
    assertEquals(InfoUtil.localDateTime(Instant.MIN), java.time.LocalDateTime.MIN)
    assertEquals(InfoUtil.localDateTime(Instant.MAX), java.time.LocalDateTime.MAX)
  }
}
