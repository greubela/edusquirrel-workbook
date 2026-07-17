package it.evadid.vm.parsing.python

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.vm.code.BeDefineStructure
import it.evadid.vm.code.defining.KnownBeDefineStructures


/** FastParse-based Python token scanner that maps every potential identifier/operator token to known definitions. */
object PythonKnownDefinitionScanner {







  /*

  private val SymbolOperators: List[String] = List(
    "**=", "//=", "<<=", ">>=", ":=", "->",
    "==", "!=", "<=", ">=", "//", "**", "<<", ">>", "&&", "||", "+=", "-=", "*=", "/=", "%=", "@=", "&=", "|=", "^=",
    "+", "-", "*", "/", "%", "@", "<", ">", "=", "&", "|", "^", "~", "!"
  )
  private val WordOperators: Set[String] = Set("and", "or", "not", "is", "in")

  def scan(source: String): Map[String, List[BeDefineStructure]] =
    scanTokens(source).distinct.flatMap { identifier =>
      val definitions = KnownBeDefineStructures.byName(identifier)
      Option.when(definitions.nonEmpty)(identifier -> definitions)
    }.toMap

  def scanTokens(source: String): List[String] =
    parse(source, tokens(using _)) match {
      case Parsed.Success(value, _) => combineCompoundWordOperators(value.flatten)
      case _: Parsed.Failure => Nil
    }

  private def tokens[$: P]: P[List[List[String]]] =
    P((token | ignored).rep.map(_.flatten.toList) ~ End)

  private def token[$: P]: P[Option[List[String]]] =
    P(identifier.map(value => Some(List(value))).filter(value => value.exists(_.exists(WordOperators.contains)) || value.exists(_.exists(isPotentialIdentifier))) | symbolOperatorChunk.map(value => Some(splitSymbolOperators(value))))

  private def ignored[$: P]: P[Option[List[String]]] =
    P(CharsWhileIn(" \r\n\t").!.map(_ => None) | comment.map(_ => None) | stringLiteral.map(_ => None) | AnyChar.!.map(_ => None))

  private def identifier[$: P]: P[String] =
    P((CharPred(ch => ch.isLetter || ch == '_') ~ CharsWhile(ch => ch.isLetterOrDigit || ch == '_', 0)).!)

  private def symbolOperatorChunk[$: P]: P[String] =
    P(CharsWhile(ch => "+-*%/@<>=!&|^~".contains(ch), 1).!)

  private def comment[$: P]: P[Unit] =
    P("#" ~ CharsWhile(c => c != '\n' && c != '\r', 0))

  private def stringLiteral[$: P]: P[Unit] =
    P(tripleQuotedString("\"\"\"") | tripleQuotedString("'''") | quotedString('"') | quotedString('\''))

  private def tripleQuotedString[$: P](quote: String): P[Unit] =
    P(quote ~ (!quote ~ AnyChar).rep ~ quote.toString.?)

  private def quotedString[$: P](quote: Char): P[Unit] =
    P(quote.toString ~ (("\\" ~ AnyChar) | (!CharPred(_ == quote) ~ AnyChar)).rep ~ quote.toString.?)

  private def isPotentialIdentifier(value: String): Boolean =
    value.headOption.exists(ch => ch.isLetter || ch == '_')

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

  private def combineCompoundWordOperators(tokens: List[String]): List[String] = {
    tokens.foldLeft(List.empty[String]) {
      case (init :+ "is", "not") => init :+ "is not"
      case (init :+ "not", "in") => init :+ "not in"
      case (acc, token) => acc :+ token
    }


  }
*/
}
