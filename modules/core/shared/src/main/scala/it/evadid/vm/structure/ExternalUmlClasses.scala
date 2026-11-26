package it.evadid.vm.structure

import it.evadid.core.datastructures.uml.*
import it.evadid.vm.code.defining.{BeDefineClass, BeUnknownDependency}
import it.evadid.vm.types.BeDataType

/** Read-only boxes of what a program uses without defining it, independent of the source language.
  *
  * Recognized library classes are `Library` boxes. What the program implies but nothing defines (its unknown
  * dependency) is marked [[UmlMember.implied]]: a member of a library class joins that class's box, and any other
  * class becomes an `Unknown` box.
  */
object ExternalUmlClasses {
  def apply(library: Seq[UmlClass], unknown: Option[BeUnknownDependency]): UmlClassDiagram = {
    val implied = unknown.toSeq.flatMap(dependency =>
      dependency.rootClasses.map(unknownClass(_, static = true)) ++ dependency.memberClasses.map(unknownClass(_, static = false)))
    val boxes = library.distinctBy(_.name).map(known => implied.find(_.name == known.name).fold(known)(used => keyed(known.copy(
      attributes = known.attributes ++ used.attributes.filterNot(member => known.attributes.exists(_.name == member.name)),
      functions = known.functions ++ used.functions.filterNot(member => known.functions.exists(_.label == member.label))))))
    UmlClassDiagram(boxes ++ implied.filterNot(used => library.exists(_.name == used.name)))
  }

  /** Implied members have no known types: attributes show the class they lead to, functions their arguments. */
  private def unknownClass(definition: BeDefineClass, static: Boolean): UmlClass = {
    val name = definition.name.spelling()
    def member(kind: UmlEntityKind, memberName: String, label: String) =
      UmlMember(UmlEntityKey(name, Some(UmlMemberKey(kind, memberName, 0))), s"${UmlVisibility.Public.symbol}$label",
        UmlVisibility.Public, static, isAbstract = false, code = "", implied = true)
    val attributes = definition.attributes.map { attribute =>
      val owner = attribute.variableType match {
        case BeDataType.ClassType(owner) => s": ${owner.spelling()}"
        case _ => ""
      }
      member(UmlEntityKind.Attribute, attribute.name.spelling(), s"${attribute.name.spelling()}$owner")
    }
    val functions = definition.methods.distinctBy(_.functionTypeInfo.displayName.spelling()).map { method =>
      val methodName = method.functionTypeInfo.displayName.spelling()
      member(UmlEntityKind.Function, methodName, s"$methodName(${method.inputs.map(_.name.spelling()).mkString(", ")})")
    }
    UmlClass(definition.name, name, "", isAbstract = false, attributes, functions, UmlClassOrigin.Unknown)
  }

  /** Overloads numbered by occurrence, so a library function and an implied one of the same name keep their own keys. */
  private def keyed(umlClass: UmlClass): UmlClass = {
    def number(members: Seq[UmlMember]) = members.foldLeft(Vector.empty[UmlMember]) { (done, member) =>
      val occurrence = done.count(_.name == member.name)
      done :+ member.copy(key = UmlEntityKey(umlClass.name, member.key.member.map(_.copy(occurrence = occurrence))))
    }
    umlClass.copy(attributes = number(umlClass.attributes), functions = number(umlClass.functions))
  }
}
