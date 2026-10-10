package it.evadid.evacuation.core.io.instances.binary

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import munit.FunSuite

class BinaryConvertersSpec extends FunSuite {
  private val samples = List(Array.emptyByteArray, Array[Byte](0), Array[Byte](-1),
    Array[Byte](0, -128, 127, -1, 1, 3), (0 until 256).map(_.toByte).toArray,
    Array.fill[Byte](600)(-42))
  test("run-length coding preserves every byte and splits runs at 255") {
    val codec = new RunLengthConverter
    samples.foreach(v => assertEquals(codec.reconstruct(codec.convert(v)).toList, v.toList))
    assertEquals(codec.convert(Array.fill[Byte](256)(7)).toList, List[Byte](-1, 7, 1, 7))
    intercept[AssertionError](codec.reconstruct(Array[Byte](2)))
  }
  test("bit-plane coding is a bijection for empty, short, signed and mixed data") {
    val codec = new BitplaneConverter
    samples.foreach(v => assertEquals(codec.reconstruct(codec.convert(v)).toList, v.toList))
    assertEquals(codec.convert(Array[Byte](-128, 0)).toList, List[Byte](-128, 0))
    val random = new scala.util.Random(42)
    for (size <- 0 to 40) {
      val bytes = Array.fill[Byte](size)(random.nextInt(256).toByte)
      assertEquals(codec.reconstruct(codec.convert(bytes)).toList, bytes.toList, s"length $size")
    }
  }
  test("byte-plane coding preserves remainder groups and steps larger than the input") {
    for (step <- List(1, 2, 3, 4, 12); bytes <- samples) {
      val codec = BytePlaneConverter(step)
      assertEquals(codec.reconstruct(codec.convert(bytes)).toList, bytes.toList)
    }
    intercept[IllegalArgumentException](BytePlaneConverter(0))
  }
  test("length-prefixed bits preserve mixed lengths and empty elements") {
    val values = List(BitSequence(List(true)), BitSequence.empty, BitSequence(List(false, true, false)))
    for (width <- List(2, 8, 31, 32)) {
      val codec = SizeContentIO(width)
      assertEquals(codec.decode(codec.encode(values)), values)
      assertEquals(codec.decode(codec.encode(Nil)), Nil)
      assertEquals(codec.decode(codec.encode(List(BitSequence.empty))), List(BitSequence.empty))
    }
  }
  test("length-prefixed bits reject invalid widths, overflow and truncated packets") {
    intercept[IllegalArgumentException](SizeContentIO(0))
    intercept[IllegalArgumentException](SizeContentIO(33))
    val codec = SizeContentIO(2)
    intercept[IllegalArgumentException](codec.encode(List(BitSequence(List.fill(4)(true)))))
    intercept[IllegalArgumentException](codec.decode(BitSequence(List(true))))
    intercept[IllegalArgumentException](codec.decode(BitSequence(List(true, true, false))))
  }
  test("the minimum encoder preserves payloads through every registered pipeline") {
    val codec = new MinimalEncoder
    for ((id, pipeline) <- codec.encodingShemes; input <- samples) {
      val encoded = pipeline.foldLeft(input)((v, c) => c.convert(v))
      assertEquals(codec.reconstruct(Array(id.toByte) ++ encoded).toList, input.toList, s"scheme $id")
    }
    samples.foreach(v => assertEquals(codec.reconstruct(codec.convert(v)).toList, v.toList))
  }
}
