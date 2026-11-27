package it.evadid.vm.parsing.cpp

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.conversion.parsed.DefaultDefinitions
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference}

import scala.collection.mutable.ListBuffer

/**
 *
 * c++ parser is currently minimal and only understands a subset of statements
 * Anything it doesn't understand is ignored (so it won't crash the editor on tab switch).
 *
 */
object CppParser {

  private final class ParseContext {
    private var scopes: List[scala.collection.mutable.LinkedHashMap[String, BeDefineVariable]] =
      List(scala.collection.mutable.LinkedHashMap.empty[String, BeDefineVariable])

    def pushScope(): Unit = {
      scopes = scala.collection.mutable.LinkedHashMap.empty[String, BeDefineVariable] :: scopes
    }

    def popScope(): Unit = {
      if (scopes.nonEmpty) scopes = scopes.tail
    }

    def lookupVariable(name: String): Option[BeDefineVariable] =
      scopes.collectFirst { case scope if scope.contains(name) => scope(name) }

    def defineVariable(name: String, dataType: BeDataType): BeDefineVariable = {
      val variable = BeDefineVariable(BeEntityName.fromUniversalNameInParts(name), dataType)
      scopes.head.update(name, variable)
      variable
    }

    def assignVariable(name: String, dataType: BeDataType): BeDefineVariable =
      lookupVariable(name).getOrElse(defineVariable(name, dataType))
  }

  final case class ParseResult(sequence: BeSequence, unsupportedStatements: List[String])

  def parseCppWithDiagnostics(source: String): ParseResult = {
    val stripped = stripWrappers(removeComments(source))
    val (expressions, unsupported) = parseStatements(stripped)
    ParseResult(BeSequence.optionalBody(expressions), unsupported)
  }

  def parseCpp(source: String): BeSequence = {
    parseCppWithDiagnostics(source).sequence
  }

  /** Removes line and block comments in one pass, keeping comment markers inside string and character literals. */
  private def removeComments(source: String): String = {
    @scala.annotation.tailrec
    def loop(i: Int, inString: Option[Char], out: StringBuilder): String =
      if (i >= source.length) out.toString
      else {
        val ch = source.charAt(i)
        val next = if (i + 1 < source.length) source.charAt(i + 1) else '\u0000'
        inString match {
          case Some(quote) =>
            if (ch == '\\' && i + 1 < source.length) loop(i + 2, inString, out.append(ch).append(next))
            else loop(i + 1, if (ch == quote) None else inString, out.append(ch))
          case None if ch == '"' || ch == '\'' => loop(i + 1, Some(ch), out.append(ch))
          case None if ch == '/' && next == '/' =>
            val lineEnd = source.indexOf('\n', i)
            loop(if (lineEnd < 0) source.length else lineEnd, None, out)
          case None if ch == '/' && next == '*' =>
            val blockEnd = source.indexOf("*/", i + 2)
            loop(if (blockEnd < 0) source.length else blockEnd + 2, None, out.append(' '))
          case None => loop(i + 1, None, out.append(ch))
        }
      }
    loop(0, None, new StringBuilder)
  }

  private def stripWrappers(source: String): String = {
    val trimmed = source.trim
    val wrappers = List("void setup()", "void loop()", "int main()", "void main()")

    wrappers
      .find(w => trimmed.startsWith(w))
      .map { _ =>
        val openBrace = trimmed.indexOf('{')
        val closeBrace = trimmed.lastIndexOf('}')
        if (openBrace >= 0 && closeBrace > openBrace) trimmed.substring(openBrace + 1, closeBrace) else trimmed
      }
      .getOrElse(trimmed)
  }

  // Allow Arduino-style calls like Serial.println(...) and namespaced calls like std::foo(...)
  private val CallPattern = """^([A-Za-z_][A-Za-z0-9_:\.]*)\s*\((.*)\)\s*$""".r

  private val DeclarationPattern = """^(?:const\s+)?([A-Za-z_][A-Za-z0-9_:<>]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:=\s*(.+))?$""".r
  private val AssignmentPattern = """^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)$""".r

  private def isQuote(ch: Char): Boolean = ch == '"' || ch == '\''

  private def parseStatements(source: String): (List[BeExpression], List[String]) = {
    val context = new ParseContext
    val parser = new Parser(source, context)
    parser.parseStatementsUntil(stopChar = None)
  }

