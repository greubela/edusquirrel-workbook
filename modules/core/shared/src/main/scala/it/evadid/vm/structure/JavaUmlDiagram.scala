package it.evadid.vm.structure

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.core.datastructures.uml.*
import it.evadid.vm.code.defining.{BeDefineClass, BeDependency, BeUnknownDependency}
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.parsing.source.{JavaClassStructured, SourceParsing}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.BeDataType
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured

/** Java classes in the language-independent UML model, from the declarations of [[SourceParsing]]'s first step. */
object JavaUmlDiagram {
  /** The classes of `state`. A verbatim library definition (the generated `Turtle`) is a read-only library box;
    * an edited copy is the program's own class.
    */
  def apply(state: ProgrammingStateJavaStructured): UmlClassDiagram =
    UmlClassDiagram(state.definedClasses.toSeq.map((sourceKey, source) => umlClass(sourceKey, source, origin = None)))

  /** Read-only boxes of what a program uses without defining it: the classes of the libraries it calls (in their
    * Java form) and of its unknown dependency. An unknown class named like a library class (`System` with an
    * unknown `out2`) shadows it in the stubs and keeps the library's members, so its box shows both.
    */
  def external(libraries: Seq[BeDependency], unknown: Option[BeUnknownDependency]): UmlClassDiagram = {
    val library = libraries.flatMap(dependency => dependency.definition(Java).orElse(javaLibraryViews.get(dependency.structureName)))
      .distinct.flatMap(classesOf(_, UmlClassOrigin.Library)).distinctBy(_.name)
    val known = library ++ javaLibraryViews.values.toSeq.flatMap(classesOf(_, UmlClassOrigin.Library))
    val unknownClasses = unknown.toSeq.flatMap(dependency =>
      dependency.rootClasses.map(unknownClass(_, static = true)) ++ dependency.memberClasses.map(unknownClass(_, static = false)))
      .map(umlClass => known.find(_.name == umlClass.name).fold(umlClass)(shadowed => umlClass.copy(
        attributes = shadowed.attributes ++ umlClass.attributes.filterNot(member => shadowed.attributes.exists(_.name == member.name)),
        functions = shadowed.functions ++ umlClass.functions)))
    UmlClassDiagram(library.filterNot(value => unknownClasses.exists(_.name == value.name)) ++ unknownClasses)
  }

  /** Java forms of library classes that `java.lang` provides, so no definition is printed for them. */
  private val javaLibraryViews: Map[BeEntityName, String] = Map(KnownDependencies.system.structureName ->
    """public final class System {
      |    public static PrintStream out;
      |    public static PrintStream err;
      |}
      |public class PrintStream {
      |    public void print(Object value) { }
      |    public void println(Object value) { }
      |}""".stripMargin)

  private def classesOf(source: String, origin: UmlClassOrigin): Seq[UmlClass] =
    SourceParsing.parse(source, SourceLanguage.Java).structured.classes.map(owner => umlClass(BeEntityName.fromCodeString(owner.name), owner, Some(origin)))

  /** Unknown members have no known types: attributes show the class they lead to, methods their arguments. */
  private def unknownClass(definition: BeDefineClass, static: Boolean): UmlClass = {
    val name = definition.name.spelling()
    def member(kind: UmlEntityKind, memberName: String, label: String) =
      UmlMember(UmlEntityKey(name, Some(UmlMemberKey(kind, memberName, 0))), s"${UmlVisibility.Public.symbol}$label",
        UmlVisibility.Public, static, isAbstract = false, code = "")
    val attributes = definition.attributes.map { attribute =>
      val owner = attribute.variableType match {
        case BeDataType.ClassType(owner) => typeSuffix(owner.spelling())
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

  /** The box of one class source; its `fullCode` is the declaration without the preamble of imports. Without an
    * `origin`, a verbatim library definition is a library class and any other source the program's own.
    */
  private def umlClass(sourceKey: BeEntityName, source: String, origin: Option[UmlClassOrigin]): UmlClass =
    SourceParsing.parse(source, SourceLanguage.Java).structured.classes.headOption.fold(
      UmlClass(sourceKey, sourceKey.universalInterpretation(), source.trim, isAbstract = false, Nil, Nil, origin.getOrElse(UmlClassOrigin.Program)))(
      owner => umlClass(sourceKey, owner, origin))

  /** The box of a step-1 class ([[SourceParsing]]): its members in source order, overloads numbered by occurrence. */
  private def umlClass(sourceKey: BeEntityName, owner: JavaClassStructured, origin: Option[UmlClassOrigin]): UmlClass = {
    val name = owner.name
    def member(kind: UmlEntityKind, memberName: String, label: String, modifiers: Seq[String], code: String) = UmlMember(
      UmlEntityKey(name, Some(UmlMemberKey(kind, memberName, 0))), s"${visibility(modifiers).symbol}$label", visibility(modifiers),
      modifiers.contains("static"), modifiers.contains("abstract"), code)
    def keyed(kind: UmlEntityKind, members: Seq[UmlMember]): Seq[UmlMember] = members.foldLeft(Vector.empty[UmlMember]) { (done, member) =>
      val occurrence = done.count(_.name == member.name)
      done :+ member.copy(key = UmlEntityKey(name, Some(UmlMemberKey(kind, member.name, occurrence))))
    }
    val attributes = owner.fields.map(field =>
      member(UmlEntityKind.Attribute, field.name, s"${field.name}${typeSuffix(field.javaType)}", field.modifiers, field.code))
    val functions = owner.functions.map { function =>
      val parameters = function.parameters.map(parameter => s"${parameter.name}: ${parameter.javaType}").mkString(", ")
      member(UmlEntityKind.Function, function.name, s"${function.name}($parameters)${typeSuffix(function.returnType.getOrElse(""))}",
        function.modifiers, function.code)
    }
    UmlClass(sourceKey, name, owner.code.trim, owner.isAbstract, keyed(UmlEntityKind.Attribute, attributes),
      keyed(UmlEntityKind.Function, functions), origin.getOrElse(if owner.library then UmlClassOrigin.Library else UmlClassOrigin.Program))
  }

  private def visibility(modifiers: Seq[String]): UmlVisibility =
    if modifiers.contains("public") then UmlVisibility.Public
    else if modifiers.contains("private") then UmlVisibility.Private
    else if modifiers.contains("protected") then UmlVisibility.Protected
    else UmlVisibility.Package

  private def typeSuffix(javaType: String): String = if javaType.isEmpty then "" else s": $javaType"
}
