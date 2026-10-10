package it.evadid.evacuation.core.io.instances.basic

import munit.FunSuite

class ByteCodecsSpec extends FunSuite {
  test("signed and unsigned integer codecs preserve boundaries and fixed widths") {
    for (v <- List(Int.MinValue, -1, 0, 1, 127, 128, 255, 256, 65535, 65536, Int.MaxValue)) {
      assertEquals(ByteIntIO.decode(ByteIntIO.encode(v)), v)
      assertEquals(ByteIndexIO.decode(ByteIndexIO.encode(v)), v)
      assertEquals(ByteFixedLengthIntIO.decode(ByteFixedLengthIntIO.encode(v)), v)
      assertEquals(ByteFixedLengthIntIO.encode(v).length, 4)
    }
    assertEquals(ByteIndexIO.encode(255).toList, List[Byte](-1))
    assertEquals(ByteFixedLengthIntIO.encode(0x12345678).toList, List[Byte](0x12, 0x34, 0x56, 0x78))
  }
  test("longs and shorts preserve extreme signed values") {
    List(Long.MinValue, Long.MaxValue, 0L, -1L, 1L << 40).foreach(v => assertEquals(ByteLongIO.decode(ByteLongIO.encode(v)), v))
    for (v <- List(Short.MinValue, Short.MaxValue, 0.toShort, (-1).toShort)) {
      assertEquals(ByteFixedLengthShortIO.decode(ByteFixedLengthShortIO.encode(v)), v)
      assertEquals(ByteFixedLengthShortIO.encode(v).length, 2)
    }
  }
  test("boolean wire values remain compatible and reject malformed packets") {
    assertEquals(ByteBooleanIO.encode(true).toList, List[Byte](0))
    assertEquals(ByteBooleanIO.encode(false).toList, List[Byte](1))
    List(true, false).foreach(v => assertEquals(ByteBooleanIO.decode(ByteBooleanIO.encode(v)), v))
    intercept[IllegalArgumentException](ByteBooleanIO.decode(Array.emptyByteArray))
    intercept[IllegalArgumentException](ByteBooleanIO.decode(Array[Byte](2)))
    intercept[IllegalArgumentException](ByteBooleanIO.decode(Array[Byte](0, 1)))
  }
  test("fixed-width and index codecs reject missing or extra bytes") {
    intercept[AssertionError](ByteFixedLengthIntIO.decode(Array[Byte](1)))
    intercept[AssertionError](ByteFixedLengthShortIO.decode(Array[Byte](1, 2, 3)))
    intercept[AssertionError](ByteIndexIO.decode(Array.emptyByteArray))
    intercept[AssertionError](ByteIndexIO.decode(Array.fill[Byte](5)(0)))
  }
}
