package it.evadid.vm.parsing.generic

import fastparse.*
import fastparse.NoWhitespace.*

object CodeLexer {

  // ==========================================
  // 1. Delimiters, Operators & Special Symbols
  // ==========================================
  def LPAR[ctx: P]: P[Unit] = P("(")

  def RPAR[ctx: P]: P[Unit] = P(")")

  def LSQB[ctx: P]: P[Unit] = P("[")

  def RSQB[ctx: P]: P[Unit] = P("]")

  def LBRACE[ctx: P]: P[Unit] = P("{")

  def RBRACE[ctx: P]: P[Unit] = P("}")

  def DOT[ctx: P]: P[Unit] = P(".")

  def COLON[ctx: P]: P[Unit] = P(":")

  def COMMA[ctx: P]: P[Unit] = P(",")

  def SEMI[ctx: P]: P[Unit] = P(";")

  def PLUS[ctx: P]: P[Unit] = P("+")

  def MINUS[ctx: P]: P[Unit] = P("-")

  def STAR[ctx: P]: P[Unit] = SPACES.? ~~ P("*") ~~ SPACES.?

  def SLASH[ctx: P]: P[Unit] = P("/")

  def VBAR[ctx: P]: P[Unit] = P("|")

  def AMPER[ctx: P]: P[Unit] = P("&")

  def LESS[ctx: P]: P[Unit] = P("<")

  def GREATER[ctx: P]: P[Unit] = P(">")

  def ASSIGN[ctx: P]: P[Unit] = P("=" ~ !"=") // Lookahead fixed: doesn't steal following chars

  def PERCENT[ctx: P]: P[Unit] = P("%")

  def EQEQUAL[ctx: P]: P[Unit] = P("==")

  def NOTEQUAL[ctx: P]: P[Unit] = P("!=")

  def LESSEQUAL[ctx: P]: P[Unit] = P("<=")

  def GREATEREQUAL[ctx: P]: P[Unit] = P(">=")

  def TILDE[ctx: P]: P[Unit] = P("~")

  def CIRCUMFLEX[ctx: P]: P[Unit] = P("^")

  def LEFTSHIFT[ctx: P]: P[Unit] = P("<<")

  def RIGHTSHIFT[ctx: P]: P[Unit] = P(">>")

  def DOUBLESTAR[ctx: P]: P[Unit] = P("**")

  def PLUSEQUAL[ctx: P]: P[Unit] = P("+=")

  def MINEQUAL[ctx: P]: P[Unit] = P("-=")

  def STAREQUAL[ctx: P]: P[Unit] = P("*=")

  def SLASHEQUAL[ctx: P]: P[Unit] = P("/=")

  def PERCENTEQUAL[ctx: P]: P[Unit] = P("%=")

  def AMPEREQUAL[ctx: P]: P[Unit] = P("&=")

  def VBAREQUAL[ctx: P]: P[Unit] = P("|=")

  def CIRCUMFLEXEQUAL[ctx: P]: P[Unit] = P("^=")

  def LEFTSHIFTEQUAL[ctx: P]: P[Unit] = P("<<=")

  def RIGHTSHIFTEQUAL[ctx: P]: P[Unit] = P(">>=")

  def DOUBLESTAREQUAL[ctx: P]: P[Unit] = P("**=")

  def DOUBLESLASH[ctx: P]: P[Unit] = P("//")

  def DOUBLESLASHEQUAL[ctx: P]: P[Unit] = P("//=")

  def AT[ctx: P]: P[Unit] = P("@")

  def ATEQUAL[ctx: P]: P[Unit] = P("@=")

  def RARROW[ctx: P]: P[Unit] = P("->")

  def ELLIPSIS[ctx: P]: P[Unit] = P("...")

  def COLONEQUAL[ctx: P]: P[Unit] = P(":=")

  def EXCLAMATION[ctx: P]: P[Unit] = P("!")

  def AUGASSIGN[ctx: P]: P[String] = P(
    StringIn("+=", "-=", "*=", "@=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", "**=", "//=")
  ).!

