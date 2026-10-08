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
}
