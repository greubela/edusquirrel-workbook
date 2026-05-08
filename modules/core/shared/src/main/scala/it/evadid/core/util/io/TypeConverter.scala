package it.evadid.core.util.io

import upickle.ReadWriter
import upickle.default.*

trait TypeConverter[I, O] {
  def convertToO(in: I): O

  def convertToI(in: O): I
}

object TypeConverter {
  
  lazy val singleValueMap: TypeConverter[Map[String, String], String] = new TypeConverter[Map[String, String], String] {
    def convertToO(in: Map[String, String]): String = in.values.head
    def convertToI(in: String): Map[String, String] = Map("singleValue" -> in)
  }


}
