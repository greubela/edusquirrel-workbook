package it.evadid.core.datastructures.uml

final case class UmlRelation(from: String, to: String, kind: UmlRelation.Kind,
    role: Option[String] = None, many: Boolean = false)

object UmlRelation {
  enum Kind {
    case Inheritance, Realization, Association
  }
}
