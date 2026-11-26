package it.evadid.core.datastructures.uml

final case class UmlClass(sourceKey: it.evadid.vm.naming.BeEntityName, name: String, code: String,
    isAbstract: Boolean, attributes: Seq[UmlMember], functions: Seq[UmlMember], origin: UmlClassOrigin,
    isInterface: Boolean = false) {
  def key: UmlEntityKey = UmlEntityKey(name, None)
  def members: Seq[UmlMember] = attributes ++ functions
  def entity: UmlSelectedEntity = UmlSelectedEntity(UmlEntityKind.Class, name, code)
}
