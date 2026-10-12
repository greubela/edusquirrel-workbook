package it.evadid.evacuation.core.datastructures.seqs

import it.evadid.evacuation.core.utility.BinaryUtility
import munit.FunSuite

class BitSequenceSpec extends FunSuite {
  test("every bit of a Long survives conversion, including the sign bit") {
    for (position <- 0 until 64) {
      val value = 1L << position
      assertEquals(BitSequence(value).toLong, value, s"bit $position")
    }
    List(0L, -1L, Long.MinValue, Long.MaxValue, 0x123456789abcdefL).foreach(v => assertEquals(BitSequence(v).toLong, v))
  }
  test("all byte values retain signed and unsigned representations") {
    for (v <- 0 until 256) {
      val bits = BitSequence(v.toByte)
      assertEquals(bits.toByte, v.toByte)
      assertEquals(bits.toUByte, v)
      assertEquals(bits.ensureSize(8).size, 8)
    }
  }
  test("head and tail split at every boundary, including empty halves") {
    val bits = BitSequence(List(true, false, true, true))
    for (n <- 0 to bits.size) {
      assertEquals(bits.head(n).seq, bits.seq.take(n))
      assertEquals(bits.tail(n).seq, bits.seq.drop(n))
      assertEquals(bits.head(n).append(bits.tail(n)), bits)
    }
    assertEquals(bits.head(10), bits)
    assertEquals(BitSequence.empty.tail(0), BitSequence.empty)
  }
  test("resizing pads on the left or truncates the highest bits") {
    val bits = BitSequence(List(true, false))
    assertEquals(bits.ensureSize(5).seq, List(false, false, false, true, false))
    assertEquals(bits.ensureSize(4, true).seq, List(true, true, true, false))
    assertEquals(bits.ensureSize(1).seq, List(false))
    assertEquals(bits.ensureSize(0), BitSequence.empty)
    assertEquals(bits.padToInt().size, 32)
    assertEquals(BitSequence.fullInt(7).toInt, 7)
    for (width <- List(0, 1, 4, 8, 32, 64))
      assertEquals(BitSequence.paddedNumber(7, width).size, width)
    assertEquals(BitSequence.paddedNumber(7, 1).seq, List(true))
    assertEquals(BitSequence.paddedNumber(7, 4).seq, List(false, true, true, true))
  }
  test("integer header extraction leaves an empty tail for a header-only packet") {
    val packet = BitSequence.fullInt(5).append(BitSequence(List(true, false)))
    assertEquals(packet.headInt, 5)
    assertEquals(packet.tailInt.seq, List(true, false))
    assertEquals(BitSequence.fullInt(0).tailInt, BitSequence.empty)
  }
  test("prefixes, suffixes and least-significant-bit addressing agree") {
    val bits = BitSequence(List(true, false, false))
    assert(bits.hasPrefix(BitSequence(List(true, false))))
    assert(bits.hasPostfix(BitSequence(List(false, false))))
    assert(bits.hasPrefix(BitSequence.empty))
    assert(!bits.hasPrefix(bits.append(true)))
    assert(bits.isBitSet(2)); assert(!bits.isBitSet(0))
    assert(bits.isBitSet(-1, true)); assert(!bits.isBitSet(8))
  }
  test("zero trimming preserves the requested minimum and rejects invalid sizes") {
    assertEquals(BitSequence(List(false, false, true)).removeLeadingZeros().seq, List(true))
    assertEquals(BitSequence(List(false, false)).removeLeadingZeros().seq, List(false))
    assertEquals(BitSequence.empty.removeLeadingZeros(), BitSequence.empty)
    intercept[IllegalArgumentException](BitSequence(List(true)).head(-1))
    intercept[IllegalArgumentException](BitSequence(List(true)).tail(2))
    intercept[IllegalArgumentException](BitSequence(List(true)).ensureSize(-1))
  }
  test("binary helpers use the full width and reject invalid positions") {
    for (bit <- 0 until 64) {
      assertEquals(BinaryUtility.setBit(0L, bit), 1L << bit)
      assertEquals(BinaryUtility.flipBit(1L << bit, bit), 0L)
      assert(BinaryUtility.isBitSet(1L << bit, bit))
    }
    assert(BinaryUtility.isBitSet(Integer.valueOf(Int.MinValue), Integer.valueOf(31)))
    intercept[IllegalArgumentException](BinaryUtility.setBit(0L, 64))
    intercept[IllegalArgumentException](BinaryUtility.isBitSet(Integer.valueOf(1), Integer.valueOf(-1)))
  }
}
