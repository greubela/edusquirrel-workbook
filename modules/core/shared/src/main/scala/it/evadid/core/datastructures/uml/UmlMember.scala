package it.evadid.core.datastructures.uml

/** One attribute or function of a class box. An `implied` member is used by the program but defined nowhere. */
final case class UmlMember(key: UmlEntityKey, label: String, visibility: UmlVisibility, isStatic: Boolean, isAbstract: Boolean, code: String,
    implied: Boolean = false) {
  def kind: UmlEntityKind = key.member.fold(UmlEntityKind.Attribute)(_.kind)
  def name: String = key.member.fold("")(_.name)
  def entity: UmlSelectedEntity = UmlSelectedEntity(kind, name, code)
}
