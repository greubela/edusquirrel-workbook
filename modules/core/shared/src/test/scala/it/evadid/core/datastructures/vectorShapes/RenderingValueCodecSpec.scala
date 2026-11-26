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
}
