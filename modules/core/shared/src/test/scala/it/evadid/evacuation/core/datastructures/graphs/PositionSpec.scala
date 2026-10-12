package it.evadid.evacuation.core.datastructures.graphs

import munit.FunSuite
import upickle.default.*

class PositionSpec extends FunSuite {
  test("distance uses full coordinate range without integer overflow") {
    assertEquals(Position(0, 0).distTo(Position(3, 4)), 5.0)
    assertEquals(Position(Int.MinValue, 0).distTo(Position(Int.MaxValue, 0)), 4294967295.0)
    val diagonal = Position(Int.MinValue, Int.MinValue).distTo(Position(Int.MaxValue, Int.MaxValue))
    assertEqualsDouble(diagonal, math.hypot(4294967295.0, 4294967295.0), 0.001)
  }
  test("interpolation and vectors round coordinates and retain endpoints") {
    assertEquals(Position(2, 4).addVector(Position(3, -2), 2), Position(8, 0))
    assertEquals(Position.between(Position(0, 0), Position(3, 5)), Position(2, 3))
    val start = Position(Int.MinValue, Int.MaxValue)
    val end = Position(Int.MaxValue, Int.MinValue)
    assertEquals(start.pointBetween(end, 0), start)
    assertEquals(start.pointBetween(end), end)
    assertEquals(Position.between(start, end, 0.5), Position(0, 0))
  }
  test("nearest positions preserve duplicates, sort by distance and limit to available entries") {
    val positions = List(Position(3, 4), Position(0, 1), Position(0, 1), Position(6, 8))
    assertEquals(Position.getNearestElements(positions, Position(0, 0), 10).map(_._2), List(1.0, 1.0, 5.0, 10.0))
    assertEquals(Position.getNearestElements(positions, Position(0, 0), 2).map(_._1), List.fill(2)(Position(0, 1)))
    assertEquals(Position.getNearestElement(positions, Position(0, 0)), Position(0, 1) -> 1.0)
  }
  test("nearest lookup handles empty collections and rejects a negative count") {
    assertEquals(Position.getNearestElements(List.empty[Position], Position(0, 0), 3), Nil)
    assertEquals(Position.getNearestElements(List(Position(0, 1)), Position(0, 0), 0), Nil)
    intercept[IllegalArgumentException](Position.getNearestElements(List.empty[Position], Position(0, 0), -1))
  }
  test("custom position extractors retain the original objects") {
    val data = List("a" -> Position(6, 8), "b" -> Position(0, 1))
    assertEquals(Position.getNearestElements(data, Position(0, 0), _._2, 1), List(data(1) -> 1.0))
  }
  test("default JSON and binary codecs preserve extreme coordinates") {
    for (pos <- List(Position(0, 0), Position(Int.MinValue, Int.MaxValue))) {
      assertEquals(read[Position](write(pos)), pos)
      assertEquals(readBinary[Position](writeBinary(pos)), pos)
    }
  }
}
