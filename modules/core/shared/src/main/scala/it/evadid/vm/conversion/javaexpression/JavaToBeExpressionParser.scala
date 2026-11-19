package it.evadid.vm.conversion.javaexpression

import it.evadid.vm.conversion.pythonexpression.PythonToExpression

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression

/** Parser for the Java-like source emitted by the BeExpression printer.
  *
  * The VM already has a mature Python parser. This parser supplies the Java lexical and structural
  * front end, lowers Java statements to equivalent Python, and delegates AST construction to that
  * parser. It handles classes, variables, assignment, calls, returns, functions, if/else, while, and the
  * canonical Java `for` loops emitted for [[it.evadid.vm.code.controlStructures.BeFor]].
  */
final class JavaToBeExpressionParser {
  def parseProgram(source: String): BeProgram = PythonToExpression.program(toPython(source))

  def parseExpression(source: String): BeExpression = parseProgram(source).fullProgram

  private[conversion] def toPython(source: String): String = {
    val output = List.newBuilder[String]
    var indentation = 0
    var emptyBlock = false
    case class Scope(owner: Option[String], locals: Set[String], isClass: Boolean, isStatic: Boolean)
    val classes = it.evadid.vm.conversion.structured.SourceStructure.javaClasses(source)
    val fields = classes.map(block => block.name -> it.evadid.vm.conversion.structured.SourceStructure.javaMembers(block.source).flatMap { member =>
      stripModifiers(member.trim.stripSuffix(";")) match {
        case Declaration(_, name, _) => Some(name)
        case _ => None
      }
    }.toSet).toMap
    val methods = classes.map(block => block.name -> it.evadid.vm.conversion.structured.SourceStructure.javaMembers(block.source).flatMap { member =>
      member.takeWhile(_ != '{').trim match {
        case Function(_, name, _) => Some(name -> "\\bstatic\\b".r.findFirstIn(member.takeWhile(_ != '{')).nonEmpty)
        case _ => None
      }
    }.toMap).toMap
    var scopes = List.empty[Scope]
    def owner: Option[String] = scopes.headOption.flatMap(_.owner)
    def visibleLocals: Set[String] = scopes.takeWhile(!_.isClass).flatMap(_.locals).toSet
    def expression(code: String): String = translateExpression(code, word => {
      if word == "this" then {
        require(!scopes.headOption.exists(_.isStatic), "this cannot occur in a static method.")
        "self"
      } else if visibleLocals(word) then word
      else if owner.exists(name => fields.getOrElse(name, Set.empty)(word)) && !scopes.headOption.exists(_.isClass) then {
        require(!scopes.headOption.exists(_.isStatic), s"Instance field '$word' needs an object.")
        "self." + word
      } else owner.flatMap(methods.get).flatMap(_.get(word)) match {
        case Some(true) => owner.get + "." + word
        case Some(false) if !scopes.headOption.exists(_.isClass) =>
          require(!scopes.headOption.exists(_.isStatic), s"Instance method '$word' needs an object.")
          "self." + word
        case _ => word
      }
    }, classes.map(_.name).toSet)
    tokenize(source).foreach {
      case CloseBlock =>
        if emptyBlock then output += (" " * (indentation * 4) + "pass")
        scopes = scopes.drop(1)
        indentation = (indentation - 1).max(0)
        emptyBlock = false
      case OpenBlock(header) =>
        translateHeader(header, owner, expression).linesIterator.foreach(line => output += (" " * (indentation * 4) + line))
        val next = header match {
          case ClassHeader(name) => Scope(Some(name), Set.empty, true, false)
          case Function(_, _, parameters) => Scope(owner, parameterNames(parameters), false, "\\bstatic\\b".r.findFirstIn(header).nonEmpty)
          case ForLoop(name, _, _, _) => Scope(owner, Set(name), false, scopes.headOption.exists(_.isStatic))
          case Constructor(_, parameters) => Scope(owner, parameterNames(parameters), false, false)
          case _ => Scope(owner, Set.empty, false, scopes.headOption.exists(_.isStatic))
        }
        scopes = next :: scopes
        indentation += 1
        emptyBlock = true
      case Statement(code) if code.trim.nonEmpty =>
        val statement = stripModifiers(code.trim)
        statement match {
          case Declaration(_, name, _) if scopes.nonEmpty =>
            require(!scopes.head.isClass || !"\\bstatic\\b".r.findFirstIn(code).nonEmpty, "Static object fields are not supported by the shared expression model.")
            scopes = scopes.head.copy(locals = scopes.head.locals + name) :: scopes.tail
          case _ => ()
        }
        val rendered = statement match {
          case Declaration(javaType, name, initializer) =>
            s"$name: ${pythonType(javaType)}${Option(initializer).map(expression).getOrElse("")}"
          case other => expression(other)
        }
        output += (" " * (indentation * 4) + rendered)
        emptyBlock = false
      case _ => ()
    }
    output.result().mkString("\n")
  }

