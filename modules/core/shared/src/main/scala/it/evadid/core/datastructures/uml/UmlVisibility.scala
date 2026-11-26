package it.evadid.core.datastructures.uml

enum UmlVisibility(val symbol: String) {
  case Public extends UmlVisibility("+")
  case Private extends UmlVisibility("-")
  case Protected extends UmlVisibility("#")
  case Package extends UmlVisibility("~")
}
