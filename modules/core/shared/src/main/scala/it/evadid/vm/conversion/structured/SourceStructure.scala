package it.evadid.vm.conversion.structured

import fastparse.*
import fastparse.NoWhitespace.*
import it.evadid.util.logging.Logger
import it.evadid.vm.naming.BeEntityName
import scala.collection.immutable.VectorMap

/** Lexical structure of Java and Python source, for structured editors and class hoisting.
  *
  * A fastparse lexer reads words, comments, string literals and nested bracket groups. Unfinished
  * source never fails: an unclosed group, string or comment runs to the end of the text. Classes,
  * members and Python blocks are then located in this token tree; their bodies stay editable text.
  */
object SourceStructure {
  enum MemberScope { case Static, Instance }
  case class Block(name: String, start: Int, end: Int, header: String, body: String, source: String, indent: Int)
  case class Parts(definedClasses: Map[BeEntityName, String], remaining: String)
  private val logger = Logger.withNameAndPrefixes(Some("SourceStructure"))

  sealed trait Token { def start: Int; def end: Int }
  final case class Word(start: Int, end: Int, text: String) extends Token
  /** A comment or string literal. */
  final case class Opaque(start: Int, end: Int, comment: Boolean) extends Token
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
      if python then P(Index ~ "#" ~ CharsWhile(_ != '\n', 0) ~ Index).map((s, e) => Opaque(s, e, comment = true))
      else P(Index ~ ("//" ~ CharsWhile(_ != '\n', 0) | "/*" ~ (!"*/" ~ AnyChar).rep ~ ("*/" | End)) ~ Index)
        .map((s, e) => Opaque(s, e, comment = true))

    private def quoted[$: P](delimiter: String, multiline: Boolean): P[Unit] =
      P(delimiter ~ (("\\" ~ AnyChar) | (!delimiter ~ (if multiline then AnyChar else CharPred(_ != '\n')))).rep ~ (delimiter | End | &("\n")))

    private def string[$: P]: P[Token] =
      P(Index ~ (quoted("\"\"\"", multiline = true) | (if python then quoted("'''", multiline = true) else Fail) |
        quoted("\"", multiline = false) | quoted("'", multiline = false)) ~ Index).map((s, e) => Opaque(s, e, comment = false))

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

  def tokens(source: String, python: Boolean): Seq[Token] =
    fastparse.parse(source, context => (if python then pythonLexer else javaLexer).program(using context)) match {
      case Parsed.Success(value, _) => value
      case failure: Parsed.Failure => throw IllegalStateException(s"The source lexer must accept any text: ${failure.msg}")
    }

  private def flatten(values: Seq[Token]): Seq[Token] = values.flatMap {
    case group: Group => group +: flatten(group.children)
    case other => Seq(other)
  }

  /** Comments and string contents become spaces; line breaks and offsets are kept. */
  def mask(source: String, python: Boolean): String = {
    val result = source.toCharArray
    flatten(tokens(source, python)).foreach {
      case Opaque(start, end, _) => (start until end).foreach(i => if result(i) != '\n' && result(i) != '\r' then result(i) = ' ')
      case _ => ()
    }
    new String(result)
  }

  def remove(source: String, ranges: Seq[(Int, Int)]): String = {
    val outer = ranges.sortBy(_._1).foldLeft(Vector.empty[(Int, Int)]) { (kept, next) =>
      if kept.lastOption.exists(_._2 >= next._2) then kept else kept :+ next
    }
    outer.reverse.foldLeft(source) { case (text, (start, end)) => text.take(start) + text.drop(end) }
  }

  def dedent(source: String): String = {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toVector
    val indent = lines.filter(_.trim.nonEmpty).map(_.takeWhile(c => c == ' ' || c == '\t').length).minOption.getOrElse(0)
    lines.map(_.drop(indent)).mkString("\n").trim
  }
  def indent(source: String, spaces: Int = 4): String = source.linesIterator.map(" " * spaces + _).mkString("\n")

  /** Tokens that carry code: no whitespace and no comments. */
  def significant(values: Seq[Token]): Seq[Token] = values.filter {
    case symbol: Symbol => !symbol.isSpace
    case Opaque(_, _, comment) => !comment
    case _ => true
  }

  // Python: logical lines (brackets and strings may span physical lines) and indented blocks.

  private final case class Line(start: Int, end: Int, indent: Int, tokens: Seq[Token])

  private def pythonLines(source: String): Vector[Line] = {
    val all = tokens(source, python = true)
    val breaks = all.collect { case Symbol(start, _, "\n") => start }
    val starts = 0 +: breaks.map(_ + 1)
    starts.zip(breaks :+ source.length).map { (start, end) =>
      val inLine = all.filter(token => token.start >= start && token.end <= end)
      Line(start, end, source.substring(start, end).takeWhile(c => c == ' ' || c == '\t').length, inLine)
    }.toVector
  }