  def COMPAREOP[ctx: P]: P[String] = P(
    P("==").! | P("!=").! | P("<=").! | P(">=").! | P("<").! | P(">").! |
      P(NOT ~ SPACES ~ IN).map(_ => "not in") | P(IN).!.map(_ => "in") |
      P(IS ~ SPACES ~ NOT).map(_ => "is not") | P(IS).!.map(_ => "is")
  )


  // ==========================================
  // 2. Strict Python Keywords Mapping
  // ==========================================

  def AWAIT[ctx: P]: P[Unit] = P("await" ~~ !ID_CONTINUE)

  def ELSE[ctx: P]: P[Unit] = P("else" ~~ !ID_CONTINUE)

  def IMPORT[ctx: P]: P[Unit] = P("import" ~~ !ID_CONTINUE)

  def PASS[ctx: P]: P[Unit] = P("pass" ~~ !ID_CONTINUE)

  def NONE[ctx: P]: P[Unit] = P("None" ~~ !ID_CONTINUE)

  def BREAK[ctx: P]: P[Unit] = P("break" ~~ !ID_CONTINUE)

  def EXCEPT[ctx: P]: P[Unit] = P("except" ~~ !ID_CONTINUE)

  def IN[ctx: P]: P[Unit] = P("in" ~~ !ID_CONTINUE)

  def RAISE[ctx: P]: P[Unit] = P("raise" ~~ !ID_CONTINUE)


  def CLASS[ctx: P]: P[Unit] = P("class" ~~ !ID_CONTINUE)

  def FINALLY[ctx: P]: P[Unit] = P("finally" ~~ !ID_CONTINUE)

  def IS[ctx: P]: P[Unit] = P("is" ~~ !ID_CONTINUE)

  def RETURN[ctx: P]: P[Unit] = P("return" ~~ !ID_CONTINUE)

  def AND[ctx: P]: P[Unit] = P("and" ~~ !ID_CONTINUE)

  def CONTINUE[ctx: P]: P[Unit] = P("continue" ~~ !ID_CONTINUE)

  def FOR[ctx: P]: P[Unit] = P("for" ~~ !ID_CONTINUE)

  def LAMBDA[ctx: P]: P[Unit] = P("lambda" ~~ !ID_CONTINUE)

  def TRY[ctx: P]: P[Unit] = P("try" ~~ !ID_CONTINUE)

  def AS[ctx: P]: P[Unit] = P("as" ~~ !ID_CONTINUE)

  def DEF[ctx: P]: P[Unit] = P("def" ~~ !ID_CONTINUE)

  def FROM[ctx: P]: P[Unit] = P("from" ~~ !ID_CONTINUE)

  def NONLOCAL[ctx: P]: P[Unit] = P("nonlocal" ~~ !ID_CONTINUE)

  def WHILE[ctx: P]: P[Unit] = P("while" ~~ !ID_CONTINUE)

  def ASSERT[ctx: P]: P[Unit] = P("assert" ~~ !ID_CONTINUE)

  def DEL[ctx: P]: P[Unit] = P("del" ~~ !ID_CONTINUE)

  def GLOBAL[ctx: P]: P[Unit] = P("global" ~~ !ID_CONTINUE)

  def NOT[ctx: P]: P[Unit] = P("not" ~~ !ID_CONTINUE)

  def WITH[ctx: P]: P[Unit] = P("with" ~~ !ID_CONTINUE)

  def ASYNC[ctx: P]: P[String] = P("async" ~~ !ID_CONTINUE).!

  def ELIF[ctx: P]: P[Unit] = P("elif" ~~ !ID_CONTINUE)

  def IF[ctx: P]: P[Unit] = P("if" ~~ !ID_CONTINUE)

  def OR[ctx: P]: P[Unit] = P("or" ~~ !ID_CONTINUE)

  def YIELD[ctx: P]: P[Unit] = P("yield" ~~ !ID_CONTINUE)

  // ==========================================
  // 3. Soft Keywords
  // ==========================================
  def NAME_OR_TYPE[ctx: P]: P[Unit] = P("type" ~~ !ID_CONTINUE)

  def NAME_OR_MATCH[ctx: P]: P[Unit] = P("match" ~~ !ID_CONTINUE)

