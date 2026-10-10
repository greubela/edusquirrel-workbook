package it.evadid.evacuation.core.utility

import munit.FunSuite

class UtilitySpec extends FunSuite {
  test("duration formatting retains zero minutes inside hours and full milliseconds") {
    assertEquals(GeneralUtility.formatDuration(0), "0 s")
    assertEquals(GeneralUtility.formatDuration(1250), "1.2 s")
    assertEquals(GeneralUtility.formatDuration(61000), "1:01 min")
    assertEquals(GeneralUtility.formatDuration(3601000), "1:00:01 h")
    assertEquals(GeneralUtility.formatDuration(86400000), "24:00:00 h")
    assertEquals(GeneralUtility.formatDuration(1003, false), "1.003 s")
    assertEquals(GeneralUtility.formatDuration(1050, false), "1.050 s")
  }
  test("cyclic lookup handles zero, negative indices and Long boundaries") {
    val values = List("a", "b", "c")
    for (index <- List(0L, 1L, -1L, -3L, Long.MinValue, Long.MaxValue)) {
      val expected = ((BigInt(index) % 3 + 3) % 3).toInt
      assertEquals(DataStructureHelper.getElementFromSeqSafelyMod(values, index), values(expected))
    }
    intercept[IllegalArgumentException](DataStructureHelper.getElementFromSeqSafelyMod(Nil, 0))
  }
  test("counter resets its zero-based sequence") {
    val counter = new Counter
    assertEquals(counter.getNext.intValue, 0)
    assertEquals(counter.getNext.intValue, 1)
    counter.reset(); assertEquals(counter.getNext.intValue, 0)
  }
  test("factory adapters return failures as values and preserve successful results") {
    val factory = GeneralUtility.factoryToEitherFactory((s: String) => s.toInt)
    assertEquals(factory("42"), Left(42))
    assert(factory("invalid").isRight)
  }
}