  def pythonBlocks(source: String, kind: String): Vector[Block] = {
    val lines = pythonLines(source)
    lines.zipWithIndex.flatMap { (line, index) =>
      significant(line.tokens).dropWhile { case Word(_, _, "async") => true; case _ => false } match {
        case Seq(Word(_, _, `kind`), Word(_, _, name), rest*) =>
          val end = lines.drop(index + 1).find(next => significant(next.tokens).nonEmpty && next.indent <= line.indent)
            .map(_.start).getOrElse(source.length)
          val bodyStart = rest.collect { case Symbol(_, colonEnd, ":") => colonEnd }.lastOption.getOrElse(line.end)
          Some(Block(name, line.start, end, source.substring(line.start, bodyStart).trim,
            dedent(source.substring(bodyStart, end)), dedent(source.substring(line.start, end)), line.indent))
        case _ => None
      }
    }
  }

  // Java: class declarations, wherever they occur, and the members of a class body.

  private val classModifiers = Set("public", "private", "protected", "static", "final", "abstract", "strictfp")

  def javaClasses(source: String): Vector[Block] = {
    def find(values: Seq[Token]): Vector[Block] = {
      val code = significant(values).toVector
      val here = code.indices.flatMap { index =>
        (code(index), code.lift(index + 1)) match {
          case (Word(classStart, _, "class"), Some(Word(_, _, name))) =>
            val start = code.take(index).reverse.takeWhile {
              case Word(_, _, modifier) => classModifiers(modifier)
              case _ => false
            }.lastOption.map(_.start).getOrElse(classStart)
            val body = code.drop(index + 2).takeWhile {
              case Symbol(_, _, ";") => false
              case _ => true
            }.collectFirst { case group: Group if group.open == '{' => group }
            Some(body.fold(Block(name, start, source.length, source.substring(start), "", source.substring(start), 0)) { group =>
              Block(name, start, group.end, source.substring(start, group.start),
                source.substring(group.start + 1, if group.closed then group.end - 1 else group.end), source.substring(start, group.end), 0)
            })
          case _ => None
        }
      }.toVector
      here ++ values.collect { case group: Group => find(group.children) }.flatten
    }
    find(tokens(source, python = false)).sortBy(_.start)
  }

  private def extract(source: String, blocks: Vector[Block], python: Boolean, warn: String => Unit): Parts = {
    val classes = blocks.foldLeft(VectorMap.empty[BeEntityName, String]) { (saved, block) =>
      val name = BeEntityName.fromCodeString(block.name)
      if saved.keys.exists(_.universalInterpretation() == name.universalInterpretation()) then { warn(s"Duplicate class '${block.name}' ignored; keeping the first definition."); saved }
      else {
        val children = blocks.filter(child => child.start > block.start && child.end <= block.end)
        val text = remove(source.substring(block.start, block.end), children.map(c => (c.start - block.start, c.end - block.start)))
        saved.updated(name, if python then repairEmptyBlocks(dedent(text)) else text)
      }
    }
    val remaining = remove(source, blocks.map(b => b.start -> b.end))
    Parts(classes, if python then repairEmptyBlocks(remaining).trim else remaining.trim)
  }

  /** A block header left without a body after hoisting receives `pass`. */
  private def repairEmptyBlocks(source: String): String = {
    val lines = pythonLines(source)
    lines.zipWithIndex.flatMap { (line, index) =>
      val text = source.substring(line.start, line.end)
      val opensBlock = significant(line.tokens).lastOption.exists { case Symbol(_, _, ":") => true; case _ => false }
      val next = lines.drop(index + 1).find(candidate => significant(candidate.tokens).nonEmpty)
      if opensBlock && next.forall(_.indent <= line.indent) then Vector(text, " " * (line.indent + 4) + "pass") else Vector(text)
    }.mkString("\n")
  }

