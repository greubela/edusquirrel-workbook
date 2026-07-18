package it.evadid.vm.parsing.java.clean

import fastparse.*
import fastparse.NoWhitespace.*

object JavaLexer {
  export it.evadid.vm.parsing.generic.CodeLexer.{
    LPAR, RPAR, LSQB, RSQB, LBRACE, RBRACE, DOT, COMMA, SEMI, LESS, GREATER, ASSIGN
  }

  def LINE_COMMENT[$: P]: P[Unit] = P("//" ~ CharsWhile(ch => ch != '\n' && ch != '\r', 0))
  def BLOCK_COMMENT[$: P]: P[Unit] = P("/*" ~ (!"*/" ~ AnyChar).rep ~ "*/".?)
  def WS[$: P]: P[Unit] = P(CharIn(" \t\r\n\f").rep(1))
  def SPACES[$: P]: P[Unit] = P((WS | LINE_COMMENT | BLOCK_COMMENT).rep)

  def ID_START[$: P]: P[Unit] = P(CharIn("a-zA-Z_") | "$" )
  def ID_CONTINUE[$: P]: P[Unit] = P(CharIn("a-zA-Z0-9_") | "$" )
  private def KEYWORD[$: P]: P[Unit] = P(StringIn(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "continue", "default", "do", "double",
    "else", "enum", "extends", "final", "finally", "float", "for", "if", "implements", "import", "instanceof", "int", "interface",
    "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch",
    "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"
  ) ~ !ID_CONTINUE)
  def NAME[$: P]: P[String] = P(!KEYWORD ~ ID_START ~ ID_CONTINUE.rep).!
  def keyword[$: P](value: String): P[Unit] = P(value ~ !ID_CONTINUE)
  def modifier[$: P]: P[String] = P(StringIn("public", "private", "protected", "static", "final", "abstract").! ~ !ID_CONTINUE)
  def qualifiedName[$: P]: P[String] = P(NAME.rep(1, sep = DOT).map(_.mkString(".")))
  def operator[$: P](ops: String*): P[String] = ops.map(op => P(op).!).reduce(_ | _)
  def STRING_LITERAL[$: P]: P[String] = P(("\"" ~ (("\\" ~ AnyChar) | (!"\"" ~ AnyChar)).rep ~ "\"") | ("'" ~ (("\\" ~ AnyChar) | (!"'" ~ AnyChar)).rep ~ "'")).!
  def NUMBER_LITERAL[$: P]: P[String] = P(CharIn("0-9").rep(1) ~ ("." ~ CharIn("0-9").rep(1)).?).!
}
