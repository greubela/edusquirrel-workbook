package it.evadid.evacuation.core.io.traits.converter

import munit.FunSuite

class BijectionSpec extends FunSuite {
  test("seeded shuffles use zero-based indices and are reproducible") {
    for (size <- 0 to 20; seed <- List(0L, 42L, -1L)) {
      val input = (0 until size).toList
      val a = Bijection.randomShuffle[Int](seed, size)
      assertEquals(a.convert(input).sorted, input)
      assertEquals(a.reconstruct(a.convert(input)), input)
      assertEquals(a.convert(input), Bijection.randomShuffle[Int](seed, size).convert(input))
    }
  }
  test("composition applies the first conversion followed by the second") {
    val a = Bijection.fromShuffledIndexList[Int](List(1, 0, 2))
    val b = Bijection.fromShuffledIndexList[Int](List(0, 2, 1))
    val input = List(10, 20, 30)
    val combined = a.combineWith(b)
    assertEquals(combined.convert(input), b.convert(a.convert(input)))
    assertEquals(combined.reconstruct(combined.convert(input)), input)
  }
  test("plane permutations retain every index including remainder groups") {
    for (size <- 0 to 20; step <- 1 to 12)
      assertEquals(Bijection.planeList(step, size).sorted, (0 until size).toList)
  }
  test("invalid permutations and sizes are rejected at construction") {
    List(List(0, 0), List(1, 2), List(-1, 0)).foreach { indices =>
      intercept[IllegalArgumentException](Bijection.fromShuffledIndexList[Int](indices))
    }
    intercept[IllegalArgumentException](Bijection.planeList(0, 3))
    intercept[IllegalArgumentException](Bijection.randomShuffle[Int](1, -1))
  }
}