  def java(source: String, warn: String => Unit = logger.logWarn): Parts = {
    val parts = extract(source, javaClasses(source), false, warn)
    // Package and import declarations, and remaining comments, stay as the first class's preamble.
    val code = significant(tokens(parts.remaining, python = false)).toVector
    val imports = code.indices.collect {
      case index if code(index).isInstanceOf[Word] && Set("package", "import")(code(index).asInstanceOf[Word].text) =>
        val end = code.drop(index).collectFirst { case Symbol(_, semicolonEnd, ";") => semicolonEnd }.getOrElse(parts.remaining.length)
        code(index).start -> end
    }.toVector
    val rest = remove(parts.remaining, imports)
    val onlyTrivia = significant(tokens(rest, python = false)).isEmpty
    val preamble = (imports.map((start, end) => parts.remaining.substring(start, end)) ++
      Option.when(onlyTrivia)(rest.trim)).filter(_.nonEmpty).mkString("\n")
    if preamble.isEmpty then parts
    else {
      val (name, body) = parts.definedClasses.headOption.getOrElse(
        BeEntityName.fromCodeString("Main") -> "class Main {}")
      Parts(parts.definedClasses.updated(name, preamble + "\n" + body), if onlyTrivia then "" else rest.trim)
    }
  }
  def python(source: String, warn: String => Unit = logger.logWarn): Parts = extract(source, pythonBlocks(source, "class"), true, warn)

  /** Direct class members, separated without parsing their bodies: a member ends with `;` or with
    * a `{…}` body that is not part of an initializer.
    */
  def javaMembers(source: String): Vector[String] =
    tokens(source, python = false).collectFirst { case group: Group if group.open == '{' => group } match {
      case None => Vector.empty
      case Some(body) =>
        val (members, start, _) = body.children.foldLeft((Vector.empty[String], body.start + 1, false)) {
          case ((done, start, assigned), Symbol(_, end, ";")) => (done :+ source.substring(start, end).trim, end, false)
          case ((done, start, false), group: Group) if group.open == '{' => (done :+ source.substring(start, group.end).trim, group.end, false)
          case ((done, start, _), Symbol(_, _, "=")) => (done, start, true)
          case (state, _) => state
        }
        val end = if body.closed then body.end - 1 else body.end
        (members :+ source.substring(start, end).trim).filter(_.nonEmpty)
    }

  def scope(member: String): MemberScope =
    if header(member).exists { case Word(_, _, "static") => true; case _ => false } then MemberScope.Static else MemberScope.Instance

  /** A member's tokens before its body or initializer. */
  def header(member: String): Seq[Token] =
    significant(tokens(member, python = false)).takeWhile {
      case group: Group => group.open != '{'
      case Symbol(_, _, "=") => false
      case _ => true
    }

  /** The name of a declared method: a word directly followed by its parameter list, after a type. */
  def methodName(member: String): Option[String] = {
    val code = header(member).toVector
    code.indices.collectFirst {
      case index if index >= 1 && (code(index) match { case group: Group => group.open == '('; case _ => false }) =>
        (code(index - 1), code.lift(index - 2)) match {
          // The type ends in a word, a generic `>` or an array `[]`.
          case (Word(nameStart, _, name), Some(before))
              if (before match { case _: Word => true; case Symbol(_, _, text) => text == ">"; case group: Group => group.open == '['; case _ => false }) &&
                member.substring(before.end, nameStart).exists(_.isWhitespace) => Some(name)
          case _ => None
        }
    }.flatten
  }

  /** The dedented text inside a member's `{…}` body. */
  def body(member: String): Option[String] =
    significant(tokens(member, python = false)).collectFirst { case group: Group if group.open == '{' && group.closed => group }
      .map(group => dedent(member.substring(group.start + 1, group.end - 1)))

  /** A field or local declaration: modifiers, a type, a name and an optional initializer. */
  final case class Declaration(javaType: String, name: String, initializer: Option[String])

  private val statementKeywords = Set("return", "new", "throw", "else", "case", "yield")

  def declaration(member: String): Option[Declaration] = {
    val code = significant(tokens(member, python = false)).toVector
    val afterModifiers = code.dropWhile { case Word(_, _, modifier) => classModifiers(modifier); case _ => false }
    val equals = afterModifiers.indexWhere { case Symbol(_, _, "=") => true; case _ => false }
    val target = (if equals < 0 then afterModifiers else afterModifiers.take(equals)).filterNot { case Symbol(_, _, ";") => true; case _ => false }
    target.lastOption match {
      case Some(Word(nameStart, _, name)) if target.size >= 2 =>
        target.head match {
          case Word(typeStart, _, typeWord) if !statementKeywords(typeWord) && member.substring(target(target.size - 2).end, nameStart).exists(_.isWhitespace) &&
              target.init.forall { case Word(_, _, _) | Symbol(_, _, "<" | ">" | "[" | "]" | "," | ".") => true; case group: Group => group.open == '['; case _ => false } =>
            val initializer = Option.when(equals >= 0)(member.substring(afterModifiers(equals).end).trim.stripSuffix(";").trim)
            Some(Declaration(member.substring(typeStart, target(target.size - 2).end), name, initializer))
          case _ => None
        }
      case _ => None
    }
  }
}
