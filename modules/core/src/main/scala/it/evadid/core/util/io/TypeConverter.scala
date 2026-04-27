package it.evadid.core.util.io

trait TypeConverter[I, O] {
  def convertToO(in: I): O

  def convertToI(in: O): I
}