  def NAME_OR_CASE[ctx: P]: P[Unit] = P("case" ~~ !ID_CONTINUE)

  def NAME_OR_WILDCARD[ctx: P]: P[Unit] = P("_" ~~ !ID_CONTINUE)

  // ==========================================
  // 4. Identifiers, Structural Spaces & Comments
  // ==========================================
  def ID_START[ctx: P]: P[Unit] = P(CharIn("a-zA-Z_"))

  def ID_CONTINUE[ctx: P]: P[Unit] = P(CharIn("a-zA-Z0-9_"))

  //  def KEYWORD[ctx: P]: P[String] = (P(IMPORT | FROM | IF | ELSE | ASYNC | OR | YIELD | AND |AWAIT| NOT | GLOBAL | DEL | ASSERT | WHILE | FOR | AS | TRY | LAMBDA | CONTINUE | CLASS | DEF | RETURN | FINALLY | IS | NONE | RAISE ) ~~ !ID_CONTINUE).! // todo
  def KEYWORD[ctx: P]: P[Unit] = P(
    StringIn(
      "import", "from", "if", "else", "async", "or", "yield", "and",
      "await", "not", "global", "del", "assert", "while", "for", "as",
      "try", "lambda", "continue", "class", "def", "return", "finally",
      "is", "None", "raise"
    ) ~~ !ID_CONTINUE
  )

  def NAME[ctx: P]: P[String] = P(!(KEYWORD) ~~ ID_START ~~ ID_CONTINUE.rep).!

  def NEWLINE[ctx: P]: P[Unit] = P("\r".? ~~ "\n")

  def COMMENT[ctx: P]: P[Unit] = P("#" ~~ CharsWhile(_ != '\n', 0))

  def ANYLINE[ctx: P]: P[String] = P(CharsWhile(_ != '\n', 0)).!

  def WS[ctx: P]: P[Unit] = P(CharIn(" \t\f").rep(1))

  def EXPLICIT_LINE_JOINING[ctx: P]: P[Unit] = P("\\" ~~ NEWLINE)

  def SPACES[ctx: P]: P[Unit] = P((WS | COMMENT).rep)

  // ==========================================
  // 6. String Literals
  // ==========================================

  def STRING[ctx: P]: P[String] = P(tripleQuotedString | quotedString).!

  private def tripleQuotedString[ctx: P]: P[Unit] =
    P("\"\"\"" ~ (!"\"\"\"" ~ AnyChar).rep ~ "\"\"\"".?) | P("'''" ~ (!"'''" ~ AnyChar).rep ~ "'''".?)

  private def quotedString[ctx: P]: P[Unit] =
    P(("\"" ~ (("\\" ~ AnyChar) | (!"\"" ~ AnyChar)).rep ~ "\"".?) | ("'" ~ (("\\" ~ AnyChar) | (!"'" ~ AnyChar)).rep ~ "'".?))



  // ==========================================
  // 5. OPERATORS
  // ==========================================

  def SHIFTOP[ctx: P]: P[String] = P(LEFTSHIFT | RIGHTSHIFT).!

  def MULTLIKEOP[ctx: P]: P[String] = P(STAR | SLASH | DOUBLESLASH | PERCENT | AT).!

  def UNARYPREFIX[ctx: P]: P[String] = P(PLUS | MINUS | TILDE).!


  // ==========================================
  // 5. Literal HELPER
  // ==========================================

  def digit[ctx: P]: P[Unit] = P(CharIn("0-9"))

  def nonzero_digit[ctx: P]: P[Unit] = P(CharIn("1-9"))

  def bindigit[ctx: P]: P[Unit] = P(CharIn("01"))

  def octdigit[ctx: P]: P[Unit] = P(CharIn("0-7"))

  def hexdigit[ctx: P]: P[Unit] = P(CharIn("0-9a-fA-F"))

  def digitpart[ctx: P]: P[Unit] = P(digit ~~ (P("_").? ~~ digit).rep)

  def fraction[ctx: P]: P[Unit] = P("." ~~ digitpart)

  def exponent[ctx: P]: P[Unit] = P(CharIn("eE") ~~ CharIn("+\\-").? ~~ digitpart)
}
