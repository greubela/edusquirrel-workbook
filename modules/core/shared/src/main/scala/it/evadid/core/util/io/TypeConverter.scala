package it.evadid.core.util.io

import it.evadid.core.util.io.TypeConverter.ConverterResult
import upickle.default.ReadWriter

import scala.util.Try

trait TypeConverter[I, O] {
  def convertToO(in: I): O

  def convertToI(in: O): I

  def tryConvertAllToO(obj: IterableOnce[I]): ConverterResult[I, O] =
    convertAll(obj)(convertToO)

  def tryConvertAllToI(obj: IterableOnce[O]): ConverterResult[I, O] = {
    val reversed = convertAll(obj)(convertToI)
    ConverterResult(reversed.outputAfterOpteration, reversed.inputAfterOperation)
  }

  private def convertAll[A, B](values: IterableOnce[A])(convert: A => B): ConverterResult[A, B] =
    values.iterator.foldLeft(ConverterResult(Set.empty[A], Set.empty[B])) { (result, value) =>
      Try(convert(value)).fold(
        _ => result.copy(inputAfterOperation = result.inputAfterOperation + value),
        converted => result.copy(outputAfterOpteration = result.outputAfterOpteration + converted)
      )
    }
}

object TypeConverter {
  case class ConverterResult[I, O](inputAfterOperation: Set[I], outputAfterOpteration: Set[O]) derives ReadWriter

  lazy val singleValueMap: TypeConverter[Map[String, String], String] = new TypeConverter[Map[String, String], String] {
    def convertToO(in: Map[String, String]): String = in.values.head
    def convertToI(in: String): Map[String, String] = Map("singleValue" -> in)
  }
}