  private sealed trait Token derives upickle.default.ReadWriter
  private final case class OpenBlock(header: String) extends Token derives upickle.default.ReadWriter
  private case object CloseBlock extends Token
  private final case class Statement(code: String) extends Token derives upickle.default.ReadWriter

  /** Split only on structural punctuation outside strings and parentheses. */
  private def tokenize(source: String): List[Token] = {
    val tokens = List.newBuilder[Token]
    val current = new StringBuilder
    var quote: Option[Char] = None
    var escaped = false
    var parentheses = 0
    var lineComment = false

    def emitStatement(): Unit = {
      val value = current.result().trim
      current.clear()
      if value.nonEmpty then tokens += Statement(value)
    }

    // The Java printer places stable entity-name hints directly after parameters. They look like
    // line comments even when another parameter follows, so remove only those machine hints before
    // applying normal Java line-comment rules.
    val withoutEntityHints = JavaToBeExpressionParser.withoutEntityHints(source)
    withoutEntityHints.foreach { char =>
      if lineComment then
        if char == '\n' then lineComment = false
      else quote match
        case Some(delimiter) =>
          current += char
          if escaped then escaped = false
          else if char == '\\' then escaped = true
          else if char == delimiter then quote = None
        case None =>
          char match
            case '\'' | '"' => quote = Some(char); current += char
            case '/' if current.nonEmpty && current.last == '/' =>
              current.deleteCharAt(current.length - 1); lineComment = true
            case '(' => parentheses += 1; current += char
            case ')' => parentheses -= 1; current += char
            case '{' if parentheses == 0 =>
              val header = current.result().trim
              current.clear()
              if header.nonEmpty then tokens += OpenBlock(header)
            case '}' if parentheses == 0 => emitStatement(); tokens += CloseBlock
            case ';' if parentheses == 0 => emitStatement()
            case value => current += value
    }
    emitStatement()
    tokens.result()
  }

  private def translateHeader(raw: String, owner: Option[String], expression: String => String): String = {
    val header = raw.trim
    if header.matches("if\\s*\\(.*\\)") then s"if ${expression(header.substring(header.indexOf('(') + 1, header.lastIndexOf(')')))}:"
    else if header.matches("else\\s+if\\s*\\(.*\\)") then s"elif ${expression(header.substring(header.indexOf('(') + 1, header.lastIndexOf(')')))}:"
    else if header == "else" then "else:"
    else if header.matches("while\\s*\\(.*\\)") then s"while ${expression(header.substring(header.indexOf('(') + 1, header.lastIndexOf(')')))}:"
    else header match
      case ClassHeader(name) => s"class $name:"
      case _ => parseFor(header, expression).getOrElse(parseFunction(header, owner))
  }

  private val ForLoop =
    """for\s*\(\s*(?:[A-Za-z_$][\w$<>\[\]]*\s+)?([A-Za-z_$][\w$]*)\s*=\s*(.+?)\s*;\s*\1\s*(<=|<)\s*(.+?)\s*;\s*\1\s*\+\+\s*\)""".r

  private def parseFor(header: String, expression: String => String): Option[String] = header match
    case ForLoop(name, start, operator, limit) =>
      val stop = if operator == "<=" then s"${expression(limit)} + 1" else expression(limit)
      Some(s"for $name in range(${expression(start)}, $stop):")
    case _ => None

  private val ClassHeader =
    """(?:public\s+|private\s+|protected\s+|static\s+|final\s+)*class\s+([A-Za-z_$][\w$]*)""".r

  private val Function =
    """(?:public\s+|private\s+|protected\s+|static\s+)*([A-Za-z_$][\w$<>\[\]]*)\s+([A-Za-z_$][\w$]*)\s*\((.*)\)""".r

  private val Constructor = """(?:public\s+|private\s+|protected\s+)*([A-Za-z_$][\w$]*)\s*\((.*)\)""".r
  private def parameterNames(source: String): Set[String] = splitArguments(source).map(_.trim.split("\\s+").last).toSet
  private def typedParameters(source: String): List[String] = splitArguments(source).map { parameter =>
    val parts = parameter.trim.stripPrefix("final ").split("\\s+")
    s"${parts.last}: ${pythonType(parts.head)}"
  }

  private def pythonType(javaType: String): String = javaType match
    case "byte" | "short" | "int" | "long" => "float" // The VM currently models these as Numeric.
    case "float" | "double" => "float"
    case "boolean" => "bool"
    case "char" | "String" => "str"
    case "void" => "None"
    case "Date" => "date"
    case "Object" => "Any"
    case name if name.matches("[A-Za-z_$][\\w$]*") => name
    case other => throw IllegalArgumentException(s"Unsupported Java type: $other")

