package it.evadid.core.datastructures.uml

import upickle.default.ReadWriter

final case class UmlClass(sourceKey: it.evadid.vm.naming.BeEntityName, name: String, code: String,
    isAbstract: Boolean, attributes: Seq[UmlMember], functions: Seq[UmlMember], origin: UmlClassOrigin,
    isInterface: Boolean = false) derives ReadWriter {
  def key: UmlEntityKey = UmlEntityKey(name, None)
  def members: Seq[UmlMember] = attributes ++ functions
  def entity: UmlSelectedEntity = UmlSelectedEntity(UmlEntityKind.Class, name, code)
}