  private final class Parser(source: String, context: ParseContext) {
    private val s = source
    private var i = 0

    private def eof: Boolean = i >= s.length

    private def peek: Char = if (eof) '\u0000' else s.charAt(i)

    private def skipWhitespace(): Unit = {
      while (!eof && s.charAt(i).isWhitespace) i += 1
    }

    private def isIdentChar(ch: Char): Boolean = ch.isLetterOrDigit || ch == '_'

    private def startsWithKeyword(keyword: String): Boolean = {
      if (!s.regionMatches(i, keyword, 0, keyword.length)) return false
      val beforeOk = i == 0 || !isIdentChar(s.charAt(i - 1))
      val afterIdx = i + keyword.length
      val afterOk = afterIdx >= s.length || !isIdentChar(s.charAt(afterIdx))
      beforeOk && afterOk
    }

    /** Reads the content between `open` and the matching `close`, consuming both delimiters. */
    private def readBalanced(open: Char, close: Char): Either[String, String] = {
      if (peek != open) return Left(s"Expected '$open'")
      i += 1

      val out = new StringBuilder
      var depth = 1
      var inString: Option[Char] = None

      while (!eof && depth > 0) {
        val ch = s.charAt(i)
        i += 1
        inString match {
          case Some(q) =>
            if (ch == q) inString = None
          case None if isQuote(ch) =>
            inString = Some(ch)
          case None if ch == open =>
            depth += 1
          case None if ch == close =>
            depth -= 1
          case None => ()
        }
        if (depth > 0) out.append(ch)
      }

      if (depth == 0) Right(out.toString)
      else Left(s"Unclosed '$open'")
    }

    private def readUntilStatementEnd(): String = {
      val out = new StringBuilder
      var parenDepth = 0
      var braceDepth = 0
      var inString: Option[Char] = None

      while (!eof) {
        val ch = s.charAt(i)
        inString match {
          case Some(q) =>
            if (ch == q) inString = None
          case None =>
            ch match {
              case '"' | '\'' => inString = Some(ch)
              case '(' => parenDepth += 1
              case ')' => parenDepth = math.max(0, parenDepth - 1)
              case '{' => braceDepth += 1
              // A closing brace of a nested section inside this statement is kept.
              case '}' if braceDepth > 0 => braceDepth -= 1
              // End of the surrounding block; the brace is left for the caller.
              case '}' => return out.toString
              case ';' if parenDepth == 0 && braceDepth == 0 =>
                i += 1 // consume ';'
                return out.toString
              case _ => ()
            }
        }
        out.append(ch)
        i += 1
      }

      out.toString
    }

    def parseStatementsUntil(stopChar: Option[Char]): (List[BeExpression], List[String]) = {
      val expressions = ListBuffer.empty[BeExpression]
      val unsupported = ListBuffer.empty[String]

      while (!eof) {
        skipWhitespace()

        if (stopChar.nonEmpty && !eof && peek == stopChar.get) {
          i += 1 // consume stopChar
          return (expressions.toList, unsupported.toList)
        }

        if (eof) return (expressions.toList, unsupported.toList)

        if (startsWithKeyword("while")) {
          expressions += parseWhile(unsupported)
        } else if (startsWithKeyword("if")) {
          expressions += parseIfElse(unsupported)
        } else if (startsWithKeyword("for")) {
          expressions += parseForLoop(unsupported)
        } else {
          val statementStart = i
          val stmtText = readUntilStatementEnd().trim
          if (i == statementStart) {
            // A closing brace without a matching block: report it and continue after it.
            unsupported += peek.toString
            i += 1
          } else if (stmtText.nonEmpty) {
            parseSimpleStatement(stmtText) match {
              case Right(expr) => expressions += expr
              case Left(bad) =>
                if (bad.trim.nonEmpty) unsupported += bad
                expressions += BeExpressionUnsupported(bad, parsedFromLanguage = Some(Cpp))
            }
          }
        }
      }

      (expressions.toList, unsupported.toList)
    }

    /** Consumes `keyword` and returns the index where the statement started. */
    private def consumeKeyword(keyword: String): Int = {
      val startIndex = i
      i += keyword.length
      startIndex
    }

    /** Consumes the rest of a malformed statement and reports the statement from `startIndex` as unparsable. */
    private def unparsableFrom(startIndex: Int, message: String, unsupported: ListBuffer[String]): BeExpression = {
      val remainder = readUntilStatementEnd().trim
      val original = s.substring(startIndex, math.min(s.length, i)).trim + remainder
      unsupported += original
      BeExpressionUnparsable(original, message, parsedFromLanguage = Some(Cpp))
    }

