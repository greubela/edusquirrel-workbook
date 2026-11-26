package it.evadid.core.datastructures.uml

import upickle.default.ReadWriter

enum UmlVisibility(val symbol: String) derives ReadWriter {
  case Public extends UmlVisibility("+")
  case Private extends UmlVisibility("-")
  case Protected extends UmlVisibility("#")
  case Package extends UmlVisibility("~")
}