  private def parseFunction(header: String, owner: Option[String]): String = header match
    case Function(returnType, name, parameters) =>
      val typedParameters = splitArguments(parameters).filter(_.nonEmpty).map { parameter =>
        val parts = parameter.trim.stripPrefix("final ").split("\\s+")
        val parameterName = parts.last.replace("...", "")
        s"$parameterName: ${pythonType(parts.head)}"
      }
      val isStatic = "\\bstatic\\b".r.findFirstIn(header).nonEmpty
      val parametersWithReceiver = Option.when(owner.nonEmpty && !isStatic)("self").toList ++ typedParameters
      val decorator = if owner.nonEmpty && isStatic then "@staticmethod\n" else ""
      decorator + s"def $name(${parametersWithReceiver.mkString(", ")}) -> ${pythonType(returnType)}:"
    case Constructor(name, parameters) if owner.contains(name) =>
      s"def __init__(${("self" :: typedParameters(parameters)).mkString(", ")}):"
    case _ => throw IllegalArgumentException(s"Unsupported Java block header: $header")

  private val Declaration =
    """^(?:final\s+)?(boolean|byte|short|int|long|float|double|char|String|Object|[A-Z][A-Za-z0-9_$<>\[\]]*)\s+([A-Za-z_$][\w$]*)(\s*=\s*.+)?$""".r

  private def stripModifiers(raw: String): String = raw.replaceFirst("^(?:(?:public|private|protected|static|final)\\s+)+", "")

  private def translateExpression(expression: String, resolve: String => String = identity, knownClasses: Set[String] = Set.empty): String = {
    val result = new StringBuilder
    var index = 0
    var quote: Option[Char] = None
    var escaped = false
    while index < expression.length do
      val char = expression.charAt(index)
      quote match
        case Some(delimiter) =>
          result += char
          if escaped then escaped = false
          else if char == '\\' then escaped = true
          else if char == delimiter then quote = None
          index += 1
        case None if char == '(' && expression.indexOf(')', index) > index &&
            expression.substring(index + 1, expression.indexOf(')', index)).trim == "double" =>
          index = expression.indexOf(')', index) + 1
        case None if char == '\'' || char == '"' => quote = Some(char); result += char; index += 1
        case None if expression.startsWith("&&", index) => result ++= " and "; index += 2
        case None if expression.startsWith("||", index) => result ++= " or "; index += 2
        case None if char == '!' && !expression.startsWith("!=", index) => result ++= "not "; index += 1
        case None if char.isLetter || char == '_' || char == '$' =>
          val end = expression.indexWhere(value => !value.isLetterOrDigit && value != '_' && value != '$', index) match {
            case -1 => expression.length
            case value => value
          }
          val word = expression.substring(index, end)
          val qualified = expression.take(index).reverse.dropWhile(_.isWhitespace).headOption.contains('.')
          val name = word match {
            case "new" =>
              val constructed = expression.drop(end).dropWhile(_.isWhitespace).takeWhile(value => value.isLetterOrDigit || value == '_' || value == '$')
              require(knownClasses(constructed), s"Cannot convert construction of unknown class '$constructed'.")
              ""
            case "true" => "True"
            case "false" => "False"
            case "null" => "None"
            case _ => if qualified then word else resolve(word)
          }
          result ++= name
          index = end
        case None => result += char; index += 1
    result.result()
  }

  private def splitArguments(value: String): List[String] =
    if value.trim.isEmpty then Nil else value.split(',').toList.map(_.trim)
}

object JavaToBeExpressionParser {
  private[conversion] def withoutEntityHints(source: String): String = {
    val output = new StringBuilder
    var offset = 0
    while offset < source.length do {
      val start = offset
      var hint = false
      if source.charAt(offset) == '"' || source.charAt(offset) == '\'' then {
        val delimiter = source.charAt(offset)
        offset += 1
        var closed = false
        while offset < source.length && !closed do {
          if source.charAt(offset) == '\\' then offset = (offset + 2).min(source.length)
          else if source.charAt(offset) == delimiter then { offset += 1; closed = true }
          else offset += 1
        }
      } else if source.startsWith("/*", offset) then {
        val end = source.indexOf("*/", offset + 2)
        offset = if end < 0 then source.length else end + 2
      } else if source.startsWith("//", offset) then {
        offset += 2
        if source.startsWith("EvaEntityName(", offset) then {
          while offset < source.length && !")\r\n".contains(source.charAt(offset)) do offset += 1
          if offset < source.length && source.charAt(offset) == ')' then { offset += 1; hint = true }
        }
        if !hint then
          while offset < source.length && !"\r\n".contains(source.charAt(offset)) do offset += 1
      } else offset += 1
      if !hint then output.append(source.substring(start, offset))
    }
    output.result()
  }

  def parse(source: String): BeExpression = new JavaToBeExpressionParser().parseExpression(source)
}
