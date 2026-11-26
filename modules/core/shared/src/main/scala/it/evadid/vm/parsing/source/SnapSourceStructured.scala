package it.evadid.vm.parsing.source

import fastparse.*
import fastparse.NoWhitespace.*
import upickle.default.ReadWriter

/** Lossless lexical inspection, available even when XML cannot form a document. */
final case class SnapSourceStructured(source: String, tokens: Seq[SnapSourceStructured.Token]) derives ReadWriter

object SnapSourceStructured {
  enum Kind derives ReadWriter { case Text, Tag, Comment, CData, Instruction, Declaration }
  final case class Token(kind: Kind, start: Int, end: Int, closed: Boolean) derives ReadWriter

  private def quoted[$: P]: P[Unit] =
    P("\"" ~ (!"\"" ~ AnyChar).rep ~ ("\"" | End) | "'" ~ (!"'" ~ AnyChar).rep ~ ("'" | End))

  private def delimited[$: P](open: String, close: String, kind: Kind): P[Token] =
    P(Index ~ open ~ (!close ~ AnyChar).rep ~ (close.!.map(_ => true) | End.map(_ => false)) ~ Index)
      .map((start, closed, end) => Token(kind, start, end, closed))

  private def declaration[$: P]: P[Token] =
    P(Index ~ "<!DOCTYPE" ~ (quoted | subset | (!">" ~ AnyChar)).rep ~
      (">".!.map(_ => true) | End.map(_ => false)) ~ Index)
      .map((start, closed, end) => Token(Kind.Declaration, start, end, closed))

  private def subset[$: P]: P[Unit] =
    P("[" ~ (quoted | subset | (!"]" ~ AnyChar)).rep ~ ("]" | End))

  private def token[$: P]: P[Token] = P(
    delimited("<!--", "-->", Kind.Comment) | delimited("<![CDATA[", "]]>", Kind.CData) |
      delimited("<?", "?>", Kind.Instruction) | declaration |
      (Index ~ "<" ~ (quoted | (!">" ~ AnyChar)).rep ~ (">".!.map(_ => true) | End.map(_ => false)) ~ Index)
        .map((start, closed, end) => Token(Kind.Tag, start, end, closed)) |
      (Index ~ CharsWhile(_ != '<', 1) ~ Index).map((start, end) => Token(Kind.Text, start, end, true)))

  private def program[$: P]: P[Seq[Token]] = P(token.rep ~ End)

  private[source] def lex(source: String): SnapSourceStructured =
    fastparse.parse(source, context => program(using context)) match {
      case Parsed.Success(tokens, _) => SnapSourceStructured(source, tokens)
      case failure: Parsed.Failure => throw IllegalStateException(s"The Snap lexer must accept any text: ${failure.msg}")
    }
}
