package it.evadid.core.util.io

import it.evadid.distribution.command.SerializedException
import munit.FunSuite
import upickle.default.*

class TypeConverterSpec extends FunSuite {
  private val converter = new TypeConverter[String, Int] {
    def convertToO(input: String): Int = if (input == "serialized") throw SerializedException("invalid") else input.toInt
    def convertToI(input: Int): String = if (input < 0) throw SerializedException("negative") else input.toString
  }
  test("bulk conversion partitions serialized errors and ordinary errors without losing valid entries") {
    val result = converter.tryConvertAllToO(Iterator("1", "invalid", "serialized", "2"))
    assertEquals(result.inputAfterOperation, Set("invalid", "serialized"))
    assertEquals(result.outputAfterOpteration, Set(1, 2))
    assertEquals(read[TypeConverter.ConverterResult[String, Int]](write(result)), result)
  }
  test("reverse conversion partitions failures and empty inputs") {
    val result = converter.tryConvertAllToI(List(1, -1, 2))
    assertEquals(result.inputAfterOperation, Set("1", "2"))
    assertEquals(result.outputAfterOpteration, Set(-1))
    assertEquals(converter.tryConvertAllToO(Nil), TypeConverter.ConverterResult(Set.empty[String], Set.empty[Int]))
  }
  test("bulk conversion propagates fatal errors") {
    val fatal = new TypeConverter[String, Int] {
      def convertToO(input: String): Int = throw new LinkageError("fatal")
      def convertToI(input: Int): String = input.toString
    }
    val error = try { fatal.tryConvertAllToO(List("1")); None } catch { case e: LinkageError => Some(e) }
    assertEquals(error.map(_.getMessage), Some("fatal"))
  }
  test("bulk conversion evaluates duplicates in input order before deduplicating results") {
    val seen = scala.collection.mutable.ListBuffer.empty[String]
    val tracked = new TypeConverter[String, Int] {
      def convertToO(input: String): Int = { seen += input; input.toInt }
      def convertToI(output: Int): String = output.toString
    }
    val input = Iterator("1", "bad", "1", "2")
    val result = tracked.tryConvertAllToO(input)
    assertEquals(seen.toList, List("1", "bad", "1", "2"))
    assert(!input.hasNext)
    assertEquals(result, TypeConverter.ConverterResult(Set("bad"), Set(1, 2)))
  }

  test("deduplication errors propagate after conversions rather than becoming conversion failures") {
    val error = new IllegalStateException("hashing failed")
    val seen = scala.collection.mutable.ListBuffer.empty[Int]
    class HashValue(val index: Int) { override def hashCode(): Int = throw error }
    val tracked = new TypeConverter[Int, HashValue] {
      def convertToO(input: Int): HashValue = { seen += input; new HashValue(input) }
      def convertToI(output: HashValue): Int = output.index
    }
    assert(intercept[IllegalStateException](tracked.tryConvertAllToO(0 until 5)) eq error)
    assertEquals(seen.toList, List(0, 1, 2, 3, 4))
  }

  test("reversed and tuple converters preserve direction and report the failing tuple side") {
    assertEquals(converter.reversed.convertToO(5), "5")
    assertEquals(converter.reversed.convertToI("6"), 6)
    val pair = TypeConverter.tupleConverter(converter, converter)
    assertEquals(pair.convertToO(("7", "8")), (7, 8))
    assertEquals(pair.convertToI((1, 2)), ("1", "2"))
    assert(intercept[SerializedException](pair.convertToO(("bad", "1"))).msg.contains("first"))
    assert(intercept[SerializedException](pair.convertToO(("1", "serialized"))).msg.contains("second"))
    assert(intercept[SerializedException](pair.convertToO(("bad", "serialized"))).msg.contains("neither"))
  }

}
