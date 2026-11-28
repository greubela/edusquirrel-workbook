package it.evadid.vm.structure

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.core.datastructures.uml.*
import it.evadid.vm.code.defining.{BeDependency, BeUnknownDependency}
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.parsing.source.{JavaClassStructured, SourceParsing}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.interactions.programming.state.ProgrammingStateJavaStructured

/** Java classes in the language-independent UML model, from the declarations of [[SourceParsing]]'s first step. */
object JavaUmlDiagram {
  /** The classes of `state`. A verbatim library definition (the generated `Turtle`) is a read-only library box;
    * an edited copy is the program's own class.
    */
  def apply(state: ProgrammingStateJavaStructured): UmlClassDiagram =
    UmlClassDiagram(state.definedClasses.toSeq.map((sourceKey, source) => umlClass(sourceKey, source, origin = None)),
      state.definedClasses.toSeq.flatMap { (_, code) =>
        SourceParsing.parse(code, SourceLanguage.Java).structured.programClasses.flatMap { owner =>
          val parents = owner.extendsNames.map(UmlRelation(owner.name, _, UmlRelation.Kind.Inheritance)) ++
            owner.implementsNames.map(UmlRelation(owner.name, _, UmlRelation.Kind.Realization))
          val associations = owner.fields.flatMap(field => field.typeNames.distinct.filterNot(_ == owner.name).map(target =>
            UmlRelation(owner.name, target, UmlRelation.Kind.Association, Some(field.name),
              field.javaType.contains("[") || field.typeNames.exists(Set("List", "Set", "Map", "Collection", "Queue", "Deque")))))
          parents ++ associations
        }
      }.distinct)

  /** Read-only boxes of what a program uses without defining it ([[ExternalUmlClasses]]): the classes of the
    * libraries it calls, in their Java form, and of its unknown dependency. `java.lang`'s `System` is always known,
    * so an unknown `System.out2` is an implied member of its library box.
    */
  def external(libraries: Seq[BeDependency], unknown: Option[BeUnknownDependency]): UmlClassDiagram = {
    val called = libraries.flatMap(dependency => dependency.definition(Java).orElse(javaLibraryViews.get(dependency.structureName)))
    val shown = called.distinct.flatMap(classesOf(_, UmlClassOrigin.Library))
    val implied = unknown.toSeq.flatMap(dependency => dependency.rootClasses ++ dependency.memberClasses).map(_.name.spelling()).toSet
    val java = javaLibraryViews.values.toSeq.flatMap(classesOf(_, UmlClassOrigin.Library)).filter(value => implied(value.name))
    ExternalUmlClasses(shown ++ java, unknown)
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
      keyed(UmlEntityKind.Function, functions), origin.getOrElse(if owner.library then UmlClassOrigin.Library else UmlClassOrigin.Program), owner.isInterface)
  }

  private def visibility(modifiers: Seq[String]): UmlVisibility =
    if modifiers.contains("public") then UmlVisibility.Public
    else if modifiers.contains("private") then UmlVisibility.Private
    else if modifiers.contains("protected") then UmlVisibility.Protected
    else UmlVisibility.Package

  private def typeSuffix(javaType: String): String = if javaType.isEmpty then "" else s": $javaType"
}
