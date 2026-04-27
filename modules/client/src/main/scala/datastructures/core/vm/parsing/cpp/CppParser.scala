package datastructures.core.vm.parsing.cpp

import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import datastructures.core.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import datastructures.core.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import datastructures.core.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import datastructures.core.vm.parsing.python.DefaultDefinitions
import datastructures.core.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference}
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*

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
      val variable = BeDefineVariable(LanguageMap.universalMap(name), dataType)
      scopes.head.update(name, variable)
      variable
    }

    def assignVariable(name: String, dataType: BeDataType): BeDefineVariable = {
      lookupVariable(name).getOrElse {
        val variable = BeDefineVariable(LanguageMap.universalMap(name), dataType)
        scopes.head.update(name, variable)
        variable
      }
    }
  }

  final case class ParseResult(sequence: BeSequence, unsupportedStatements: List[String])

  def parseCppWithDiagnostics(source: String): ParseResult = {
    val stripped = stripWrappers(removeBlockComments(removeLineComments(source)))
    val (expressions, unsupported) = parseStatements(stripped)
    ParseResult(BeSequence.optionalBody(expressions), unsupported)
  }

  def parseCpp(source: String): BeSequence = {
    parseCppWithDiagnostics(source).sequence
  }

  private def removeLineComments(source: String): String =
    source
      .linesIterator
      .map { line =>
        val idx = line.indexOf("//")
        if (idx >= 0) line.substring(0, idx) else line
      }
      .mkString("\n")

  private def removeBlockComments(source: String): String = {
    val out = new StringBuilder
    var i = 0
    while (i < source.length) {
      if (i + 1 < source.length && source.charAt(i) == '/' && source.charAt(i + 1) == '*') {
        i += 2
        while (i + 1 < source.length && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i += 1
        if (i + 1 < source.length) i += 2
      } else {
        out.append(source.charAt(i))
        i += 1
      }
    }
    out.toString
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

    private def readBalanced(open: Char, close: Char): Either[String, String] = {
      if (peek != open) return Left(s"Expected '$open'")
      i += 1

      val out = new StringBuilder
      var depth = 1
      var inString: Option[Char] = None

      while (!eof && depth > 0) {
        val ch = s.charAt(i)
        inString match {
          case Some(q) =>
            out.append(ch)
            i += 1
            if (ch == q) inString = None
          case None =>
            ch match {
              case '"' | '\'' =>
                inString = Some(ch)
                out.append(ch)
                i += 1
              case c if c == open =>
                depth += 1
                out.append(ch)
                i += 1
              case c if c == close =>
                depth -= 1
                if (depth > 0) out.append(ch)
                i += 1
              case _ =>
                out.append(ch)
                i += 1
            }
        }
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

        if (inString.nonEmpty) {
          out.append(ch)
          i += 1
          if (ch == inString.get) inString = None
        } else {
          ch match {
            case '"' | '\'' =>
              inString = Some(ch)
              out.append(ch)
              i += 1
            case '(' =>
              parenDepth += 1
              out.append(ch)
              i += 1
            case ')' =>
              parenDepth = math.max(0, parenDepth - 1)
              out.append(ch)
              i += 1
            case '{' =>
              braceDepth += 1
              out.append(ch)
              i += 1
            case '}' =>
              // If we're at the end of a nested brace section inside this statement, keep it.
              if (braceDepth > 0) {
                braceDepth -= 1
                out.append(ch)
                i += 1
              } else {
                // End of the surrounding block.
                return out.toString
              }
            case ';' if parenDepth == 0 && braceDepth == 0 =>
              i += 1 // consume ';'
              return out.toString
            case _ =>
              out.append(ch)
              i += 1
          }
        }
      }

      out.toString
    }

    def parseStatementsUntil(stopChar: Option[Char]): (List[BeExpression], List[String]) = {
      val expressions = scala.collection.mutable.ListBuffer.empty[BeExpression]
      val unsupported = scala.collection.mutable.ListBuffer.empty[String]

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
          val stmtText = readUntilStatementEnd().trim
          if (stmtText.nonEmpty) {
            parseSimpleStatement(stmtText) match {
              case Right(expr) => expressions += expr
              case Left(bad) =>
                if (bad.trim.nonEmpty) unsupported += bad
                expressions += BeExpressionUnsupported(bad)
            }
          }
        }
      }

      (expressions.toList, unsupported.toList)
    }

    private def parseWhile(unsupported: scala.collection.mutable.ListBuffer[String]): BeExpression = {
      val startIndex = i

      // consume 'while'
      i += "while".length
      skipWhitespace()

      val conditionText = readBalanced('(', ')') match {
        case Right(text) => text
        case Left(err) =>
          val remainder = readUntilStatementEnd().trim
          val original = s.substring(startIndex, math.min(s.length, i)).trim + (if (remainder.nonEmpty) remainder else "")
          val msg = s"C++ while: condition parse error: $err"
          unsupported += original
          return BeExpressionUnparsable(original, msg)
      }

      skipWhitespace()

      val bodyText = readBalanced('{', '}') match {
        case Right(text) => text
        case Left(err) =>
          val remainder = readUntilStatementEnd().trim
          val original = s.substring(startIndex, math.min(s.length, i)).trim + (if (remainder.nonEmpty) remainder else "")
          val msg = s"C++ while: body parse error: $err"
          unsupported += original
          return BeExpressionUnparsable(original, msg)
      }

      val conditionSeq = parseCondition(conditionText, context)

      // New scope for the while body.
      context.pushScope()
      val (bodyExpressions, bodyUnsupported) = new Parser(bodyText, context).parseStatementsUntil(stopChar = None)
      context.popScope()
      val bodySeq = BeSequence.optionalBody(bodyExpressions)

      // Bubble up diagnostics, but keep the unsupported statements as red blocks in the body.
      bodyUnsupported.foreach(stmt => if (stmt.trim.nonEmpty) unsupported += stmt)
      BeWhile(conditionSeq, bodySeq)
    }

    private def parseIfElse(unsupported: scala.collection.mutable.ListBuffer[String]): BeExpression = {
      val startIndex = i

      // consume 'if'
      i += "if".length
      skipWhitespace()

      val conditionText = readBalanced('(', ')') match {
        case Right(text) => text
        case Left(err) =>
          val remainder = readUntilStatementEnd().trim
          val original = s.substring(startIndex, math.min(s.length, i)).trim + (if (remainder.nonEmpty) remainder else "")
          val msg = s"C++ if: condition parse error: $err"
          unsupported += original
          return BeExpressionUnparsable(original, msg)
      }

      skipWhitespace()

      val thenBodyText = readBalanced('{', '}') match {
        case Right(text) => text
        case Left(err) =>
          val remainder = readUntilStatementEnd().trim
          val original = s.substring(startIndex, math.min(s.length, i)).trim + (if (remainder.nonEmpty) remainder else "")
          val msg = s"C++ if: body parse error: $err"
          unsupported += original
          return BeExpressionUnparsable(original, msg)
      }

      val conditionSeq = parseCondition(conditionText, context)

      context.pushScope()
      val (thenExpressions, thenUnsupported) = new Parser(thenBodyText, context).parseStatementsUntil(stopChar = None)
      context.popScope()
      thenUnsupported.foreach(stmt => if (stmt.trim.nonEmpty) unsupported += stmt)

      skipWhitespace()

      val elseExpressions: List[BeExpression] =
        if (startsWithKeyword("else")) {
          i += "else".length
          skipWhitespace()

          if (startsWithKeyword("if")) {
            // else-if chain; parse nested if and keep it as the single expression in else body.
            List(parseIfElse(unsupported))
          } else {
            val elseBodyText = readBalanced('{', '}') match {
              case Right(text) => text
              case Left(err) =>
                val remainder = readUntilStatementEnd().trim
                val original = s.substring(startIndex, math.min(s.length, i)).trim + (if (remainder.nonEmpty) remainder else "")
                val msg = s"C++ else: body parse error: $err"
                unsupported += original
                return BeExpressionUnparsable(original, msg)
            }

            context.pushScope()
            val (elseBodyExpressions, elseUnsupported) = new Parser(elseBodyText, context).parseStatementsUntil(stopChar = None)
            context.popScope()
            elseUnsupported.foreach(stmt => if (stmt.trim.nonEmpty) unsupported += stmt)
            elseBodyExpressions
          }
        } else Nil

      val thenSeq = BeSequence.optionalBody(thenExpressions)
      val elseSeq = BeSequence.optionalBody(elseExpressions)
      BeIfElse(conditionSeq, thenSeq, elseSeq)
    }

    private def parseForLoop(unsupported: scala.collection.mutable.ListBuffer[String]): BeExpression = {
      val startIndex = i

      // consume 'for'
      i += "for".length
      skipWhitespace()

      val headerText = readBalanced('(', ')') match {
        case Right(text) => text
        case Left(err) =>
          val remainder = readUntilStatementEnd().trim
          val original = s.substring(startIndex, math.min(s.length, i)).trim + (if (remainder.nonEmpty) remainder else "")
          val msg = s"C++ for: header parse error: $err"
          unsupported += original
          return BeExpressionUnparsable(original, msg)
      }

      skipWhitespace()

      val bodyText = readBalanced('{', '}') match {
        case Right(text) => text
        case Left(err) =>
          val remainder = readUntilStatementEnd().trim
          val original = s.substring(startIndex, math.min(s.length, i)).trim + (if (remainder.nonEmpty) remainder else "")
          val msg = s"C++ for: body parse error: $err"
          unsupported += original
          return BeExpressionUnparsable(original, msg)
      }

      val parts = splitTopLevel(headerText, ';').map(_.trim)
      if (parts.length != 3) {
        val original = s"for(${headerText.trim}){...}"
        unsupported += original
        return BeExpressionUnsupported(original)
      }

      val initPart = parts(0)
      val condPart = parts(1)
      val incPart = parts(2)

      parseCountedFor(initPart, condPart, incPart) match {
        case Some(amount) =>
          context.pushScope()
          val (bodyExpressions, bodyUnsupported) = new Parser(bodyText, context).parseStatementsUntil(stopChar = None)
          context.popScope()
          bodyUnsupported.foreach(stmt => if (stmt.trim.nonEmpty) unsupported += stmt)
          BeRepeatNr(amount, BeSequence.optionalBody(bodyExpressions))
        case None =>
          val original = s"for(${headerText.trim}){...}"
          unsupported += original
          BeExpressionUnsupported(original)
      }
    }

    private def parseCountedFor(initPart: String, condPart: String, incPart: String): Option[Int] = {
      // Accept e.g.: int i = 0; i < 10; i++
      val init = initPart.trim
      val initNameAndStart: Option[(String, BigInt)] =
        init match {
          case DeclarationPattern(_, varName, initRaw) if initRaw != null =>
            scala.util.Try(BigInt(initRaw.trim)).toOption.map(v => varName -> v)
          case AssignmentPattern(varName, rhs) =>
            scala.util.Try(BigInt(rhs.trim)).toOption.map(v => varName -> v)
          case _ => None
        }

      initNameAndStart match {
        case None => None
        case Some((loopVar, start)) =>
          val stepOk: Boolean = {
            val inc = incPart.trim.replaceAll("\\s+", "")
            inc == s"${loopVar}++" || inc == s"++${loopVar}" || inc == s"${loopVar}+=1" ||
              inc == s"${loopVar}=${loopVar}+1" || inc == s"${loopVar}=1+${loopVar}"
          }
          if (!stepOk) return None

          val cond = condPart.trim
          val condMatch = findTopLevelBinaryOperator(cond, List("<=", "<")).flatMap { case (left, op, right) =>
            if (left.trim.replaceAll("\\s+", "") != loopVar) None
            else scala.util.Try(BigInt(right.trim)).toOption.map(bound => op -> bound)
          }

          condMatch match {
            case None => None
            case Some((op, bound)) =>
              val countOpt: Option[BigInt] = op match {
                case "<" => Some(bound - start)
                case "<=" => Some((bound - start) + 1)
                case _ => None
              }

              countOpt.flatMap { count =>
                if (count < 0 || count > Int.MaxValue) None
                else Some(count.toInt)
              }
          }
      }
    }

    private def splitTopLevel(text: String, separator: Char): List[String] = {
      val out = scala.collection.mutable.ListBuffer.empty[String]
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
              case '"' | '\'' =>
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
        val inputs = funcDef.inputs
        val leftVar = inputs.headOption
        val rightVar = inputs.drop(1).headOption

        val leftExpr = leftVar.map(v => parseOperand(left, v.variableType, context)).getOrElse(parseOperand(left, BeDataType.AnyType, context))
        val rightExpr = rightVar.map(v => parseOperand(right, v.variableType, context)).getOrElse(parseOperand(right, BeDataType.AnyType, context))

        val valueMap: Map[BeDefineVariable, BeExpression] = (leftVar.toList.map(_ -> leftExpr) ++ rightVar.toList.map(_ -> rightExpr)).toMap
        BeSequence.conditionalBody(List(BeFunctionCall(funcDef, valueMap)))
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
      case ident if ident.matches("^[A-Za-z_][A-Za-z0-9_]*$") =>
        val defVar = context.assignVariable(ident, expectedType)
        BeUseValue(BeUseValueReference(defVar), None)
      case _ if BeDataType.Numeric.isValidLiteral(trimmed) || scala.util.Try(BigDecimal(trimmed)).isSuccess =>
        BeUseValue(BeDataValueLiteral(trimmed), None)
      case _ if BeDataType.Boolean.isValidLiteral(trimmed) =>
        BeUseValue(BeDataValueLiteral(trimmed), None)
      case _ if BeDataType.String.isValidLiteral(trimmed) =>
        BeUseValue(BeDataValueLiteral(trimmed), None)
      case _ =>
        BeUseValue(BeDataValueLiteral(trimmed), None)
    }
  }

  private def mapCppType(typeNameRaw: String): BeDataType = {
    val t = typeNameRaw.trim
    val lower = t.toLowerCase
    lower match {
      case "int" | "short" | "long" | "unsigned" | "unsignedint" | "uint8_t" | "uint16_t" | "uint32_t" | "size_t" => BeDataType.Int
      case "float" | "double" => BeDataType.Numeric
      case "bool" | "boolean" => BeDataType.Boolean
      case "string" | "std::string" | "arduino::string" | "string" | "char*" | "constchar*" => BeDataType.String
      case _ if t == "String" => BeDataType.String
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

    val parameters: List[BeDefineVariable] = args.zipWithIndex.map { case (_, idx) =>
      BeDefineVariable(LanguageMap.universalMap[HumanLanguage](s"arg${idx + 1}"), BeDataType.AnyType)
    }

    val funcDef = BeDefineFunction(
      inputs = parameters,
      outputs = None,
      body = BeSequence.optionalBody(Nil),
      functionTypeInfo = BeDefineFunction.functionInfo(LanguageMap.universalMap[HumanLanguage](name))
    )

    val valueMap: Map[BeDefineVariable, BeExpression] = parameters.zip(args).map { case (par, raw) =>
      val literal = raw.trim
      par -> BeUseValue(BeDataValueLiteral(literal), Some(par))
    }.toMap

    BeFunctionCall(funcDef, valueMap)
  }

  private def splitArgs(argsText: String): List[String] = {
    val trimmed = argsText.trim
    if (trimmed.isEmpty) return Nil

    val out = scala.collection.mutable.ListBuffer.empty[String]
    val cur = new StringBuilder
    var depth = 0
    var inString: Option[Char] = None

    def flush(): Unit = {
      val s = cur.toString.trim
      if (s.nonEmpty) out += s
      cur.clear()
    }

    trimmed.foreach { ch =>
      inString match {
        case Some(quote) =>
          cur.append(ch)
          if (ch == quote) inString = None
        case None =>
          ch match {
            case '"' | '\'' =>
              inString = Some(ch)
              cur.append(ch)
            case '(' =>
              depth += 1
              cur.append(ch)
            case ')' =>
              depth = math.max(0, depth - 1)
              cur.append(ch)
            case ',' if depth == 0 =>
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
