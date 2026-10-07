package it.evadid.vm.parsing.java.turtle

import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.java.clean.model.JavaAST.*
import it.evadid.vm.parsing.java.clean.model.JavaType.*
import it.evadid.vm.parsing.java.turtle.JavaTurtleSource.{Diagnostic, ParsedSource, Problem}

object JavaTurtleStructure {
  final class StructuredSource private[JavaTurtleStructure](
      val parsedSource: ParsedSource,
      val classDef: JavaClassDef,
      val main: JavaMethodDef,
      val methods: Seq[JavaMethodDef]
  )

  def check(parsed: ParsedSource): Either[Diagnostic, StructuredSource] =
    parsed.program.statements.map(_.statement) match {
      case Seq(clazz: JavaClassDef) =>
        for {
          _ <- checkClass(clazz).toLeft(())
          methods = clazz.body.statements.collect { case method: JavaMethodDef => method }
          _ <- duplicate(methods.map(_.name)).map(name => problem(
            Problem.DuplicateDeclaration, s"Use a different method name for $name; overloads are not supported yet."
          )).toLeft(())
          main <- methods.find(_.name == "main").toRight(problem(
            Problem.MissingMain, "Add public static void main(String[] args) as the starting method."
          ))
          _ <- methods.iterator.map(checkMethod).collectFirst { case Some(error) => error }.toLeft(())
        } yield new StructuredSource(parsed, clazz, main, methods)
      case _ => Left(problem(Problem.UnsupportedStructure, "Put the program in one class, without a package or imports."))
    }

  private val reservedIdentifiers = Set("_", "const", "goto")
  private val restrictedTypeNames = Set("var", "yield", "record", "sealed", "permits")
  private val classNamesInUse = Set("String", "Turtle")
  private val helperModifiers = Set("public", "private", "static")

  private def problem(kind: Problem, message: String): Diagnostic = Diagnostic(kind, message, None)

  private def identifierProblem(name: String): Option[Diagnostic] =
    Option.when(reservedIdentifiers.contains(name))(
      problem(Problem.InvalidIdentifier, s"Choose a different name instead of $name.")
    )

  private def duplicate(names: Seq[String]): Option[String] = {
    var seen = Set.empty[String]
    names.find { name =>
      val repeated = seen.contains(name)
      seen += name
      repeated
    }
  }

  private def checkClass(clazz: JavaClassDef): Option[Diagnostic] =
    identifierProblem(clazz.name).orElse {
      if restrictedTypeNames.contains(clazz.name) || classNamesInUse.contains(clazz.name) then
        Some(problem(Problem.InvalidIdentifier, s"The class name ${clazz.name} is not available for turtle programs."))
      else if clazz.modifiers != Seq.empty && clazz.modifiers != Seq("public") then
        Some(problem(Problem.UnsupportedStructure, "Use a class without modifiers, or a public class."))
      else if clazz.extendsType.nonEmpty || clazz.implementsTypes.nonEmpty then
        Some(problem(Problem.UnsupportedStructure, "Inheritance and interfaces are not supported yet."))
      else if !clazz.body.statements.forall(_.isInstanceOf[JavaMethodDef]) then
        Some(problem(Problem.UnsupportedStructure, "Put only methods in the class; fields and nested classes are not supported yet."))
      else None
    }

  private def isVoid(method: JavaMethodDef): Boolean =
    method.returnType.exists {
      case value: JAVA_UNPARSABLE_TYPE => value.typenameInCode == "void"
      case _ => false
    }

  private def isMainParameter(parameter: JavaVariableDeclaration): Boolean =
    parameter.javaType match {
      case array: JAVA_ARRAY[?] => array.elementType.isInstanceOf[JAVA_STRING]
      case _ => false
    }

  private def checkMethod(method: JavaMethodDef): Option[Diagnostic] =
    identifierProblem(method.name).orElse {
      val modifiers = method.modifiers
      val uniqueModifiers = modifiers.distinct.size == modifiers.size
      if method.name == "main" then {
        if !uniqueModifiers || modifiers.toSet != Set("public", "static") || !isVoid(method) ||
            method.parameters.size != 1 || !isMainParameter(method.parameters.head) then
          Some(problem(Problem.InvalidMain, "Use public static void main(String[] args) as the starting method."))
        else checkParameters(method).orElse(checkBody(method.body))
      } else if !uniqueModifiers || !modifiers.contains("static") || !modifiers.forall(helperModifiers.contains) ||
          modifiers.count(value => value == "public" || value == "private") > 1 || !isVoid(method) then
        Some(problem(Problem.UnsupportedStructure, s"Use static void for ${method.name}, with at most public or private visibility."))
      else checkParameters(method).orElse(checkBody(method.body))
    }

  private def checkParameters(method: JavaMethodDef): Option[Diagnostic] =
    duplicate(method.parameters.map(_.name)).map(name => problem(
      Problem.DuplicateDeclaration, s"Use each parameter name only once in ${method.name}: $name is repeated."
    )).orElse {
      method.parameters.iterator.map { parameter =>
        identifierProblem(parameter.name).orElse {
          if parameter.modifiers.nonEmpty || parameter.value.nonEmpty then
            Some(problem(Problem.UnsupportedStructure, "Parameter modifiers and initial values are not supported yet."))
          else if method.name != "main" && !parameter.javaType.isInstanceOf[JAVA_INTEGER] &&
              !parameter.javaType.isInstanceOf[JAVA_BOOL] then
            Some(problem(Problem.UnsupportedType, s"Use int or boolean for the parameters of ${method.name}."))
          else None
        }
      }.collectFirst { case Some(error) => error }
    }

  private def checkBody(node: GenericAST): Option[Diagnostic] =
    node match {
      case _: JavaClassDef | _: JavaMethodDef | _: JavaImportStatement | _: JavaPackageStatement =>
        Some(problem(Problem.UnsupportedStructure, "Class, method, package and import declarations do not belong inside a method."))
      case variable: JavaVariableDeclaration =>
        identifierProblem(variable.name).orElse(checkChildren(variable))
      case _ => checkChildren(node)
    }

  private def checkChildren(node: GenericAST): Option[Diagnostic] =
    node.getChildren().iterator.map(checkBody).collectFirst { case Some(error) => error }
}
