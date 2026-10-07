package it.evadid.core.datastructures.geometry

import munit.FunSuite

class GeometryIntegrationTest extends FunSuite {
  test("bounds creation from points and center") {
    val p1 = Point[Double](1.0, 2.0)
    val p2 = Point[Double](4.0, 6.0)
    val b = Bounds.fromPoints(p1, p2)

    assertEquals(b.areaString, "3.0 x 4.0")
    assertEquals(b.centerPoint, Point[Double](2.5, 4.0))
    assertEquals(Bounds.fromCenter(Point[Double](3.0, 4.0), Dimension[Double](2.0, 2.0)).startPoint, Point[Double](2.0, 3.0))
  }
}
