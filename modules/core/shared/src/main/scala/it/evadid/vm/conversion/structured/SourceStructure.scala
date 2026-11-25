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

  def tokens(source: String, python: Boolean): Seq[Token] =
    fastparse.parse(source, context => (if python then pythonLexer else javaLexer).program(using context)) match {
      case Parsed.Success(value, _) => value
      case failure: Parsed.Failure => throw IllegalStateException(s"The source lexer must accept any text: ${failure.msg}")
    }

  private def flatten(values: Seq[Token]): Seq[Token] = values.flatMap {
    case group: Group => group +: flatten(group.children)
    case other => Seq(other)
  }

  /** Every bracket, string and comment is closed and no closing bracket is left over. */
  def balanced(source: String, python: Boolean = false): Boolean = flatten(tokens(source, python)).forall {
    case group: Group => group.closed
    case opaque: Opaque => opaque.closed
    case Symbol(_, _, text) => !")]}".contains(text)
    case _ => true
  }

  /** Comments and string contents become spaces; line breaks and offsets are kept. */
  def mask(source: String, python: Boolean): String = {
    val result = source.toCharArray
    flatten(tokens(source, python)).foreach {
      case Opaque(start, end, _, _) => (start until end).foreach(i => if result(i) != '\n' && result(i) != '\r' then result(i) = ' ')
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

  /** Removes shared leading spaces and tabs; unlike Python's tokenizer it keeps tabs in the text. */
  def dedent(source: String): String = {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toVector
    val indent = lines.filter(_.trim.nonEmpty).map(_.takeWhile(c => c == ' ' || c == '\t').length).minOption.getOrElse(0)
    lines.map(_.drop(indent)).mkString("\n").trim
  }
  /** Indents every non-empty line, so blank lines carry no trailing whitespace. */
  def indent(source: String, spaces: Int = 4): String =
    source.linesIterator.map(line => if line.isEmpty then line else " " * spaces + line).mkString("\n")

  /** Tokens that carry code: no whitespace and no comments. */
  def significant(values: Seq[Token]): Seq[Token] = values.filter {
    case symbol: Symbol => !symbol.isSpace
    case Opaque(_, _, comment, _) => !comment
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

  /** A Python logical line holding code: its 1-based physical row, indentation and code without comments. */
  final case class CodeLine(row: Int, indent: Int, code: String)

  def pythonCodeLines(source: String): Vector[CodeLine] =
    pythonLines(source).flatMap { line =>
      val code = significant(line.tokens)
      Option.when(code.nonEmpty)(CodeLine(1 + source.substring(0, line.start).count(_ == '\n'), line.indent,
        source.substring(code.head.start, code.last.end)))
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
        val inner = source.substring(block.start, block.end)
        val ranges = children.map(c => (c.start - block.start, c.end - block.start))
        saved.updated(name, if python then dedent(hoist(inner, ranges)) else remove(inner, ranges))
      }
    }
    val ranges = blocks.map(b => b.start -> b.end)
    Parts(classes, if python then hoist(source, ranges).trim else remove(source, ranges).trim)
  }

  /** Python source as the parser reads it: a block header without a body, such as the `if x:` being typed, gets a
    * `pass`, so the rest of the program stays readable. The `pass` fills the blank line below the header, which keeps
    * the line numbers; only without such a line it is inserted. The learner's text itself is never changed.
    */
  def withEmptyBodies(source: String): String = {
    val code = pythonLines(source).filter(line => significant(line.tokens).nonEmpty)
    val headers = code.zipWithIndex.collect {
      case (line, index) if code.lift(index + 1).forall(_.indent <= line.indent) &&
        significant(line.tokens).lastOption.exists { case Symbol(_, _, ":") => true; case _ => false } => line
    }
    headers.sortBy(_.start).reverse.foldLeft(source) { (text, header) =>
      val body = " " * (header.indent + 4) + "pass"
      val nextStart = header.end + 1
      val nextEnd = text.indexOf('\n', nextStart) match { case -1 => text.length; case at => at }
      if nextStart <= text.length && header.end < text.length && text.substring(nextStart, nextEnd).trim.isEmpty then
        text.take(nextStart) + body + text.drop(nextEnd)
      else text.take(header.end) + "\n" + body + text.drop(header.end)
    }
  }

  /** Removes the hoisted Python blocks `ranges` (whole lines). A block header whose entire body is removed keeps a
    * body: the first removed block becomes `pass` at its indentation. Headers that had no body before, such as the
    * `if x:` being typed, stay as they are.
    */
  private def hoist(source: String, ranges: Seq[(Int, Int)]): String = {
    val outer = ranges.sortBy(_._1).foldLeft(Vector.empty[(Int, Int)]) { (kept, next) =>
      if kept.lastOption.exists(_._2 >= next._2) then kept else kept :+ next
    }
    val code = pythonLines(source).filter(line => significant(line.tokens).nonEmpty)
    def rangeOf(line: Line): Option[(Int, Int)] =
      significant(line.tokens).headOption.flatMap(token => outer.find((start, end) => token.start >= start && token.start < end))
    val emptied = code.zipWithIndex.flatMap { (line, index) =>
      val opensBlock = significant(line.tokens).lastOption.exists { case Symbol(_, _, ":") => true; case _ => false }
      val body = code.drop(index + 1).takeWhile(_.indent > line.indent)
      Option.when(opensBlock && rangeOf(line).isEmpty && body.nonEmpty && body.forall(rangeOf(_).nonEmpty))(body.head).flatMap(rangeOf)
    }.toSet
    outer.reverse.foldLeft(source) { case (text, range @ (start, end)) =>
      val removed = text.substring(start, end)
      val replacement = if emptied(range) then removed.takeWhile(c => c == ' ' || c == '\t') + "pass" + (if removed.endsWith("\n") then "\n" else "") else ""
      text.take(start) + replacement + text.drop(end)
    }
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

  /** Spans of the direct members of a class body: a member ends with `;` or with a `{…}` body that is
    * not part of an initializer. Spans start at the member's first code token.
    */
  private def memberSpans(source: String, body: Group): Vector[(Int, Int)] = {
    final case class State(done: Vector[(Int, Int)] = Vector.empty, start: Option[Int] = None, assigned: Boolean = false) {
      def begin(token: Token): State = if start.nonEmpty then this else copy(start = Some(token.start))
      def finish(end: Int): State = State(done ++ start.map(_ -> end))
    }
    val state = body.children.foldLeft(State()) { (state, token) => token match {
      case Opaque(_, _, true, _) => state
      case symbol: Symbol if symbol.isSpace => state
      case Symbol(_, end, ";") => state.begin(token).finish(end)
      case group: Group if group.open == '{' && !state.assigned => state.begin(group).finish(group.end)
      case Symbol(_, _, "=") => state.begin(token).copy(assigned = true)
      case other => state.begin(other)
    } }
    state.start.fold(state.done)(start => state.done :+ (start -> (if body.closed then body.end - 1 else body.end)))
  }

  private def firstBody(values: Seq[Token]): Option[Group] = values.collectFirst { case group: Group if group.open == '{' => group }

  /** Direct class members as source text. */
  def javaMembers(source: String): Vector[String] =
    firstBody(tokens(source, python = false)).toVector.flatMap(body =>
      memberSpans(source, body).map((start, end) => source.substring(start, end).trim)).filter(_.nonEmpty)

  /** A method in a class body: its signature parts and the offsets from its first token to its `}`. */
  final case class MethodSpan(name: String, returnType: String, parameters: String, start: Int, end: Int)
  /** A class whose body is closed: offsets of its `{` and `}` and its methods. */
  final case class ClassSpan(name: String, bodyOpen: Int, bodyClose: Int, methods: Vector[MethodSpan])

  private val memberModifiers = classModifiers ++ Set("synchronized", "native", "default")

  def javaClassSpans(source: String): Vector[ClassSpan] = {
    def find(values: Seq[Token]): Vector[ClassSpan] = {
      val code = significant(values).toVector
      val here = code.indices.flatMap { index =>
        (code(index), code.lift(index + 1)) match {
          case (Word(_, _, "class"), Some(Word(_, _, name))) =>
            code.drop(index + 2).takeWhile { case Symbol(_, _, ";") => false; case _ => true }
              .collectFirst { case group: Group if group.open == '{' && group.closed => group }
              .map(body => ClassSpan(name, body.start, body.end - 1, memberSpans(source, body).flatMap { (start, end) =>
                val member = source.substring(start, end)
                for {
                  name <- methodName(member)
                  parameters <- header(member).collectFirst { case group: Group if group.open == '(' && group.closed => group }
                  _ <- firstBody(tokens(member, python = false)).filter(_.closed)
                } yield {
                  val typeStart = header(member).dropWhile { case Word(_, _, modifier) => memberModifiers(modifier); case _ => false }
                    .headOption.map(_.start).getOrElse(0)
                  val nameStart = header(member).collect { case word @ Word(_, _, `name`) if word.end <= parameters.start => word }.last.start
                  MethodSpan(name, member.substring(typeStart, nameStart).trim, member.substring(parameters.start + 1, parameters.end - 1).trim,
                    start, end)
                }
              }))
          case _ => None
        }
      }.toVector
      here ++ values.collect { case group: Group => find(group.children) }.flatten
    }
    find(tokens(source, python = false)).sortBy(_.bodyOpen)
  }

  // Class outlines: the structure a UML class diagram shows, derived from the source text alone.

  enum Visibility {
    case Public, Protected, Package, Private

    /** The UML visibility marker. */
    def symbol: String = this match {
      case Public => "+"
      case Protected => "#"
      case Package => "~"
      case Private => "-"
    }
  }

  /** A declared parameter; `javaType` keeps generics, arrays and varargs as written. */
  final case class Param(javaType: String, name: String)

  /** A direct member of a class body. Offsets span from its first code token to its end, like [[MethodSpan]]. */
  sealed trait MemberOutline {
    def visibility: Visibility
    def start: Int
    def end: Int
  }

  object MemberOutline {
    final case class Field(visibility: Visibility, isStatic: Boolean, isFinal: Boolean, javaType: String, name: String,
                           initializer: Option[String], start: Int, end: Int) extends MemberOutline

    final case class Constructor(className: String, visibility: Visibility, params: Vector[Param], parameters: String,
                                 start: Int, end: Int, hasBody: Boolean) extends MemberOutline

    final case class Method(visibility: Visibility, isStatic: Boolean, isAbstract: Boolean, returnType: String, name: String,
                            params: Vector[Param], parameters: String, start: Int, end: Int, hasBody: Boolean) extends MemberOutline
  }

  /** A class or interface whose body is closed, with its supertypes and members in source order. */
  final case class ClassOutline(name: String, isInterface: Boolean, isAbstract: Boolean, extendsNames: Vector[String],
                                implementsNames: Vector[String], bodyOpen: Int, bodyClose: Int, members: Vector[MemberOutline]) {
    def fields: Vector[MemberOutline.Field] = members.collect { case field: MemberOutline.Field => field }
    def constructors: Vector[MemberOutline.Constructor] = members.collect { case constructor: MemberOutline.Constructor => constructor }
    def methods: Vector[MemberOutline.Method] = members.collect { case method: MemberOutline.Method => method }
  }

  def javaClassOutlines(source: String): Vector[ClassOutline] = {
    def find(values: Seq[Token]): Vector[ClassOutline] = {
      val code = significant(values).toVector
      val here = code.indices.flatMap { index =>
        (code(index), code.lift(index + 1)) match {
          case (Word(_, _, keyword @ ("class" | "interface")), Some(Word(_, _, name))) if !precededByDot(code, index) =>
            val modifiers = code.take(index).reverse.takeWhile {
              case Word(_, _, modifier) => classModifiers(modifier)
              case _ => false
            }.collect { case Word(_, _, modifier) => modifier }.toSet
            val header = code.drop(index + 2).takeWhile {
              case Symbol(_, _, ";") => false
              case group: Group => group.open != '{'
              case _ => true
            }
            code.drop(index + 2 + header.size).headOption.collect { case body: Group if body.open == '{' && body.closed => body }.map { body =>
              val isInterface = keyword == "interface"
              ClassOutline(name, isInterface, isInterface || modifiers("abstract"),
                supertypes(header, "extends"), supertypes(header, "implements"), body.start, body.end - 1,
                memberSpans(source, body).flatMap((start, end) => memberOutlines(source, start, end, name, isInterface)))
            }
          case _ => None
        }
      }.toVector
      here ++ values.collect { case group: Group => find(group.children) }.flatten
    }
    find(tokens(source, python = false)).sortBy(_.bodyOpen)
  }

  private def precededByDot(code: Vector[Token], index: Int): Boolean =
    code.lift(index - 1).exists { case Symbol(_, _, ".") => true; case _ => false }

  /** Type names after `keyword` in a class header, without their type arguments. */
  private def supertypes(header: Seq[Token], keyword: String): Vector[String] = {
    val clauses = Set("extends", "implements", "permits")
    val afterKeyword = header.dropWhile { case Word(_, _, `keyword`) => false; case _ => true }.drop(1)
      .takeWhile { case Word(_, _, word) => !clauses(word); case _ => true }
    afterKeyword.foldLeft((Vector.empty[String], 0, true)) { case ((names, depth, expectName), token) => token match {
      case Symbol(_, _, "<") => (names, depth + 1, false)
      case Symbol(_, _, ">") => (names, depth - 1, false)
      case Symbol(_, _, ",") if depth == 0 => (names, depth, true)
      case Symbol(_, _, ".") if depth == 0 => (names.dropRight(1), depth, true)
      case Word(_, _, word) if depth == 0 && expectName => (names :+ word, depth, false)
      case _ => (names, depth, expectName)
    } }._1
  }

  /** Drops leading annotations such as `@Override` or `@SuppressWarnings("x")`. */
  private def withoutAnnotations(code: Seq[Token]): Seq[Token] = code match {
    case Symbol(_, _, "@") +: Word(_, _, "interface") +: _ => code
    case Symbol(_, _, "@") +: (_: Word) +: rest =>
      // A qualified name such as `@java.lang.Override` continues with `.` and a word.
      def afterQualifiedName(tokens: Seq[Token]): Seq[Token] = tokens match {
        case Symbol(_, _, ".") +: (_: Word) +: next => afterQualifiedName(next)
        case other => other
      }
      withoutAnnotations(afterQualifiedName(rest) match {
        case (group: Group) +: next if group.open == '(' => next
        case other => other
      })
    case other => other
  }

  /** Splits text at commas outside `<…>` and brackets. */
  private def splitTopLevel(text: String): Vector[String] = {
    val masked = mask(text, python = false)
    val (parts, last, _) = masked.indices.foldLeft((Vector.empty[String], 0, 0)) { case ((parts, from, depth), index) =>
      masked.charAt(index) match {
        case '<' | '(' | '[' | '{' => (parts, from, depth + 1)
        case '>' | ')' | ']' | '}' => (parts, from, depth - 1)
        case ',' if depth == 0 => (parts :+ text.substring(from, index), index + 1, depth)
        case _ => (parts, from, depth)
      }
    }
    (parts :+ text.substring(last)).map(_.trim).filter(_.nonEmpty)
  }

  private def params(parameters: String): Vector[Param] =
    splitTopLevel(parameters).map { parameter =>
      declaration(parameter).fold(Param(parameter, ""))(value => Param(value.javaType, value.name))
    }

  private def memberOutlines(source: String, start: Int, end: Int, className: String, inInterface: Boolean): Vector[MemberOutline] = {
    val member = source.substring(start, end)
    val code = withoutAnnotations(header(member)).toVector
    val modifiers = code.takeWhile { case Word(_, _, word) => memberModifiers(word); case _ => false }
      .collect { case Word(_, _, word) => word }.toSet
    val rest = code.drop(modifiers.size)
    val visibility =
      if modifiers("public") then Visibility.Public
      else if modifiers("private") then Visibility.Private
      else if modifiers("protected") then Visibility.Protected
      else if inInterface then Visibility.Public
      else Visibility.Package
    val body = significant(tokens(member, python = false)).collectFirst { case group: Group if group.open == '{' => group }
    val hasBody = body.exists(_.closed) && !member.trim.endsWith(";")
    val parameterGroup = rest.indexWhere { case group: Group => group.open == '('; case _ => false }
    if rest.isEmpty || rest.exists { case Word(_, _, "class" | "interface" | "enum" | "record") => true; case _ => false } then Vector.empty
    else if parameterGroup >= 0 then {
      val group = rest(parameterGroup).asInstanceOf[Group]
      val parameters = member.substring(group.start + 1, if group.closed then group.end - 1 else group.end).trim
      (parameterGroup, rest.lift(parameterGroup - 1)) match {
        case (1, Some(Word(_, _, `className`))) =>
          Vector(MemberOutline.Constructor(className, visibility, params(parameters), parameters, start, end, hasBody))
        case (index, Some(Word(nameStart, _, name))) if index >= 2 =>
          Vector(MemberOutline.Method(visibility, modifiers("static"), modifiers("abstract") || inInterface && !hasBody,
            member.substring(rest.head.start, nameStart).trim, name, params(parameters), parameters, start, end, hasBody))
        case _ => Vector.empty
      }
    } else {
      // Fields: one declaration, or several sharing a type such as `int a, b = 2;`.
      val declarations = splitTopLevel(member.substring(rest.head.start).trim.stripSuffix(";"))
      declarations.headOption.flatMap(declaration).toVector.flatMap { first =>
        val others = declarations.drop(1).map(text => declaration(s"${first.javaType} $text"))
        Option.when(others.forall(_.isDefined))((first +: others.flatten).map(value =>
          MemberOutline.Field(visibility, modifiers("static") || inInterface, modifiers("final") || inInterface,
            value.javaType, value.name, value.initializer, start, end)
        )).getOrElse(Vector.empty)
      }
    }
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
