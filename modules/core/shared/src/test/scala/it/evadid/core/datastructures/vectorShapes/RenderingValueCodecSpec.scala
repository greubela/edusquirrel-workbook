package it.evadid.core.datastructures.vectorShapes

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.helper.{AlignmentInParent, RenderingDimension}
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent.*
import munit.FunSuite
import upickle.default.*

class RenderingValueCodecSpec extends FunSuite {
  private def roundTrip[T: ReadWriter](value: T): T = {
    val json = read[T](write(value))
    assertEquals(json, value)
    assertEquals(readBinary[T](writeBinary(value)), value)
    json
  }

  test("all alignments round trip as their canonical singleton") {
    val alignments = List(DistortionAlignment, TopLeft, TopCenter, TopRight,
      MiddleLeft, MiddleCenter, MiddleRight, BottomLeft, BottomCenter, BottomRight)
    alignments.foreach { alignment =>
      assert(roundTrip[AlignmentInParent](alignment) eq alignment)
    }
    val constructed: AlignmentInParent = new PositionInParent(VerticalAlignment.Top, HorizontalAlignment.Left)
    assertEquals(read[AlignmentInParent](write(constructed)), TopLeft)
    assertEquals(readBinary[AlignmentInParent](writeBinary(constructed)), TopLeft)
  }
  test("alignment wire names are stable and unknown names fail") {
    assertEquals(write[AlignmentInParent](BottomRight), "\"BottomRight\"")
    assertEquals(read[AlignmentInParent]("\"TopLeft\""), TopLeft)
    intercept[Exception](read[AlignmentInParent]("\"sideways\""))
  }
  test("horizontal and vertical alignment enums round trip") {
    HorizontalAlignment.values.foreach(roundTrip(_))
    VerticalAlignment.values.foreach(roundTrip(_))
  }
  test("rendering dimensions retain fractional geometry and padding") {
    val value = RenderingDimension(Dimension(1.25, 2.5), Dimension(3.75, 6.0))
    val restored = roundTrip(value)
    assertEquals(restored.fullDimension.decreaseSize(restored.rawDimension), Dimension(2.5, 3.5))
  }
  test("rendering dimensions support non-Double fractional types") {
    roundTrip(RenderingDimension(Dimension(BigDecimal("1.25"), BigDecimal("2.5")),
      Dimension(BigDecimal("4.25"), BigDecimal("6.5"))))
  }
  test("deserializing dimensions enforces non-negative dimensions") {
    intercept[Exception](read[RenderingDimension[Double]](
      """[[-1,2],[3,4]]"""))
  }
  test("deserializing dimensions enforces full-size containment") {
    intercept[Exception](read[RenderingDimension[Double]](
      """[[3,2],[1,4]]"""))
  }
}
