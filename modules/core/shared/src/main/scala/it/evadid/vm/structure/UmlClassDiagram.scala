package it.evadid.vm.structure

import it.evadid.vm.structure.UmlClassDiagram.*
import it.evadid.vm.naming.BeEntityName

/** Language-independent class diagram. Source fragments keep selections useful even when a language's
  * executable VM subset cannot represent a member yet; parsing belongs to language-specific builders.
  */
final case class UmlClassDiagram(classes: Seq[UmlClassDiagram.UmlClass]) derives upickle.default.ReadWriter {
  def entity(key: UmlClassDiagram.EntityKey): Option[SelectedUmlEntity] =
    classes.find(_.name == key.className).flatMap(umlClass =>
      key.member.fold(Some(umlClass.entity))(member => umlClass.members.find(_.key.member.contains(member)).map(_.entity)))

  /** The structured-state entry of the class of `key`. */
  def sourceKey(key: UmlClassDiagram.EntityKey): Option[BeEntityName] = classes.find(_.name == key.className).map(_.sourceKey)

  /** The entry of the class stored under `sourceKey`, or of its function declared by `functionCode`. */
  def key(sourceKey: BeEntityName, functionCode: Option[String] = None): Option[UmlClassDiagram.EntityKey] =
    classes.find(_.sourceKey == sourceKey).flatMap(umlClass => functionCode.fold(Some(umlClass.key))(code =>
      umlClass.functions.find(_.code.trim == code.trim).map(_.key)))
}

object UmlClassDiagram {
  sealed trait SelectedUmlEntity derives upickle.default.ReadWriter {
    def name: String
    def fullCode: String
  }
  case class SelectedClass(name: String, fullCode: String) extends SelectedUmlEntity derives upickle.default.ReadWriter
  case class SelectedFunction(name: String, fullCode: String) extends SelectedUmlEntity derives upickle.default.ReadWriter
  case class SelectedAttribute(name: String, fullCode: String) extends SelectedUmlEntity derives upickle.default.ReadWriter


  enum Visibility(val symbol: String) derives upickle.default.ReadWriter {
    case Public extends Visibility("+")
    case Private extends Visibility("-")
    case Protected extends Visibility("#")
    case Package extends Visibility("~")
  }

  enum MemberKind derives upickle.default.ReadWriter { case Attribute, Function }

  /** Identifies a box entry across source edits; `occurrence` separates overloads and repeated names. */
  final case class MemberKey(kind: MemberKind, name: String, occurrence: Int) derives upickle.default.ReadWriter
  final case class EntityKey(className: String, member: Option[MemberKey]) derives upickle.default.ReadWriter

  /** One attribute or function line, e.g. `+name: String` or `+train(hours: int): void`. */
  final case class UmlMember(key: EntityKey, label: String, visibility: Visibility, isStatic: Boolean, isAbstract: Boolean, code: String) derives upickle.default.ReadWriter {
    def kind: MemberKind = key.member.fold(MemberKind.Attribute)(_.kind)
    def name: String = key.member.fold("")(_.name)
    def entity: SelectedUmlEntity = kind match {
      case MemberKind.Attribute => SelectedAttribute(name, code)
      case MemberKind.Function => SelectedFunction(name, code)
    }
  }

  /** `sourceKey` is the class's entry in the structured state. */
  final case class UmlClass(sourceKey: BeEntityName, name: String, code: String, isAbstract: Boolean, attributes: Seq[UmlMember], functions: Seq[UmlMember]) derives upickle.default.ReadWriter {
    def key: EntityKey = EntityKey(name, None)
    def members: Seq[UmlMember] = attributes ++ functions
    def entity: SelectedUmlEntity = SelectedClass(name, code)
  }

}