    /** Reads a balanced section after optional whitespace, or reports the enclosing statement as unparsable. */
    private def readSection(
                             open: Char,
                             close: Char,
                             startIndex: Int,
                             description: String,
                             unsupported: ListBuffer[String]
                           ): Either[BeExpression, String] = {
      skipWhitespace()
      readBalanced(open, close).left.map(err => unparsableFrom(startIndex, s"$description parse error: $err", unsupported))
    }

    /** Parses a block body in a new variable scope; its unsupported statements stay in the body and are also reported. */
    private def parseScopedBody(bodyText: String, unsupported: ListBuffer[String]): List[BeExpression] = {
      context.pushScope()
      val (bodyExpressions, bodyUnsupported) = new Parser(bodyText, context).parseStatementsUntil(stopChar = None)
      context.popScope()
      bodyUnsupported.foreach(stmt => if (stmt.trim.nonEmpty) unsupported += stmt)
      bodyExpressions
    }

    private def parseWhile(unsupported: ListBuffer[String]): BeExpression = {
      val startIndex = consumeKeyword("while")
      val parsed: Either[BeExpression, BeExpression] = for {
        conditionText <- readSection('(', ')', startIndex, "C++ while: condition", unsupported)
        bodyText <- readSection('{', '}', startIndex, "C++ while: body", unsupported)
      } yield {
        val conditionSeq = parseCondition(conditionText, context)
        BeWhile(conditionSeq, BeSequence.optionalBody(parseScopedBody(bodyText, unsupported)))
      }
      parsed.merge
    }

    private def parseIfElse(unsupported: ListBuffer[String]): BeExpression = {
      val startIndex = consumeKeyword("if")
      val parsed: Either[BeExpression, BeExpression] =
        readSection('(', ')', startIndex, "C++ if: condition", unsupported).flatMap { conditionText =>
          readSection('{', '}', startIndex, "C++ if: body", unsupported).flatMap { thenBodyText =>
            val conditionSeq = parseCondition(conditionText, context)
            val thenExpressions = parseScopedBody(thenBodyText, unsupported)
            parseElseBranch(startIndex, unsupported).map { elseExpressions =>
              BeIfElse(conditionSeq, BeSequence.optionalBody(thenExpressions), BeSequence.optionalBody(elseExpressions))
            }
          }
        }
      parsed.merge
    }

    /** Parses an optional `else` branch; an else-if chain becomes the single expression of the else body. */
    private def parseElseBranch(startIndex: Int, unsupported: ListBuffer[String]): Either[BeExpression, List[BeExpression]] = {
      skipWhitespace()
      if (!startsWithKeyword("else")) Right(Nil)
      else {
        i += "else".length
        skipWhitespace()
        if (startsWithKeyword("if")) Right(List(parseIfElse(unsupported)))
        else readSection('{', '}', startIndex, "C++ else: body", unsupported).map(parseScopedBody(_, unsupported))
      }
    }

    private def parseForLoop(unsupported: ListBuffer[String]): BeExpression = {
      val startIndex = consumeKeyword("for")
      val parsed: Either[BeExpression, BeExpression] = for {
        headerText <- readSection('(', ')', startIndex, "C++ for: header", unsupported)
        bodyText <- readSection('{', '}', startIndex, "C++ for: body", unsupported)
      } yield {
        val countedAmount = splitTopLevel(headerText, ';').map(_.trim) match {
          case List(initPart, condPart, incPart) => parseCountedFor(initPart, condPart, incPart)
          case _ => None
        }
        countedAmount match {
          case Some(amount) =>
            BeRepeatNr.times(amount, BeSequence.optionalBody(parseScopedBody(bodyText, unsupported)))
          case None =>
            val original = s"for(${headerText.trim}){...}"
            unsupported += original
            BeExpressionUnsupported(original, parsedFromLanguage = Some(Cpp))
        }
      }
      parsed.merge
    }

