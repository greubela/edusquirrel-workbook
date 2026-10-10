package it.evadid.evacuation.core.io.instances.bits

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import munit.FunSuite

class BitCodecsSpec extends FunSuite {
  test("padded coding adds equal-width elements and handles an empty list") {
    val input = List(BitSequence(1), BitSequence(7), BitSequence(0))
    assertEquals(PaddedIO.decode(PaddedIO.encode(input)), input.map(_.ensureSize(3)))
    assertEquals(PaddedIO.decode(PaddedIO.encode(Nil)), Nil)
  }
  test("padded coding rejects truncated headers and partial elements") {
    intercept[IllegalArgumentException](PaddedIO.decode(BitSequence(1)))
    intercept[IllegalArgumentException](PaddedIO.decode(BitSequence.fullInt(3).append(BitSequence(List(true)))))
  }
  test("repetition coding retains alternating values and long runs") {
    val a = BitSequence(1); val b = BitSequence(2)
    List(Nil, List(a), List(a, a, b, b, a), List.fill(300)(a)).foreach { input =>
      assertEquals(RepetitionConverter.reconstruct(RepetitionConverter.convert(input)), input)
    }
    intercept[AssertionError](RepetitionConverter.reconstruct(List(a)))
    intercept[IllegalArgumentException](RepetitionConverter.reconstruct(List(BitSequence(0), a)))
  }
  test("Huffman coding preserves empty, one-symbol and multiple-symbol alphabets") {
    for (input <- List("", "aaaaaaaa", "abacabad", "Grüße und 😀")) {
      val frequencies = input.toList.groupMapReduce(identity)(_ => 1)(_ + _)
      val codec = HuffmanIO(HuffmanIO.createEncodingMap(frequencies))
      assertEquals(codec.decode(codec.encode(input.toList)).mkString, input)
    }
  }
  test("Huffman coding uses short codes for frequent symbols and preserves signed bytes") {
    val codec = HuffmanIO(HuffmanIO.createEncodingMap(Map('a' -> 10, 'b' -> 2, 'c' -> 1)))
    assertEquals(codec.prefixMap('a').size, 1)
    assertEquals(codec.encode("aaaaaaaaaabbc".toList).size, 16)
    val bytes = (-128 to 127).map(_.toByte)
    val byteCodec = HuffmanIO(HuffmanIO.createEncodingMap(bytes.map(_ -> 1).toMap))
    assertEquals(byteCodec.decode(byteCodec.encode(bytes)), bytes)
  }
  test("Huffman rejects ambiguous maps, missing symbols and truncated codes") {
    intercept[IllegalArgumentException](HuffmanIO(Map('a' -> BitSequence.empty)))
    intercept[IllegalArgumentException](HuffmanIO(Map('a' -> BitSequence(List(false)), 'b' -> BitSequence(List(false, true)))))
    intercept[IllegalArgumentException](HuffmanIO.createEncodingMap(Map('a' -> 0)))
    val codec = HuffmanIO(Map('a' -> BitSequence(List(false)), 'b' -> BitSequence(List(true, false))))
    intercept[IllegalArgumentException](codec.decode(BitSequence(List(true))))
    intercept[IllegalArgumentException](codec.decode(BitSequence(List(true, true))))
    intercept[NoSuchElementException](codec.encode(List('x')))
  }
  test("large Huffman frequencies do not overflow when combined") {
    val codec = HuffmanIO(HuffmanIO.createEncodingMap(Map('a' -> Int.MaxValue, 'b' -> Int.MaxValue, 'c' -> 1)))
    assertEquals(codec.decode(codec.encode("abcabc".toList)).mkString, "abcabc")
  }
}
