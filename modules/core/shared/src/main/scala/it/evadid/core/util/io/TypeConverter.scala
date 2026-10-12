package it.evadid.core.util.io

import it.evadid.core.util.io.TypeConverter.ConverterResult
import upickle.ReadWriter
import upickle.default.*

import scala.collection.mutable
import scala.util.control.NonFatal

trait TypeConverter[I, O] {
  def convertToO(in: I): O

  def convertToI(in: O): I

  def tryConvertAllToO(obj: IterableOnce[I]): ConverterResult[I, O] = {
    val input = mutable.ListBuffer[I]()
    val output = mutable.ListBuffer[O]()
    obj.iterator.foreach((curObj: I) => try output += convertToO(curObj) catch case NonFatal(e) => input += curObj)
    ConverterResult(input.toSet, output.toSet)
  }

  def tryConvertAllToI(obj: IterableOnce[O]): ConverterResult[I, O] = {
    val input = mutable.ListBuffer[I]()
    val output = mutable.ListBuffer[O]()
    obj.iterator.foreach((curObj: O) => try input += convertToI(curObj) catch case NonFatal(e) => output += curObj)
    ConverterResult(input.toSet, output.toSet)
  }

}

object TypeConverter {


  case class ConverterResult[I, O](inputAfterOperation: Set[I], outputAfterOpteration: Set[O]) derives ReadWriter

  lazy val singleValueMap: TypeConverter[Map[String, String], String] = new TypeConverter[Map[String, String], String] {
    def convertToO(in: Map[String, String]): String = in.values.head
    def convertToI(in: String): Map[String, String] = Map("singleValue" -> in)
  }


}
