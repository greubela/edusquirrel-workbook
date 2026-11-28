package it.evadid.util

import java.time.LocalDateTime
import munit.FunSuite
import scala.scalajs.js

class JavaTimePlatformSpec extends FunSuite {
  test("local timestamps agree with the JavaScript platform clock") {
    val before = js.Date.now()
    val current = LocalDateTime.now()
    val after = js.Date.now()
    val platformTime = new js.Date(current.getYear, current.getMonthValue - 1, current.getDayOfMonth,
      current.getHour, current.getMinute, current.getSecond, current.getNano / 1000000).getTime()
    assert(platformTime >= before - 1 && platformTime <= after,
      s"Local timestamp $current differs from the platform clock: $platformTime outside [$before, $after]")
  }
}
