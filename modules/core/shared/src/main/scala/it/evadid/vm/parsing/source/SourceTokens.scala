package it.evadid.vm.parsing.source

import fastparse.*
import fastparse.NoWhitespace.*

/** Lexical tokens of Java or Python source: words, comments and string literals, symbols and nested bracket
  * groups. Unfinished source never fails: an unclosed group, string or comment runs to the end of the text.
  */
object SourceTokens {
  sealed trait Token { def start: Int; def end: Int }
  final case class Word(start: Int, end: Int, text: String) extends Token
  /** A comment or string literal; `closed` is false when the text ends inside it. */
  final case class Opaque(start: Int, end: Int, comment: Boolean, closed: Boolean = true) extends Token
  final case class Symbol(start: Int, end: Int, text: String) extends Token {
    def isSpace: Boolean = text.forall(_.isWhitespace)
  }
  final case class Group(open: Char, start: Int, end: Int, children: Seq[Token], closed: Boolean) extends Token

  private final class Lexer(python: Boolean) {
    private val closers = Map('(' -> ')', '[' -> ']', '{' -> '}')
    private def isWordStart(c: Char) = c.isLetter || c == '_' || (!python && c == '$')
    private def isWordPart(c: Char) = c.isLetterOrDigit || c == '_' || (!python && c == '$')

    def program[$: P]: P[Seq[Token]] = P(token(None).rep ~ End)

    private def token[$: P](closer: Option[Char]): P[Token] =
      P(comment | string | group | word | stray(closer) | spaces | symbol)

    private def comment[$: P]: P[Token] =
      if python then P(Index ~ "#" ~ CharsWhile(c => c != '\n' && c != '\r', 0) ~ Index).map((s, e) => Opaque(s, e, comment = true))
      else P(Index ~ ("//" ~ CharsWhile(c => c != '\n' && c != '\r', 0) ~ Pass(true) | "/*" ~ (!"*/" ~ AnyChar).rep ~ ("*/".!.map(_ => true) | End.map(_ => false))) ~ Index)
        .map((s, closed, e) => Opaque(s, e, comment = true, closed))

    private def quoted[$: P](delimiter: String, multiline: Boolean): P[Boolean] =
      P(delimiter ~ (("\\" ~ AnyChar) | (!delimiter ~ (if multiline then AnyChar else CharPred(c => c != '\n' && c != '\r')))).rep ~
        (delimiter.!.map(_ => true) | (End | &(CharIn("\r\n"))).map(_ => false)))

    private def string[$: P]: P[Token] =
      P(Index ~ (quoted("\"\"\"", multiline = true) | (if python then quoted("'''", multiline = true) else Fail) |
        quoted("\"", multiline = false) | quoted("'", multiline = false)) ~ Index).map((s, closed, e) => Opaque(s, e, comment = false, closed))

    private def group[$: P]: P[Token] =
      P(Index ~ CharIn("([{").! ~~ Pass).flatMapX { (start, open) =>
        val close = closers(open.head)
        P(token(Some(close)).rep ~ (close.toString.!.map(_ => true) | Pass.map(_ => false)) ~ Index)
          .map((children, closed, end) => Group(open.head, start, end, children, closed))
      }

    private def word[$: P]: P[Token] =
      P(Index ~ (CharPred(isWordStart) ~ CharsWhile(isWordPart, 0)).! ~ Index).map((s, text, e) => Word(s, e, text))

    /** A closing bracket that does not close the enclosing group. */
    private def stray[$: P](closer: Option[Char]): P[Token] =
      P(Index ~ CharPred(c => ")]}".contains(c) && !closer.contains(c)).! ~ Index).map((s, text, e) => Symbol(s, e, text))

    private def spaces[$: P]: P[Token] =
      P(Index ~ CharsWhile(c => c == ' ' || c == '\t' || c == '\r').! ~ Index).map((s, text, e) => Symbol(s, e, text))

    private def symbol[$: P]: P[Token] =
      P(Index ~ CharPred(c => !")]}".contains(c)).! ~ Index).map((s, text, e) => Symbol(s, e, text))
  }

  private val javaLexer = new Lexer(python = false)
  private val pythonLexer = new Lexer(python = true)

  /** Only the pipeline's first step lexes ([[SourceParsing]]). */
  private[source] def lex(source: String, python: Boolean): Seq[Token] =
    fastparse.parse(source, context => (if python then pythonLexer else javaLexer).program(using context)) match {
      case Parsed.Success(value, _) => value
      case failure: Parsed.Failure => throw IllegalStateException(s"The source lexer must accept any text: ${failure.msg}")
    }

  def flatten(values: Seq[Token]): Seq[Token] = values.flatMap {
    case group: Group => group +: flatten(group.children)
    case other => Seq(other)
  }

  /** Tokens that carry code: no whitespace and no comments. */
  def significant(values: Seq[Token]): Seq[Token] = values.filter {
    case symbol: Symbol => !symbol.isSpace
    case Opaque(_, _, comment, _) => !comment
    case _ => true
  }

  /** Every bracket, string and comment is closed and no closing bracket is left over. */
  def balanced(values: Seq[Token]): Boolean = flatten(values).forall {
    case group: Group => group.closed
    case opaque: Opaque => opaque.closed
    case Symbol(_, _, text) => !")]}".contains(text)
    case _ => true
  }

  /** `source` with the characters of `ranges` turned into spaces; line breaks and offsets are kept. */
  def blank(source: String, ranges: Iterable[(Int, Int)]): String = {
    val result = source.toCharArray
    ranges.foreach((start, end) => (start until end).foreach(i => if result(i) != '\n' && result(i) != '\r' then result(i) = ' '))
    new String(result)
  }
}
