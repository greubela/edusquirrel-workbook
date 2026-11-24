package it.evadid.core.datastructures.uml

import upickle.default.ReadWriter

final case class UmlMember(key: UmlEntityKey, label: String, visibility: UmlVisibility, isStatic: Boolean, isAbstract: Boolean, code: String) derives ReadWriter {
  def kind: UmlEntityKind = key.member.fold(UmlEntityKind.Attribute)(_.kind)
  def name: String = key.member.fold("")(_.name)
  def entity: UmlSelectedEntity = UmlSelectedEntity(kind, name, code)
}
