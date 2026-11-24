package it.evadid.core.datastructures.uml

import upickle.default.ReadWriter

/** Where a class box comes from. Only the program's own classes can be edited; library classes are provided by
  * the environment, and unknown classes are what the program uses without anything defining it.
  */
enum UmlClassOrigin derives ReadWriter {
  case Program, Library, Unknown

  def editable: Boolean = this == Program
}
