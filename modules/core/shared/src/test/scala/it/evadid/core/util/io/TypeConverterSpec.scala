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

  test("bulk conversion handles one-shot iterators and deduplicates only its result sets") {
    val input = Iterator("1", "01", "bad", "bad", "2")
    val result = converter.tryConvertAllToO(input)
    assertEquals(result.inputAfterOperation, Set("bad"))
    assertEquals(result.outputAfterOpteration, Set(1, 2))
    assert(!input.hasNext)

    val reverse = Iterator(1, 1, -1, -1, 2)
    assertEquals(converter.tryConvertAllToI(reverse),
      TypeConverter.ConverterResult(Set("1", "2"), Set(-1)))
    assert(!reverse.hasNext)
    assertEquals(converter.tryConvertAllToI(Iterator.empty),
      TypeConverter.ConverterResult(Set.empty[String], Set.empty[Int]))
  }

  test("reverse bulk conversion also propagates fatal errors") {
    val fatal = new TypeConverter[String, Int] {
      def convertToO(input: String): Int = input.toInt
      def convertToI(input: Int): String = throw new LinkageError("fatal reverse")
    }
    val error = try { fatal.tryConvertAllToI(Iterator(1)); None }
      catch { case e: LinkageError => Some(e) }
    assertEquals(error.map(_.getMessage), Some("fatal reverse"))
  }

}