    /** Recognizes `start; var < bound; var++`-style headers and returns the repetition count. */
    private def parseCountedFor(initPart: String, condPart: String, incPart: String): Option[Int] =
      for {
        (loopVar, start) <- parseLoopStart(initPart.trim)
        if isUnitIncrement(incPart, loopVar)
        (op, bound) <- findTopLevelBinaryOperator(condPart.trim, List("<=", "<")).flatMap { case (left, op, right) =>
          if (left.trim.replaceAll("\\s+", "") != loopVar) None
          else scala.util.Try(BigInt(right.trim)).toOption.map(bound => op -> bound)
        }
        count <- op match {
          case "<" => Some(bound - start)
          case "<=" => Some((bound - start) + 1)
          case _ => None
        }
        if count >= 0 && count <= Int.MaxValue
      } yield count.toInt

    private def parseLoopStart(init: String): Option[(String, BigInt)] =
      init match {
        case DeclarationPattern(_, varName, initRaw) if initRaw != null =>
          scala.util.Try(BigInt(initRaw.trim)).toOption.map(v => varName -> v)
        case AssignmentPattern(varName, rhs) =>
          scala.util.Try(BigInt(rhs.trim)).toOption.map(v => varName -> v)
        case _ => None
      }

    private def isUnitIncrement(incPart: String, loopVar: String): Boolean = {
      val inc = incPart.trim.replaceAll("\\s+", "")
      inc == s"${loopVar}++" || inc == s"++${loopVar}" || inc == s"${loopVar}+=1" ||
        inc == s"${loopVar}=${loopVar}+1" || inc == s"${loopVar}=1+${loopVar}"
    }

    private def parseSimpleStatement(statement: String): Either[String, BeExpression] = {
      val stmt = statement.trim.stripSuffix(";").trim
      if (stmt.isEmpty) return Left("")

      stmt match {
        case DeclarationPattern(typeNameRaw, varName, initRaw) =>
          val dataType = mapCppType(typeNameRaw)
          val defVar = context.defineVariable(varName, dataType)
          Option(initRaw).map(_.trim).filter(_.nonEmpty) match {
            case Some(initExprText) =>
              Right(BeAssignVariable(defVar, parseValueExpression(initExprText, context)))
            case None =>
              Right(defVar)
          }

        case AssignmentPattern(varName, rhs) =>
          val defVar = context.assignVariable(varName, BeDataType.AnyType)
          Right(BeAssignVariable(defVar, parseValueExpression(rhs, context)))

        case CallPattern(name, argsText) => Right(parseFunctionCall(name, argsText))
        case _ => Left(stmt)
      }
    }
  }

  private def parseCondition(conditionText: String, context: ParseContext): BeSequence = {
    val trimmed = conditionText.trim
    if (trimmed.isEmpty) return BeSequence.conditionalBody(Nil)

    parseBinaryComparison(trimmed, context)
      .orElse {
        trimmed match {
          case CallPattern(name, argsText) =>
            Some(BeSequence.conditionalBody(List(parseFunctionCall(name, argsText))))
          case _ => None
        }
      }
      .getOrElse(BeSequence.conditionalBody(List(parseOperand(trimmed, BeDataType.Boolean, context))))
  }

  private val ComparisonOperators: List[String] = List("==", "!=", ">=", "<=", ">", "<")

  private def parseBinaryComparison(text: String, context: ParseContext): Option[BeSequence] = {
    findTopLevelBinaryOperator(text, ComparisonOperators).flatMap { case (left, op, right) =>
      val funcOpt = DefaultDefinitions.operatorDefinitionsBySymbolAndArity.get(op -> 2)
      funcOpt.map { funcDef =>
        val leftVar = funcDef.inputs.headOption
        val rightVar = funcDef.inputs.drop(1).headOption
        def operandType(input: Option[BeDefineVariable]): BeDataType = input.map(_.variableType).getOrElse(BeDataType.AnyType)

        val leftExpr = parseOperand(left, operandType(leftVar), context)
        val rightExpr = parseOperand(right, operandType(rightVar), context)

        val valueMap: Map[BeDefineVariable, BeExpression] = (leftVar.toList.map(_ -> leftExpr) ++ rightVar.toList.map(_ -> rightExpr)).toMap
        BeSequence.conditionalBody(List(BeFunctionCall.fromDefinition(funcDef, valueMap)))
      }
    }
  }

  private def parseValueExpression(text: String, context: ParseContext): BeExpression = {
    val trimmed = text.trim
    trimmed match {
      case CallPattern(name, argsText) => parseFunctionCall(name, argsText)
      case _ => parseOperand(trimmed, BeDataType.AnyType, context)
    }
  }

