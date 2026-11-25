package it.evadid.core.datastructures.uml

import upickle.default.ReadWriter

final case class UmlRelation(from: String, to: String, kind: UmlRelation.Kind,
    role: Option[String] = None, many: Boolean = false) derives ReadWriter

object UmlRelation {
  enum Kind derives ReadWriter {
    case Inheritance, Realization, Association, Dependency
  }
}
