package it.evadid.core.util

import java.time.LocalDateTime
import munit.FunSuite
import scala.collection.mutable

class UtilityCoverageSpec extends FunSuite {
  test("dependency results are calculated once even when shared by several callers") {
    val calls = mutable.Map.empty[Int, Int].withDefaultValue(0)
    val fibonacci = FunctionalUtility.withCacheAndResolvedDependencies[Int, Int] { (n, resolve) =>
      calls(n) += 1
      if (n < 2) n else resolve(n - 1) + resolve(n - 2)
    }
    assertEquals(fibonacci(8), 21)
    assertEquals(fibonacci(7), 13)
    assertEquals(calls.values.toList.distinct, List(1))
  }

  test("a failed dependency calculation can be retried") {
    var fail = true
    val expected = new IllegalArgumentException("temporary")
    val resolve = FunctionalUtility.withCacheAndResolvedDependencies[Int, Int] { (n, dependency) =>
      if (n == 0 && fail) throw expected
      if (n == 0) 10 else dependency(n - 1) + 1
    }
    assert(intercept[IllegalArgumentException](resolve(2)) eq expected)
    fail = false
    assertEquals(resolve(2), 12)
  }

  test("cycles are diagnosed without poisoning later calculations") {
    val resolve = FunctionalUtility.withCacheAndResolvedDependencies[Int, Int] { (n, dependency) =>
      if (n < 2) dependency(1 - n) else n
    }
    val error = intercept[IllegalStateException](resolve(0))
    assert(error.getMessage.contains("Cyclic dependency"))
    assertEquals(resolve(3), 3)
    assert(intercept[IllegalStateException](resolve(1)).getMessage.contains("Cyclic dependency"))
  }

  test("code builder supports inline parameters, multiline input and indentation changes") {
    val builder = CodeStringBuilderMutable("f").appendParameters(List("a", "b")).appendInLine(":")
      .setIntLevel(1).appendAllAsLines(List("first\nsecond", "third"))
      .changeIntLevel(-1).appendNextLine("end")
    assertEquals(builder.toString, "f(a,b):\n    first\n    second\n    third\nend")
    assert(builder.appendAllAsLines(Nil) eq builder)
    assertEquals(CodeStringBuilderMutable().appendParameters(Nil).toString, "()")
    assertEquals(CodeStringBuilderMutable().changeForEach(List(1, 2), (b, n: Int) => b.appendInLine(n.toString)).toString, "12")
  }

  test("date formats are stable for storage and distinguish human dates") {
    val fixed = LocalDateTime.of(2024, 2, 3, 4, 5, 6)
    assertEquals(InfoUtil.datetimeFormattedForLog(fixed), "2024-02-03 04:05:06")
    assertEquals(InfoUtil.datetimeFormattedForLog(Some(fixed)), "2024-02-03 04:05:06")
    assertEquals(InfoUtil.datetimeFormattedForLog(None), "[no time]")
    assertEquals(InfoUtil.datetimeFormattedForDb(fixed), "2024-02-03 04:05:06")
    assertEquals(InfoUtil.datetimeFormattedForFilenames(fixed), "20240203-040506")
    val now = LocalDateTime.now()
    assertEquals(InfoUtil.datetimeFormattedForHumans(now), f"${now.getHour}%02d:${now.getMinute}%02d")
    val previousYear = now.minusYears(1).withMonth(2).withDayOfMonth(3).withHour(4).withMinute(5)
    assertEquals(InfoUtil.datetimeFormattedForHumans(previousYear), f"04:05 (03.02.${previousYear.getYear % 100}%02d)")
    val otherDay = now.withMonth(if (now.getMonthValue == 1) 2 else 1).withDayOfMonth(3).withHour(4).withMinute(5)
    assertEquals(InfoUtil.datetimeFormattedForHumans(otherDay), f"04:05 (03.${otherDay.getMonthValue}%02d)")
  }
}
