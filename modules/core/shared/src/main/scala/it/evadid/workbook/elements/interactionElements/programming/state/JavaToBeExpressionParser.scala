package it.evadid.workbook.elements.interactionElements.programming.state

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
  def parseProgram(source: String): BeProgram = BeProgram.fromPythonString(toPython(source))

  def parseExpression(source: String): BeExpression = parseProgram(source).fullProgram

  private[programming] def toPython(source: String): String = {
    val output = List.newBuilder[String]
    var indentation = 0
    tokenize(source).foreach {
      case CloseBlock => indentation = (indentation - 1).max(0)
      case OpenBlock(header) =>
        output += (" " * (indentation * 4) + translateHeader(header))
        indentation += 1
      case Statement(code) if code.trim.nonEmpty =>
        output += (" " * (indentation * 4) + translateStatement(code))
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
    val withoutEntityHints = source.replaceAll("//EvaEntityName\\([^)]*\\)", "")
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

  private def translateHeader(raw: String): String = {
    val header = raw.trim
    if header.matches("if\\s*\\(.*\\)") then s"if ${conditionBetweenParens(header)}:"
    else if header.matches("else\\s+if\\s*\\(.*\\)") then s"elif ${conditionBetweenParens(header)}:"
    else if header == "else" then "else:"
    else if header.matches("while\\s*\\(.*\\)") then s"while ${conditionBetweenParens(header)}:"
    else header match
      case ClassHeader(name) => s"class $name:"
      case _ => parseFor(header).getOrElse(parseFunction(header))
  }

  private def conditionBetweenParens(header: String): String =
    translateExpression(header.substring(header.indexOf('(') + 1, header.lastIndexOf(')')))

  private val ForLoop =
    """for\s*\(\s*(?:[A-Za-z_$][\w$<>\[\]]*\s+)?([A-Za-z_$][\w$]*)\s*=\s*(.+?)\s*;\s*\1\s*(<=|<)\s*(.+?)\s*;\s*\1\s*\+\+\s*\)""".r

  private def parseFor(header: String): Option[String] = header match
    case ForLoop(name, start, operator, limit) =>
      val stop = if operator == "<=" then s"${translateExpression(limit)} + 1" else translateExpression(limit)
      Some(s"for $name in range(${translateExpression(start)}, $stop):")
    case _ => None

  private val ClassHeader =
    """(?:public\s+|private\s+|protected\s+|static\s+|final\s+)*class\s+([A-Za-z_$][\w$]*)""".r

  private val Function =
    """(?:public\s+|private\s+|protected\s+|static\s+)*([A-Za-z_$][\w$<>\[\]]*)\s+([A-Za-z_$][\w$]*)\s*\((.*)\)""".r

  private def pythonType(javaType: String): String = javaType match
    case "byte" | "short" | "int" | "long" => "float" // The VM currently models these as Numeric.
    case "float" | "double" => "float"
    case "boolean" => "bool"
    case "char" | "String" => "str"
    case "void" => "None"
    case "Date" => "date"
    case _ => "Any"

  private def parseFunction(header: String): String = header match
    case Function(returnType, name, parameters) =>
      val typedParameters = splitArguments(parameters).filter(_.nonEmpty).map { parameter =>
        val parts = parameter.trim.stripPrefix("final ").split("\\s+")
        val parameterName = parts.last.replace("...", "")
        s"$parameterName: ${pythonType(parts.head)}"
      }
      s"def $name(${typedParameters.mkString(", ")}) -> ${pythonType(returnType)}:"
    case _ => throw IllegalArgumentException(s"Unsupported Java block header: $header")

  private val Declaration =
    """^(?:final\s+)?(boolean|byte|short|int|long|float|double|char|String|Object|[A-Z][A-Za-z0-9_$<>\[\]]*)\s+([A-Za-z_$][\w$]*)(\s*=\s*.+)?$""".r

  private def translateStatement(raw: String): String = raw.trim match
    case Declaration(javaType, name, initializer) =>
      val assignment = Option(initializer).map(translateExpression).getOrElse("")
      s"$name: ${pythonType(javaType)}$assignment"
    case statement if statement.matches("[A-Za-z_$][\\w$]*") =>
      throw IllegalArgumentException(s"Unsupported Java statement: $statement")
    case statement => translateExpression(statement)

  private def translateExpression(expression: String): String = {
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
        case None if char == '\'' || char == '"' => quote = Some(char); result += char; index += 1
        case None if expression.startsWith("&&", index) => result ++= " and "; index += 2
        case None if expression.startsWith("||", index) => result ++= " or "; index += 2
        case None if char == '!' && !expression.startsWith("!=", index) => result ++= "not "; index += 1
        case None => result += char; index += 1
    normalizeKeywords(result.result())
  }

  private def normalizeKeywords(value: String): String =
    value.replaceAll("\\btrue\\b", "True").replaceAll("\\bfalse\\b", "False").replaceAll("\\bnull\\b", "None")

  private def splitArguments(value: String): List[String] =
    if value.trim.isEmpty then Nil else value.split(',').toList.map(_.trim)
}

object JavaToBeExpressionParser {
  def parse(source: String): BeExpression = new JavaToBeExpressionParser().parseExpression(source)
}
