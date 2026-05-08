package it.evadid.core.util.io

import upickle.ReadWriter
import upickle.default.*

trait TypeConverter[I, O] {
  def convertToO(in: I): O

  def convertToI(in: O): I
}

object TypeConverter {
  
  
}