  private def parseOperand(text: String, expectedType: BeDataType, context: ParseContext): BeExpression = {
    val trimmed = text.trim
    trimmed match {
      case CallPattern(name, argsText) =>
        parseFunctionCall(name, argsText)
      // Boolean literals also match the identifier pattern, so they are recognized first.
      case _ if BeDataType.Boolean.isValidLiteral(trimmed) =>
        BeUseValue(BeDataValueLiteral(trimmed), None)
      case ident if ident.matches("^[A-Za-z_][A-Za-z0-9_]*$") =>
        val defVar = context.assignVariable(ident, expectedType)
        BeUseValue(BeUseValueReference(defVar), None)
      // Numeric, string and any other text are kept as literals.
      case _ =>
        BeUseValue(BeDataValueLiteral(trimmed), None)
    }
  }

  private def mapCppType(typeNameRaw: String): BeDataType = {
    typeNameRaw.trim.toLowerCase match {
      case "int" | "short" | "long" | "unsigned" | "unsignedint" | "uint8_t" | "uint16_t" | "uint32_t" | "size_t" => BeDataType.Int
      case "float" | "double" => BeDataType.Numeric
      case "bool" | "boolean" => BeDataType.Boolean
      case "string" | "std::string" | "arduino::string" | "char*" | "constchar*" => BeDataType.String
      case _ => BeDataType.AnyType
    }
  }

  private def findTopLevelBinaryOperator(text: String, operators: List[String]): Option[(String, String, String)] = {
    var inString: Option[Char] = None
    var parenDepth = 0
    var i = 0

    while (i < text.length) {
      val ch = text.charAt(i)
      inString match {
        case Some(q) =>
          if (ch == q) inString = None
          i += 1
        case None =>
          ch match {
            case '"' | '\'' =>
              inString = Some(ch)
              i += 1
            case '(' =>
              parenDepth += 1
              i += 1
            case ')' =>
              parenDepth = math.max(0, parenDepth - 1)
              i += 1
            case _ if parenDepth == 0 =>
              val opOpt = operators.find(op => text.regionMatches(i, op, 0, op.length))
              opOpt match {
                case Some(op) =>
                  val left = text.substring(0, i).trim
                  val right = text.substring(i + op.length).trim
                  if (left.nonEmpty && right.nonEmpty) return Some((left, op, right))
                  i += op.length
                case None =>
                  i += 1
              }
            case _ =>
              i += 1
          }
      }
    }

    None
  }

  private def parseFunctionCall(name: String, argsText: String): BeExpression = {
    val args = splitArgs(argsText)

    val parameters: List[BeDefineVariable] = args.indices.toList.map { idx =>
      BeDefineVariable(BeEntityName.fromUniversalNameInParts(s"arg${idx + 1}"), BeDataType.AnyType)
    }

    val funcDef = BeDefineFunction(
      inputs = parameters,
      outputs = None,
      body = BeSequence.optionalBody(Nil),
      functionTypeInfo = BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(name))
    )

    val valueMap: Map[BeDefineVariable, BeExpression] = parameters.zip(args).map { case (par, raw) =>
      val literal = raw.trim
      par -> BeUseValue(BeDataValueLiteral(literal), Some(par))
    }.toMap

    BeFunctionCall.fromDefinition(funcDef, valueMap)
  }

  private def splitArgs(argsText: String): List[String] =
    splitTopLevel(argsText.trim, ',').map(_.trim).filter(_.nonEmpty)

  /** Splits at `separator` outside parentheses and string literals, keeping empty, untrimmed parts. */
  private def splitTopLevel(text: String, separator: Char): List[String] = {
    val out = ListBuffer.empty[String]
    val cur = new StringBuilder
    var depth = 0
    var inString: Option[Char] = None

    def flush(): Unit = {
      out += cur.toString
      cur.clear()
    }

    text.foreach { ch =>
      inString match {
        case Some(q) =>
          cur.append(ch)
          if (ch == q) inString = None
        case None =>
          ch match {
            case c if isQuote(c) =>
              inString = Some(ch)
              cur.append(ch)
            case '(' =>
              depth += 1
              cur.append(ch)
            case ')' =>
              depth = math.max(0, depth - 1)
              cur.append(ch)
            case c if c == separator && depth == 0 =>
              flush()
            case _ =>
              cur.append(ch)
          }
      }
    }

    flush()
    out.toList
  }
}
