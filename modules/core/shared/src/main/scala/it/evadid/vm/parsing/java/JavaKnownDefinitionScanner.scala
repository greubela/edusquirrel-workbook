package it.evadid.vm.parsing.java

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.vm.code.abstractions.BeDefineStructure
import it.evadid.vm.code.defining.KnownBeDefineStructures

/** FastParse-based Java token scanner that maps every potential identifier/operator token to known definitions. */
object JavaKnownDefinitionScanner {
  private val SymbolOperators: List[String] = List(
    ">>>=", "<<=", ">>=", "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>>", ">>",
    "+", "-", "*", "/", "%", "<", ">", "=", "&", "|", "^", "~", "!"
  )

  def scan(source: String): Map[String, List[BeDefineStructure]] =
    scanTokens(source).distinct.flatMap { token =>
      val definitions = KnownBeDefineStructures.byName(token)
      Option.when(definitions.nonEmpty)(token -> definitions)
    }.toMap

  def scanTokens(source: String): List[String] =
    parse(source, tokens(using _)) match {
      case Parsed.Success(value, _) => value.flatten
      case _: Parsed.Failure => Nil
    }

  private def tokens[$: P]: P[List[List[String]]] =
    P((commentOrLiteralIgnored | token | ignored).rep.map(_.flatten.toList) ~ End)

  private def token[$: P]: P[Option[List[String]]] =
    P(identifier.map(value => Some(List(value))) | symbolOperatorChunk.map(value => Some(splitSymbolOperators(value))))

  private def commentOrLiteralIgnored[$: P]: P[Option[List[String]]] =
    P(lineComment.map(_ => None) | blockComment.map(_ => None) | stringLiteral.map(_ => None) | charLiteral.map(_ => None))

  private def ignored[$: P]: P[Option[List[String]]] =
    P(CharsWhileIn(" \r\n\t").!.map(_ => None) | AnyChar.!.map(_ => None))

  private def identifier[$: P]: P[String] =
    P((CharPred(ch => ch.isLetter || ch == '_' || ch == '$') ~ CharsWhile(ch => ch.isLetterOrDigit || ch == '_' || ch == '$', 0)).!)

  private def symbolOperatorChunk[$: P]: P[String] =
    P(CharsWhile(ch => "+-*/%<>=!&|^~".contains(ch), 1).!)

  private def lineComment[$: P]: P[Unit] =
    P("//" ~ CharsWhile(c => c != '\n' && c != '\r', 0))

  private def blockComment[$: P]: P[Unit] =
    P("/*" ~ (!"*/" ~ AnyChar).rep ~ "*/".?)

  private def stringLiteral[$: P]: P[Unit] =
    P('"'.toString ~ (("\\" ~ AnyChar) | (!CharPred(_ == '"') ~ AnyChar)).rep ~ '"'.toString.?)

  private def charLiteral[$: P]: P[Unit] =
    P("'" ~ (("\\" ~ AnyChar) | (!CharPred(_ == '\'') ~ AnyChar)).rep ~ "'".?)

  private def splitSymbolOperators(value: String): List[String] = {
    val builder = List.newBuilder[String]
    var remaining = value
    while (remaining.nonEmpty) {
      SymbolOperators.find(remaining.startsWith) match {
        case Some(operator) =>
          builder += operator
          remaining = remaining.drop(operator.length)
        case None =>
          builder += remaining.head.toString
          remaining = remaining.tail
      }
    }
    builder.result()
  }
}
