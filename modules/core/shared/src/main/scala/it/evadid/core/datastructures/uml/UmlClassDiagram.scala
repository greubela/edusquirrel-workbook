package it.evadid.core.datastructures.uml

import it.evadid.vm.naming.BeEntityName

/** Language-independent class diagram. Source fragments keep selections useful even when a language's
  * executable VM subset cannot represent a member yet; parsing belongs to language-specific builders.
  */
final case class UmlClassDiagram(classes: Seq[UmlClass]) derives upickle.default.ReadWriter {
  def entity(key: UmlEntityKey): Option[UmlSelectedEntity] =
    classes.find(_.name == key.className).flatMap(umlClass =>
      key.member.fold(Some(umlClass.entity))(member => umlClass.members.find(_.key.member.contains(member)).map(_.entity)))

  /** The structured-state entry of the class of `key`. */
  def sourceKey(key: UmlEntityKey): Option[BeEntityName] = classes.find(_.name == key.className).map(_.sourceKey)

  /** The entry of the class stored under `sourceKey`, or of its function declared by `functionCode`. */
  def key(sourceKey: BeEntityName, functionCode: Option[String] = None): Option[UmlEntityKey] =
    classes.find(_.sourceKey == sourceKey).flatMap(umlClass => functionCode.fold(Some(umlClass.key))(code =>
      umlClass.functions.find(_.code.trim == code.trim).map(_.key)))
}
