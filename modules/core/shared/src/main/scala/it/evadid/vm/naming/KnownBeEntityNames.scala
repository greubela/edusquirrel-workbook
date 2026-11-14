package it.evadid.vm.naming

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*

/** Hard-coded, role-separated names that the block environment can recognize without parsing user code. */
object KnownBeEntityNames {

  val classNames: List[BeEntityName] = List(
    BeEntityName.fromUniversalNameInParts("turtle"),
    BeEntityName.fromMapInCodeNotation(Map(English -> "string", German -> "zeichenkette")),
    BeEntityName.fromMapInCodeNotation(Map(English -> "integer", German -> "ganzzahl")),
    BeEntityName.fromMapInCodeNotation(Map(English -> "number", German -> "zahl")),
    BeEntityName.fromMapInCodeNotation(Map(English -> "boolean", German -> "wahrheitswert")),
    BeEntityName.fromUniversalNameInParts("list"),
    BeEntityName.fromUniversalNameInParts("tuple"),
    BeEntityName.fromUniversalNameInParts("dict"),
    BeEntityName.fromUniversalNameInParts("set"),
    BeEntityName.fromUniversalNameInParts("range"),
    BeEntityName.fromUniversalNameInParts("object")
  )

  val functionNames: List[BeEntityName] = List(
    BeEntityName.fromUniversalNameInParts("str"),
    BeEntityName.fromUniversalNameInParts("int"),
    BeEntityName.fromUniversalNameInParts("float"),
    BeEntityName.fromUniversalNameInParts("bool"),
    BeEntityName.fromUniversalNameInParts("abs"),
    BeEntityName.fromUniversalNameInParts("print"),
    BeEntityName.fromUniversalNameInParts("input"),
    BeEntityName.fromUniversalNameInParts("len"),
    BeEntityName.fromUniversalNameInParts("range"),
    BeEntityName.fromUniversalNameInParts("round"),
    BeEntityName.fromUniversalNameInParts("min"),
    BeEntityName.fromUniversalNameInParts("max"),
    BeEntityName.fromUniversalNameInParts("sum"),
    BeEntityName.fromUniversalNameInParts("sorted"),
    BeEntityName.fromUniversalNameInParts("enumerate"),
    BeEntityName.fromUniversalNameInParts("zip"),
    BeEntityName.fromUniversalNameInParts("type"),
    BeEntityName.fromUniversalNameInParts("isinstance")
  )

  lazy val methodNames: List[BeEntityName] =
    TurtleCommand.turtleClass.methods.map(_.functionTypeInfo.displayName)

  val operatorNames: List[BeEntityName] = List(
    "+", "-", "*", "/", "//", "%", "**", "@",
    "<", "<=", ">", ">=", "==", "!=",
    "&", "|", "^", "<<", ">>", "~",
    "and", "or", "not", "is", "is not", "in", "not in"
  ).map(BeEntityName.fromLiteral)

  val variableNames: List[BeEntityName] = Nil

  lazy val allNames: List[BeEntityName] = classNames ++ functionNames ++ methodNames ++ operatorNames ++ variableNames

  private val aliases: Map[String, String] = Map(
    "&&" -> "and",
    "||" -> "or",
    "!" -> "not",
    "println" -> "print",
    "printf" -> "print",
    "double" -> "number",
    "long" -> "integer",
    "short" -> "integer",
    "byte" -> "integer",
    "char" -> "string"
  )

  private val supportedStyles = List(NamingStyle.SnakeCase, NamingStyle.CamelCase)
  private lazy val canonicalLookup: Map[String, BeEntityName] = allNames.flatMap { name =>
    AppLanguage.humanLanguages.flatMap { language =>
      supportedStyles.map(style => normalize(name.getNameIn(language, style)) -> name)
    }
  }.toMap

  private lazy val lookup: Map[String, BeEntityName] =
    canonicalLookup ++ aliases.flatMap { case (alias, canonical) =>
      canonicalLookup.get(normalize(canonical)).map(alias -> _)
    }

  /** Function-call names only; declarations and variables retain their source names. */
  def resolveCanonicalNames(name: String): Option[BeEntityName] =
    TurtleCommand.resolveCanonicalNames(name).orElse(resolveSystemMethods(name))

  def resolveSystemMethods(name: String): Option[BeEntityName] =
    Option.when(Set("print", "println", "system.out.println").contains(normalize(name)))(
      functionNames.find(_.universalInterpretation() == "print").get)

  def byName(name: String): Option[BeEntityName] =
    resolveCanonicalNames(name).orElse(lookup.get(normalize(name)))

  private def normalize(name: String): String = name.trim.toLowerCase
}
