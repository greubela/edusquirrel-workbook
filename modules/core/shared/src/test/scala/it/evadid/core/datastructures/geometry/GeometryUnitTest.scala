package it.evadid.core.datastructures.geometry

import munit.FunSuite

class GeometryUnitTest extends FunSuite {
  test("point and dimension operations") {
    val p1 = Point[Double](1.0, 2.0)
    val p2 = Point[Double](4.0, 6.0)
    assertEquals(p1.distanceToSquared(p2), 25.0)

    val dim = Dimension[Double](3.0, 4.0)
    assertEquals(dim.area, 12.0)
    assertEquals(dim.ensureAtLeastAsBigAs(5.0, 1.0), Dimension[Double](5.0, 4.0))
  }
}
