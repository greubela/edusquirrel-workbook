package it.evadid.util

import it.evadid.core.util.InfoUtil
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
}
