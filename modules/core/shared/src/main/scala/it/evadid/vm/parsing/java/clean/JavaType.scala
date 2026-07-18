package it.evadid.vm.parsing.java.clean

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.vm.parsing.java.clean.JavaLexer.*

sealed trait JavaType { def javaRepresentation: String }

object JavaType {
  case object JAVA_INT extends JavaType { val javaRepresentation = "int" }
  case object JAVA_LONG extends JavaType { val javaRepresentation = "long" }
  case object JAVA_DOUBLE extends JavaType { val javaRepresentation = "double" }
  case object JAVA_FLOAT extends JavaType { val javaRepresentation = "float" }
  case object JAVA_BOOLEAN extends JavaType { val javaRepresentation = "boolean" }
  case object JAVA_CHAR extends JavaType { val javaRepresentation = "char" }
  case object JAVA_STRING extends JavaType { val javaRepresentation = "String" }
  case object JAVA_VOID extends JavaType { val javaRepresentation = "void" }
  case class JAVA_CLASS(name: String) extends JavaType { val javaRepresentation = name }
  case class JAVA_ARRAY(elementType: JavaType) extends JavaType { val javaRepresentation = elementType.javaRepresentation + "[]" }
  case class JAVA_GENERIC(base: JavaType, typeArguments: Seq[JavaType]) extends JavaType {
    val javaRepresentation = s"${base.javaRepresentation}<${typeArguments.map(_.javaRepresentation).mkString(", ")}>"
  }

  def javaType[$: P]: P[JavaType] = P(baseType ~ (SPACES.? ~ LSQB ~ SPACES.? ~ RSQB).!.rep).map {
    case (base, arrays) => arrays.foldLeft(base)((acc, _) => JAVA_ARRAY(acc))
  }

  private def baseType[$: P]: P[JavaType] = P(namedType ~ (SPACES.? ~ LESS ~ SPACES.? ~ javaType.rep(1, sep = SPACES.? ~ COMMA ~ SPACES.?) ~ SPACES.? ~ GREATER).?).map {
    case (base, Some(args)) => JAVA_GENERIC(base, args)
    case (base, None) => base
  }

  private def namedType[$: P]: P[JavaType] = P(
    StringIn("boolean", "double", "float", "long", "int", "char", "void").!.map {
      case "boolean" => JAVA_BOOLEAN; case "double" => JAVA_DOUBLE; case "float" => JAVA_FLOAT; case "long" => JAVA_LONG
      case "int" => JAVA_INT; case "char" => JAVA_CHAR; case _ => JAVA_VOID
    } | P("String" ~ !ID_CONTINUE).map(_ => JAVA_STRING) | qualifiedName.map(JAVA_CLASS(_))
  )
}
