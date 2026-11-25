package it.evadid.homepage.webElements.code

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.conversion.structured.SourceStructure.{Group, Opaque, Param, Token, Visibility}

import JavaMethodSource.*

/** Reads Python source into the class diagram's model and writes the members its dialogs add.
  *
  * Module-level functions form the module box. Classes contribute their methods (`__init__` is the
  * constructor, `@staticmethod` and `@classmethod` are static), their class attributes and the `self.x`
  * attributes their `__init__` assigns. Visibility follows the naming convention: `_name` is protected,
  * `__name` private. Functions without a result get no `-> None`.
  */
private[code] object PythonStructure {

  /** A logical line: physical lines joined while brackets or strings are open or a line ends with a backslash.
    * `start` is its first code character, `end` the end of its last code character; `code` is the masked text
    * of that range (comments blanked, string contents replaced), so brackets and keywords can be read safely.
    * A `comment` line holds only a comment: it belongs to the block it is indented into, but is no statement.
    */
  private[code] final case class Line(start: Int, end: Int, indent: Int, code: String, comment: Boolean = false)

  /** A statement with its decorators and the deeper lines of its block. */
  private[code] final case class Statement(decorators: Vector[Line], line: Line, body: Vector[Line]) {
    def start: Int = decorators.headOption.fold(line.start)(_.start)
    def end: Int = body.lastOption.fold(line.end)(_.end)
  }

  private val Indent = "    "
  private val DefHeader = """(?s)(?:async\s+)?def\s+([A-Za-z_]\w*)\s*\(""".r
  private val ClassHeader = """(?s)class\s+([A-Za-z_]\w*)\s*""".r
  private val SelfAttribute = """self\.([A-Za-z_]\w*)""".r
  private val Identifier = "[A-Za-z_][A-Za-z0-9_]*"

  private val keywords = Set("False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
    "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
    "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield")

  /** One parsed source: its masked text, logical lines and top-level statements. */
  private[code] final class Parsed(val source: String) {
    private val tokens = SourceStructure.tokens(source, python = true)
    private val stringRanges = Vector.newBuilder[(Int, Int)]
    val masked: String = {
      val chars = source.toCharArray
      def walk(values: Seq[Token]): Unit = values.foreach {
        case Opaque(start, end, comment, _) =>
          if !comment then stringRanges += start -> end
          (start until end).foreach(i => if chars(i) != '\n' && chars(i) != '\r' then chars(i) = if comment then ' ' else '_')
        case group: Group => walk(group.children)
        case _ => ()
      }
      walk(tokens)
      new String(chars)
    }
    private val strings = stringRanges.result()
    private def insideString(position: Int): Boolean = strings.exists((start, end) => start < position && position < end)

    val lines: Vector[Line] = {
      val result = Vector.newBuilder[Line]
      var position = 0
      var depth = 0
      var open = Option.empty[(Int, Int)]
      var lastEnd = 0
      while position <= masked.length do {
        val lineEnd = masked.indexOf('\n', position) match {
          case -1 => masked.length
          case index => index
        }
        val physical = masked.substring(position, lineEnd)
        val firstCode = physical.indexWhere(c => !c.isWhitespace)
        val text = source.substring(position, lineEnd)
        val firstText = text.indexWhere(c => !c.isWhitespace)
        if firstCode < 0 && open.isEmpty && firstText >= 0 then
          result += Line(position + firstText, position + text.lastIndexWhere(c => !c.isWhitespace) + 1,
            indentation(text.substring(0, firstText)), "", comment = true)
        else if firstCode >= 0 || open.nonEmpty then {
          if open.isEmpty then open = Some((position + firstCode, indentation(physical.substring(0, firstCode))))
          physical.foreach { c =>
            if c == '(' || c == '[' || c == '{' then depth += 1
            else if c == ')' || c == ']' || c == '}' then depth = math.max(0, depth - 1)
          }
          if firstCode >= 0 then lastEnd = position + physical.lastIndexWhere(c => !c.isWhitespace) + 1
          val continues = depth > 0 || insideString(lineEnd) || (firstCode >= 0 && masked.charAt(lastEnd - 1) == '\\')
          if !continues then open.foreach { (start, indent) =>
            result += Line(start, lastEnd, indent, masked.substring(start, lastEnd))
            open = None
          }
        }
        position = lineEnd + 1
      }
      open.foreach((start, indent) => result += Line(start, lastEnd, indent, masked.substring(start, lastEnd)))
      result.result()
    }

    val top: Vector[Statement] = statements(lines)

    def original(start: Int, end: Int): String = source.substring(start, end).trim

    /** The matching closing bracket of the one at `open`. */
    def closing(open: Int): Option[Int] = {
      var depth = 0
      var index = open
      while index < masked.length do {
        val c = masked.charAt(index)
        if c == '(' || c == '[' || c == '{' then depth += 1
        else if c == ')' || c == ']' || c == '}' then {
          depth -= 1
          if depth == 0 then return Some(index)
        }
        index += 1
      }
      None
    }

    /** Positions of `separator` outside brackets between `from` and `until`. */
    def topLevel(from: Int, until: Int, separator: Char => Boolean): Vector[Int] = {
      var depth = 0
      (from until until).filter { index =>
        val c = masked.charAt(index)
        if c == '(' || c == '[' || c == '{' then { depth += 1; false }
        else if c == ')' || c == ']' || c == '}' then { depth -= 1; false }
        else depth == 0 && separator(c)
      }.toVector
    }

    /** Comma separated parts between `from` and `until`, as (start, end) ranges. */
    def parts(from: Int, until: Int): Vector[(Int, Int)] = {
      val bounds = (from - 1) +: topLevel(from, until, _ == ',') :+ until
      bounds.sliding(2).collect { case Seq(a, b) => (a + 1, b) }.filter((a, b) => original(a, b).nonEmpty).toVector
    }
  }

  private def indentation(prefix: String): Int = prefix.foldLeft(0)((column, c) => if c == '\t' then column / 8 * 8 + 8 else column + 1)

  /** Statements of a suite: code lines at the suite's indentation, each with its decorators and deeper lines.
    * Comment lines indented into a block belong to it; others are skipped.
    */
  private[code] def statements(lines: Vector[Line]): Vector[Statement] = {
    val indent = lines.find(!_.comment).fold(0)(_.indent)
    val result = Vector.newBuilder[Statement]
    var decorators = Vector.empty[Line]
    var index = 0
    while index < lines.length do {
      val line = lines(index)
      if line.comment then index += 1
      else {
        var next = index + 1
        while next < lines.length && (lines(next).comment || lines(next).indent > math.max(indent, line.indent)) do next += 1
        // Comments after the block that are not indented into it stay outside.
        while next > index + 1 && lines(next - 1).comment && lines(next - 1).indent <= line.indent do next -= 1
        val body = lines.slice(index + 1, next)
        if line.code.startsWith("@") && body.forall(_.comment) then decorators :+= line
        else {
          result += Statement(decorators, line, body)
          decorators = Vector.empty
        }
        index = next
      }
    }
    result.result()
  }

  private[code] def visibilityOf(name: String): Visibility =
    if name.startsWith("__") && name.endsWith("__") then Visibility.Public
    else if name.startsWith("__") then Visibility.Private
    else if name.startsWith("_") then Visibility.Protected
    else Visibility.Public

  private def dunder(name: String): Boolean = name.length > 4 && name.startsWith("__") && name.endsWith("__")

  /** The name without the underscores that encode its visibility. */
  private[code] def bareName(name: String): String = if dunder(name) then name else name.dropWhile(_ == '_')

  private[code] def memberName(name: String, visibility: Visibility): String =
    if dunder(name) then name
    else {
      val bare = bareName(name)
      visibility match {
        case Visibility.Private => "__" + bare
        case Visibility.Protected => "_" + bare
        case _ => bare
      }
    }

  // Reading.

  private def isDefinition(statement: Statement): Boolean =
    DefHeader.findPrefixMatchOf(statement.line.code).nonEmpty || ClassHeader.findPrefixMatchOf(statement.line.code).nonEmpty

  private def decoratorNames(statement: Statement): Set[String] =
    statement.decorators.map(_.code.drop(1).takeWhile(_ != '(').trim.split('.').last).toSet

  private def parameter(parsed: Parsed, start: Int, end: Int): Param = {
    val separators = parsed.topLevel(start, end, c => c == ':' || c == '=')
    val nameEnd = separators.headOption.getOrElse(end)
    val annotation = separators.headOption.filter(parsed.masked.charAt(_) == ':').map { colon =>
      parsed.original(colon + 1, separators.find(index => index > colon && parsed.masked.charAt(index) == '=').getOrElse(end))
    }
    Param(annotation.getOrElse(""), parsed.original(start, nameEnd))
  }

  private def function(parsed: Parsed, statement: Statement, className: String, inClass: Boolean): Option[JavaMethod] =
    DefHeader.findPrefixMatchOf(statement.line.code).flatMap { header =>
      val open = statement.line.start + header.end - 1
      parsed.closing(open).filter(_ < statement.line.end).map { close =>
        val colon = parsed.topLevel(close + 1, statement.line.end, _ == ':').headOption.getOrElse(statement.line.end)
        val arrow = parsed.masked.indexOf("->", close)
        val returnType = if arrow >= 0 && arrow < colon then parsed.original(arrow + 2, colon) else ""
        val params = parsed.parts(open + 1, close).map((start, end) => parameter(parsed, start, end))
          .filterNot(param => param.name == "/" || param.name == "*").toList
        val decorators = decoratorNames(statement)
        val isStatic = inClass && (decorators("staticmethod") || decorators("classmethod"))
        val explicit = if inClass && !decorators("staticmethod") then params.drop(1) else params
        val name = header.group(1)
        JavaMethod(className, name, returnType, parsed.original(open + 1, close), statement.start, statement.end,
          visibilityOf(name), isStatic, isConstructor = inClass && name == "__init__",
          isAbstract = decorators("abstractmethod"), params = explicit)
      }
    }

  /** `target`, its annotation and its value, when the statement is an assignment or a bare annotation. */
  private def assignment(parsed: Parsed, line: Line): Option[(String, Option[String], Option[String])] = {
    val code = line.code
    val separators = parsed.topLevel(line.start, line.end, c => c == ':' || c == '=').filter { index =>
      val c = parsed.masked.charAt(index)
      def at(offset: Int) = if index + offset >= 0 && index + offset < parsed.masked.length then parsed.masked.charAt(index + offset) else ' '
      c == ':' && at(1) != '=' || c == '=' && at(1) != '=' && !"=!<>:+-*/%&|^@".contains(at(-1))
    }
    separators.headOption.filterNot(_ => code.startsWith("@") || DefHeader.findPrefixMatchOf(code).nonEmpty).map { first =>
      val target = parsed.original(line.start, first)
      if parsed.masked.charAt(first) == ':' then {
        val equals = separators.find(_ > first)
        (target, Some(parsed.original(first + 1, equals.getOrElse(line.end))), equals.map(index => parsed.original(index + 1, line.end)))
      } else (target, None, Some(parsed.original(first + 1, line.end)))
    }.filter((target, _, value) => target.nonEmpty && value.forall(_.nonEmpty))
  }

  /** The type a literal or a constructor call suggests, for attributes without annotation. */
  private def inferred(value: String, params: List[Param]): String = {
    val text = value.trim
    params.find(_.name == text).map(_.javaType).filter(_.nonEmpty).getOrElse {
      if text.matches("-?\\d+") then "int"
      else if text.matches("-?\\d*\\.\\d+([eE]-?\\d+)?|-?\\d+[eE]-?\\d+") then "float"
      else if text == "True" || text == "False" then "bool"
      else if text.startsWith("\"") || text.startsWith("'") then "str"
      else if text.startsWith("[") then "list"
      else if text.startsWith("{") then "dict"
      else if text.matches("[A-Z]\\w*\\(.*") then text.takeWhile(_ != '(')
      else ""
    }
  }

  private def classAttributes(parsed: Parsed, className: String, body: Vector[Statement]): Vector[JavaField] =
    body.filter(statement => statement.body.isEmpty && statement.decorators.isEmpty).flatMap { statement =>
      assignment(parsed, statement.line).collect { case (target, annotation, value) if target.matches(Identifier) =>
        JavaField(className, visibilityOf(target), isStatic = true, isFinal = false,
          annotation.getOrElse(value.fold("")(inferred(_, Nil))), target, value, statement.line.start, statement.line.end, shared = false)
      }
    }

  private def instanceAttributes(parsed: Parsed, className: String, constructor: Option[(Statement, JavaMethod)]): Vector[JavaField] =
    constructor.toVector.flatMap { (statement, method) =>
      statements(statement.body).filter(_.body.isEmpty).flatMap { line =>
        assignment(parsed, line.line).collect { case (SelfAttribute(name), annotation, value) =>
          JavaField(className, visibilityOf(name), isStatic = false, isFinal = false,
            annotation.getOrElse(value.fold("")(inferred(_, method.params))), name, value, line.line.start, line.line.end, shared = false)
        }
      }.distinctBy(_.name)
    }

  private def classOf(parsed: Parsed, statement: Statement, library: Boolean): Option[JavaClass] =
    ClassHeader.findPrefixMatchOf(statement.line.code).map { header =>
      val name = header.group(1)
      val afterName = statement.line.start + header.end
      val bases = Option.when(afterName < parsed.masked.length && parsed.masked.charAt(afterName) == '(')(afterName)
        .flatMap(open => parsed.closing(open).map(close => parsed.parts(open + 1, close).map((a, b) => parsed.original(a, b)).toList))
        .getOrElse(Nil).filterNot(_.contains("=")).map(_.split('.').last.trim)
      val body = statements(statement.body)
      val definitions = body.flatMap(member => function(parsed, member, name, inClass = true).map(member -> _))
      def shown(visibility: Visibility) = !library || visibility == Visibility.Public
      val methods = definitions.map(_._2).filter(method => shown(method.visibility)).toList
      val fields = (classAttributes(parsed, name, body) ++ instanceAttributes(parsed, name, definitions.find(_._2.isConstructor)))
        .filter(field => shown(field.visibility)).toList
      JavaClass(name, statement.line.end, statement.end, methods, fields,
        isInterface = bases.exists(_.startsWith("Protocol")),
        isAbstract = bases.contains("ABC") || methods.exists(_.isAbstract),
        superclasses = bases.filterNot(base => Set("object", "ABC", "Protocol")(base) || base.contains("[")),
        library = library)
    }

  /** The module box first, then the classes; classes of known dependencies are listed as libraries. */
  def analyse(code: String): (List[JavaClass], List[JavaClass]) = {
    val parsed = Parsed(code)
    val libraryRanges = KnownDependencies.definitionRanges(code, Python)
    def library(statement: Statement) = libraryRanges.exists((start, end) => start <= statement.start && statement.end <= end)
    val (dependencies, own) = parsed.top.partition(library)
    val module = JavaClass("", -1, code.length, own.flatMap(function(parsed, _, "", inClass = false)).toList, isModule = true)
    (module :: own.flatMap(classOf(parsed, _, library = false)).toList, dependencies.flatMap(classOf(parsed, _, library = true)).toList)
  }

  /** A module function `main()` without parameters opens first, as the program's entry point. */
  def entryMethod(classes: List[JavaClass], balanced: Boolean): Option[JavaMethod] =
    Option.when(balanced)(classes.filter(_.isModule).flatMap(_.methods).find(method => method.name == "main" && method.params.isEmpty)).flatten

  def validName(name: String): Boolean = name.matches(Identifier) && !keywords(name)

  /** An annotation such as `float`, `list[int]` or `dict[str, Walker] | None`; it may be left out. */
  def validType(value: String): Boolean = {
    val text = value.trim
    text.isEmpty || text.matches("[A-Za-z_][A-Za-z0-9_.\\[\\], |]*") && text.count(_ == '[') == text.count(_ == ']') && !keywords(text.takeWhile(_ != '['))
      || text == "None"
  }

  // Writing. Every edit returns new source text; the editor validates it before committing.

  private def lineStart(source: String, position: Int): Int = source.lastIndexWhere(c => c == '\n' || c == '\r', position - 1) + 1

  private def lineEnd(source: String, position: Int): Int = source.indexWhere(c => c == '\n' || c == '\r', position) match {
    case -1 => source.length
    case index => index
  }

  private def afterBreak(source: String, position: Int): Int =
    if source.startsWith("\r\n", position) then position + 2
    else if position < source.length && (source.charAt(position) == '\n' || source.charAt(position) == '\r') then position + 1
    else position

  /** `text` with every line but the first indented, for insertion at an indented position. */
  private def continued(text: String, indent: String, newline: String): String =
    text.split("\n", -1).toList match {
      case first :: rest => (first :: rest.map(line => if line.isEmpty then line else indent + line)).mkString(newline)
      case Nil => ""
    }

  private def spaces(source: String, line: Line): String = source.substring(lineStart(source, line.start), line.start)

  private def isPass(statement: Statement): Boolean = statement.body.isEmpty && statement.line.code == "pass"

  private def isImport(statement: Statement): Boolean =
    statement.line.code.startsWith("import ") || statement.line.code.startsWith("from ")

  /** Inserts `text` as a statement after the one ending at `end` (behind a comment on its line), separated by `blankLines`. */
  private def after(source: String, end: Int, indent: String, text: String, blankLines: Int): String = {
    val newline = lineSeparator(source)
    val at = lineEnd(source, end)
    source.substring(0, at) + newline * (blankLines + 1) + indent + continued(text, indent, newline) + source.substring(at)
  }

  /** Inserts `text` as a statement before `statement`, at its indentation. */
  private def before(source: String, statement: Statement, text: String, blankLines: Int): String = {
    val newline = lineSeparator(source)
    val first = statement.decorators.headOption.getOrElse(statement.line)
    val indent = spaces(source, first)
    source.substring(0, first.start) + continued(text, indent, newline) + newline * (blankLines + 1) + indent + source.substring(first.start)
  }

  /** Replaces a lone `pass` with `text`. */
  private def replacing(source: String, pass: Statement, text: String): String =
    source.substring(0, pass.line.start) + continued(text, spaces(source, pass.line), lineSeparator(source)) + source.substring(pass.line.end)

  private def append(source: String, text: String): String = {
    val newline = lineSeparator(source)
    val trimmed = source.reverse.dropWhile(_.isWhitespace).reverse
    (if trimmed.isEmpty then "" else trimmed + newline + newline) + continued(text, "", newline) + newline
  }

  /** Adds `text` to a block body: after the last of `anchors`, else replacing a lone `pass`, else before `first`. */
  private def intoBody(source: String, header: Statement, anchors: Vector[Statement], first: Option[Statement], text: String,
      blankLines: Int): String = {
    val body = statements(header.body)
    val indent = body.headOption.fold(spaces(source, header.line) + Indent)(member => spaces(source, member.line))
    anchors.lastOption match {
      case Some(anchor) => after(source, anchor.end, indent, text, blankLines)
      case None if body.size == 1 && isPass(body.head) => replacing(source, body.head, text)
      case None => first.orElse(body.headOption) match {
        case Some(statement) => before(source, statement, text, blankLines)
        case None => after(source, header.end, indent, text, 0)
      }
    }
  }

  private def classStatement(parsed: Parsed, className: String): Option[Statement] =
    parsed.top.find(statement => ClassHeader.findPrefixMatchOf(statement.line.code).exists(_.group(1) == className))

  private def methodsOf(parsed: Parsed, statement: Statement): Vector[(Statement, JavaMethod)] =
    statements(statement.body).flatMap(member => function(parsed, member, "", inClass = true).map(member -> _))

  def addClass(source: String, name: String): String = {
    val parsed = Parsed(source)
    val text = s"class $name:\n${Indent}pass"
    parsed.top.filter(statement => ClassHeader.findPrefixMatchOf(statement.line.code).nonEmpty).lastOption match {
      case Some(last) => after(source, last.end, "", text, 1)
      case None => parsed.top.find(statement => !isImport(statement)) match {
        case Some(first) => before(source, first, text, 1)
        case None => append(source, text)
      }
    }
  }

  private def parameterList(params: List[Param]): List[String] =
    params.map(param => if param.javaType.trim.isEmpty then param.name else s"${param.name}: ${param.javaType.trim}")

  private def result(returnType: String): String =
    if returnType.trim.isEmpty || returnType.trim == "None" then "" else s" -> ${returnType.trim}"

  def methodDeclaration(inClass: Boolean, returnType: String, name: String, params: List[Param], isStatic: Boolean): String = {
    val receiver = if inClass && !isStatic then List("self") else Nil
    (if inClass && isStatic then "@staticmethod\n" else "") +
      s"def $name(${(receiver ++ parameterList(params)).mkString(", ")})${result(returnType)}:\n${Indent}pass"
  }

  def addMethod(source: String, clazz: JavaClass, declaration: String): String = {
    val parsed = Parsed(source)
    if clazz.isModule then {
      val functions = parsed.top.filter(statement => DefHeader.findPrefixMatchOf(statement.line.code).nonEmpty)
      functions.lastOption match {
        case Some(last) => after(source, last.end, "", declaration, 1)
        case None => parsed.top.find(statement => !isImport(statement) && !isDefinition(statement)) match {
          case Some(first) => before(source, first, declaration, 1)
          case None => append(source, declaration)
        }
      }
    } else classStatement(parsed, clazz.name).fold(source) { statement =>
      intoBody(source, statement, statements(statement.body).filter(member => !isPass(member)), None, declaration, 1)
    }
  }

  def constructorDeclaration(params: List[Param], assigned: List[String]): String = {
    val body = if assigned.isEmpty then List("pass") else assigned.map(name => s"self.$name = $name")
    s"def __init__(${("self" :: parameterList(params)).mkString(", ")}):\n" + body.map(Indent + _).mkString("\n")
  }

  /** After the class attributes, before the first method. */
  def addConstructor(source: String, clazz: JavaClass, declaration: String): String = {
    val parsed = Parsed(source)
    classStatement(parsed, clazz.name).fold(source) { statement =>
      val body = statements(statement.body)
      val attributes = body.takeWhile(member => !isDefinition(member)).filter(member => member.body.isEmpty && assignment(parsed, member.line).nonEmpty)
      intoBody(source, statement, attributes, body.find(isDefinition), declaration, 1)
    }
  }

  private def defaultValue(pythonType: String): String = pythonType.trim.takeWhile(_ != '[') match {
    case "int" => "0"
    case "float" => "0.0"
    case "str" => "\"\""
    case "bool" => "False"
    case "list" => "[]"
    case "dict" => "{}"
    case "set" => "set()"
    case _ => "None"
  }

  /** A class attribute, or an instance attribute that `__init__` assigns (Python needs a value for it). */
  def fieldDeclaration(isStatic: Boolean, pythonType: String, name: String, initializer: String): String = {
    val annotation = if pythonType.trim.isEmpty then "" else s": ${pythonType.trim}"
    val value = initializer.trim
    if isStatic then s"$name$annotation" + (if value.isEmpty && annotation.isEmpty then " = None" else if value.isEmpty then "" else s" = $value")
    else s"self.$name$annotation = ${if value.isEmpty then defaultValue(pythonType) else value}"
  }

  def saveField(source: String, clazz: JavaClass, edited: Option[JavaField], isStatic: Boolean, declaration: String): String =
    edited match {
      case Some(field) if field.isStatic == isStatic => source.substring(0, field.start) + declaration + source.substring(field.end)
      case Some(field) => insertField(removeMember(source, field.start, field.end), clazz.name, isStatic, declaration)
      case None => insertField(source, clazz.name, isStatic, declaration)
    }

  private def insertField(source: String, className: String, isStatic: Boolean, declaration: String): String = {
    val parsed = Parsed(source)
    classStatement(parsed, className).fold(source) { statement =>
      val body = statements(statement.body)
      def attributes(members: Vector[Statement]) =
        members.filter(member => member.body.isEmpty && member.decorators.isEmpty && assignment(parsed, member.line).nonEmpty)
      if isStatic then intoBody(source, statement, attributes(body.takeWhile(member => !isDefinition(member))), body.find(isDefinition), declaration, 0)
      else methodsOf(parsed, statement).find(_._2.name == "__init__") match {
        case Some((constructor, _)) =>
          val assigned = attributes(statements(constructor.body)).filter(member => member.line.code.startsWith("self."))
          intoBody(source, constructor, assigned, None, declaration, 0)
        case None =>
          val text = s"def __init__(self):\n$Indent$declaration"
          val classLevel = attributes(body.takeWhile(member => !isDefinition(member)))
          intoBody(source, statement, classLevel, body.find(isDefinition), text, 1)
      }
    }
  }

  /** Removes a member with its lines; a block it leaves empty keeps a `pass`. The blank lines that followed the
    * member go with it when blank lines or a block header precede it, so the gap before it stays the only one.
    */
  def removeMember(source: String, start: Int, end: Int): String = {
    val parsed = Parsed(source)
    def enclosing(members: Vector[Statement]): Option[Statement] =
      members.find(member => member.body.nonEmpty && member.line.end < start && end <= member.end)
        .map(member => enclosing(statements(member.body)).getOrElse(member))
    // Comments alone do not keep a block valid.
    val emptied = enclosing(parsed.top).exists(block => block.body.filterNot(_.comment).forall(line => line.start >= start && line.end <= end))
    if emptied then source.substring(0, start) + "pass" + source.substring(end)
    else {
      val from = lineStart(source, start)
      var removed = source.substring(0, from) + source.substring(afterBreak(source, lineEnd(source, end)))
      def blankAt(position: Int) = position < removed.length && removed.substring(position, lineEnd(removed, position)).trim.isEmpty
      val previous = if from == 0 then "" else removed.substring(lineStart(removed, from - 1), from).trim
      if previous.isEmpty || previous.endsWith(":") then
        while blankAt(from) && afterBreak(removed, lineEnd(removed, from)) > from do
          removed = removed.substring(0, from) + removed.substring(afterBreak(removed, lineEnd(removed, from)))
      removed
    }
  }

  /** The declaration without its body, as shown in the dialog preview. */
  def head(declaration: String): String =
    declaration.split("\n").takeWhile(line => !line.startsWith(Indent)).mkString("\n")
}
