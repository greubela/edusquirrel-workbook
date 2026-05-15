package it.evadid.core.datastructures.geometry

import munit.FunSuite

class GeometryBranchCoverageTest extends FunSuite {
  test("dimension and bounds branch paths") {
    val d = Dimension[Double](1, 2)
    assertEquals(d.ensureWidth(5), Dimension[Double](5, 2))
    assertEquals(d.ensureHeight(6), Dimension[Double](1, 6))
    assertEquals(d.increaseSize(1, 1), Dimension[Double](2, 3))
    assertEquals(d.increaseSize(Dimension[Double](2, 3)), Dimension[Double](3, 5))
    assertEquals(d.decreaseSize(Dimension[Double](1, 1)), Dimension[Double](0, 1))
    assertEquals(d.asPoint, Point[Double](1, 2))

    val ensured = Bounds(Point[Double](0,0), Dimension[Double](1,1)).ensureAtLeastAsBigAs(Dimension[Double](2,3))
    assertEquals(ensured.dimension, Dimension[Double](2,3))

    val empty = Bounds.thatContainsAll[Double](Seq.empty)
    assertEquals(empty.startPoint, Point[Double](0,0))
    assertEquals(empty.dimension, Dimension[Double](0,0))
  }
}
