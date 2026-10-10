package it.evadid.evacuation.core.io.instances.eva

import munit.FunSuite
import it.evadid.evacuation.core.io.instances.basic.ByteFixedLengthIntIO

class PaddedByteSeqSpec extends FunSuite {
  test("byte arrays are left-padded to the maximum width") {
    val input = List(Array[Byte](1), Array[Byte](2, 3), Array.emptyByteArray)
    assertEquals(MinimalPaddedByteSeqEncoder.decode(MinimalPaddedByteSeqEncoder.encode(input)).map(_.toList),
      List(List[Byte](0, 1), List[Byte](2, 3), List[Byte](0, 0)))
  }
  test("empty collections and zero-width entries retain their counts") {
    for (input <- List(Nil, List(Array.emptyByteArray), List.fill(3)(Array.emptyByteArray)))
      assertEquals(MinimalPaddedByteSeqEncoder.decode(MinimalPaddedByteSeqEncoder.encode(input)).map(_.toList), input.map(_.toList))
  }
  test("the width header is unsigned up to 255 bytes") {
    for (size <- List(127, 128, 255)) {
      val input = List(Array.fill[Byte](size)(-1))
      assertEquals(MinimalPaddedByteSeqEncoder.decode(MinimalPaddedByteSeqEncoder.encode(input)).map(_.toList), input.map(_.toList))
    }
    intercept[IllegalArgumentException](MinimalPaddedByteSeqEncoder.encode(List(Array.fill[Byte](256)(0))))
  }
  test("truncated and inconsistent headers are rejected") {
    intercept[IllegalArgumentException](MinimalPaddedByteSeqEncoder.decode(Array[Byte](0, 0)))
    intercept[IllegalArgumentException](MinimalPaddedByteSeqEncoder.decode(ByteFixedLengthIntIO.encode(1) ++ Array[Byte](3, 1)))
    intercept[IllegalArgumentException](MinimalPaddedByteSeqEncoder.decode(ByteFixedLengthIntIO.encode(-1) ++ Array[Byte](0)))
  }
}
