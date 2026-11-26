package it.evadid.vm.conversion.dependencies

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable, BeUnknownDependency}
import it.evadid.vm.code.controlStructures.BeFor
import it.evadid.vm.code.errors.BeExpressionUnsupported
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.BeDataType

/** Finds the calls a program makes into code that neither the program nor a configured dependency defines,
  * and derives the [[BeUnknownDependency]] that would let the program compile.
  *
  * Parsers bind such a call to a placeholder function named after its written path (`System.out2.println`):
  * a plain function without an intrinsic. A program definition of the same name is the user's own function.
  * An unknown attribute read (`Other.value`) or write (`Other.value = 3`) stays an unsupported expression whose
  * source starts with its dotted path; it counts when the path's root is bound nowhere in the program.
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

  /** A read of the attribute at the written path `path` (e.g. `Seq("Config", "screen", "title")`). */
  final case class UnknownAttribute(path: Seq[String])

  /** A dotted path, read as a whole or written by an assignment (`=`, not `==`). */
  private val attributeSource = """([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+)(?:\s*=(?!=)[\s\S]*)?""".r

  def attributes(expression: BeExpression): Seq[UnknownAttribute] = {
    val nodes = expression.subtree
    val bound = (nodes.collect {
      case definition: BeDefineVariable => definition.name
      case assignment: BeAssignVariable => assignment.target.name
      case loop: BeFor => loop.variable.name
      case definition: BeDefineClass => definition.name
    } ++ nodes.collect { case definition: BeDefineFunction => definition.inputs.map(_.name) }.flatten)
      .map(_.universalInterpretation()).toSet ++ Set("self", "this")
    nodes.collect { case BeExpressionUnsupported(attributeSource(path), _, _) => path }
      .filterNot(path => bound(BeEntityName.fromCodeString(path.takeWhile(_ != '.')).universalInterpretation()))
      .map(path => UnknownAttribute(path.split('.').toSeq)).distinct
  }

  def of(expression: BeExpression): Option[BeUnknownDependency] = from(calls(expression), attributes(expression))

  def from(calls: Seq[UnknownCall], attributes: Seq[UnknownAttribute] = Nil): Option[BeUnknownDependency] =
    Option.when(calls.nonEmpty || attributes.nonEmpty) {
      val (free, qualified) = calls.partition(_.path.size == 1)
      // Each owner path (`System`, `System.out2`) becomes a class; its attributes lead to the next owner or hold a value.
      val owners = (qualified.map(_.path) ++ attributes.map(_.path)).flatMap(path => (1 until path.size).map(path.take)).distinct
      def owner(path: Seq[String]): BeDefineClass = {
        val signature = BeDefineClass(className(path), Nil, Nil)
        val nested = owners.filter(other => other.size == path.size + 1 && other.startsWith(path))
        val values = attributes.map(_.path).filter(read => read.init == path && !nested.contains(read))
        val members = nested.map(other =>
          BeDefineVariable(BeEntityName.fromCodeString(other.last), BeDataType.ClassType(className(other)))) ++
          values.distinct.map(read => BeDefineVariable(BeEntityName.fromCodeString(read.last), BeDataType.AnyType))
        val methods = qualified.filter(_.path.init == path).map(call =>
          function(call, BeDefineFunction.BeFunctionTypeInfo(Some(signature), Some(BeEntityName.fromCodeString(call.path.last)),
            if path.size == 1 then BeDefineFunction.StaticMethod() else BeDefineFunction.Method())))
        signature.copy(attributes = members.toList, methods = methods.toList)
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
        val initial = if typeName == "Object" then "null" else s"new $typeName()"
        s"    ${modifier(static)}$typeName ${attribute.name.spelling()} = $initial;"
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
        s"    ${attribute.name.spelling()} = ${if typeName == "object" then "None" else s"$typeName()"}"
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
