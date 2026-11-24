package it.evadid.vm.conversion.dependencies

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable, BeUnknownDependency}
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.BeDataType

/** Finds the calls a program makes into code that neither the program nor a configured dependency defines,
  * and derives the [[BeUnknownDependency]] that would let the program compile.
  *
  * Parsers bind such a call to a placeholder function named after its written path (`System.out2.println`):
  * a plain function without an intrinsic. A program definition of the same name is the user's own function.
  */
object UnknownDependencies {
  /** A call of the written path `path` (e.g. `Seq("System", "out2", "println")`) with `arity` arguments. */
  final case class UnknownCall(path: Seq[String], arity: Int)

  def calls(expression: BeExpression): Seq[UnknownCall] = {
    val nodes = expression.subtree
    val defined = nodes.collect { case definition: BeDefineFunction => definition.functionTypeInfo.isNamed }
      .flatten.map(_.universalInterpretation()).toSet
    nodes.collect {
      case call: BeFunctionCall if call.funcDef.intrinsic.isEmpty &&
          call.funcDef.functionTypeInfo.funcType == BeDefineFunction.Function() &&
          call.funcDef.functionTypeInfo.isNamed.exists(name => !defined(name.universalInterpretation())) =>
        UnknownCall(call.funcDef.functionTypeInfo.displayName.spelling().split('.').toSeq, call.arguments.size)
    }.distinct
  }

  def of(expression: BeExpression): Option[BeUnknownDependency] = from(calls(expression))

  def from(calls: Seq[UnknownCall]): Option[BeUnknownDependency] = Option.when(calls.nonEmpty) {
    val (free, qualified) = calls.partition(_.path.size == 1)
    // Each owner path (`System`, `System.out2`) becomes a class; its attributes lead to the next owner.
    val owners = qualified.flatMap(call => (1 until call.path.size).map(call.path.take)).distinct
    def owner(path: Seq[String]): BeDefineClass = {
      val signature = BeDefineClass(className(path), Nil, Nil)
      val attributes = owners.filter(other => other.size == path.size + 1 && other.startsWith(path)).map(other =>
        BeDefineVariable(BeEntityName.fromCodeString(other.last), BeDataType.ClassType(className(other))))
      val methods = qualified.filter(_.path.init == path).map(call =>
        function(call, BeDefineFunction.BeFunctionTypeInfo(Some(signature), Some(BeEntityName.fromCodeString(call.path.last)),
          if path.size == 1 then BeDefineFunction.StaticMethod() else BeDefineFunction.Method())))
      signature.copy(attributes = attributes.toList, methods = methods.toList)
    }
    BeUnknownDependency(owners.filter(_.size == 1).map(owner).toList, owners.filter(_.size > 1).map(owner).toList,
      free.map(call => function(call, BeDefineFunction.functionInfo(BeEntityName.fromCodeString(call.path.head)))).toList)
  }

  private def className(path: Seq[String]): BeEntityName =
    BeEntityName.fromLiteral(if path.size == 1 then path.head else path.map(_.capitalize).mkString)

  private def function(call: UnknownCall, info: BeDefineFunction.BeFunctionTypeInfo): BeDefineFunction =
    BeDefineFunction(List.tabulate(call.arity)(index => BeDefineVariable(BeEntityName.fromLiteral(s"arg$index"), BeDataType.AnyType)),
      Some(BeDefineVariable(BeEntityName.fromLiteral("result"), BeDataType.AnyType)), BeSequence.optionalBody(Nil), info)

  /** Java stub classes for `dependency`. Every method accepts any arguments, and its result fits any type. */
  def javaClasses(dependency: BeUnknownDependency): String = {
    def classSource(definition: BeDefineClass, static: Boolean) = {
      val attributes = definition.attributes.map { attribute =>
        val typeName = attribute.variableType match {
          case BeDataType.ClassType(owner) => owner.spelling()
          case _ => "Object"
        }
        s"    ${modifier(static)}$typeName ${attribute.name.spelling()} = new $typeName();"
      }
      val retained = if static then javaLangMembers.getOrElse(definition.name.spelling(), Nil) else Nil
      (s"class ${definition.name.spelling()} {" +: (retained ++ attributes ++ distinctNames(definition.methods).map(javaMethod(_, static))) :+ "}")
        .mkString("\n")
    }
    (dependency.rootClasses.map(classSource(_, static = true)) ++ dependency.memberClasses.map(classSource(_, static = false))).mkString("\n\n")
  }

  /** Java has no free functions: the stubs of unqualified calls are static members of the calling class. */
  def javaFunctions(dependency: BeUnknownDependency): Seq[String] = distinctNames(dependency.definedFunctions).map(javaMethod(_, static = true))

  /** A stub named like a `java.lang` class shadows it; `System` keeps the streams that system output prints to. */
  private val javaLangMembers: Map[String, Seq[String]] = Map("System" -> Seq(
    "    static java.io.PrintStream out = java.lang.System.out;", "    static java.io.PrintStream err = java.lang.System.err;"))

  /** Variadic stubs accept every arity, so one definition per name suffices. */
  private def distinctNames(functions: List[BeDefineFunction]): List[BeDefineFunction] =
    functions.distinctBy(_.functionTypeInfo.displayName.spelling())

  /** Python stubs for `dependency`; a class follows the classes its attributes instantiate. */
  def pythonDefinitions(dependency: BeUnknownDependency): String = {
    def function(definition: BeDefineFunction, indent: String, static: Boolean) =
      (if static && indent.nonEmpty then s"$indent@staticmethod\n" else "") +
        s"${indent}def ${definition.functionTypeInfo.displayName.spelling()}(${if static then "" else "self, "}*arguments):\n$indent    return None"
    def classSource(definition: BeDefineClass, static: Boolean) = {
      val attributes = definition.attributes.map { attribute =>
        val typeName = attribute.variableType match {
          case BeDataType.ClassType(owner) => owner.spelling()
          case _ => "object"
        }
        s"    ${attribute.name.spelling()} = $typeName()"
      }
      val members = attributes ++ distinctNames(definition.methods).map(function(_, "    ", static))
      (s"class ${definition.name.spelling()}:" +: (if members.isEmpty then Seq("    pass") else members)).mkString("\n")
    }
    // Owners are collected outside-in, so the reversed member classes list inner classes first.
    (dependency.memberClasses.reverse.map(classSource(_, static = false)) ++ dependency.rootClasses.map(classSource(_, static = true)) ++
      distinctNames(dependency.definedFunctions).map(function(_, "", static = true))).mkString("\n\n")
  }

  private def modifier(static: Boolean): String = if static then "static " else ""

  private def javaMethod(definition: BeDefineFunction, static: Boolean): String =
    s"    ${modifier(static)}<T> T ${definition.functionTypeInfo.displayName.spelling()}(Object... arguments) { return null; }"
}
