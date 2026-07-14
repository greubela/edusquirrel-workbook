package it.evadid.vm.parsing.python.normalization

import PythonNormalizationModel._
import it.evadid.vm.parsing.python.PythonBlockWalker
import scala.collection.mutable
import it.evadid.vm.parsing.ParenthesesUtils.stripOuterBalancedParens

object PythonStatementTreeBuilder {

  private val AugmentedAssignmentPattern =
    """^(.+?)\s*(\+=|-=|\*=|/=|//=|%=|\*\*=|<<=|>>=|&=|\|=|\^=)\s*(.+)$""".r

  private val ReturnPattern = """return\s+(.+)""".r
  private val WhilePattern = """while\s+(.+)(:)""".r
  private val IfPattern = """if\s+(.+)(:)""".r

  def parseStatements(rawLines: List[RawLine], defaultIndent: Int): ParsedStatementTree = {
    if (rawLines.isEmpty) ParsedStatementTree(Nil, defaultIndent)
    else {
      val indentStep = PythonLineNormalizationStage.computeIndentStep(rawLines.map(_.indent), defaultIndent)
      val lines = rawLines.map(raw => Line(raw.indent / indentStep, raw.text)).toVector
      val (statements, _) = parseBlockStatements(lines, 0, 0)
      ParsedStatementTree(statements, indentStep)
    }
  }

  private def parseBlockStatements(lines: Vector[Line], startIndex: Int, indentLevel: Int): (List[Statement], Int) = {
    val statements = mutable.ListBuffer[Statement]()
    var index = startIndex
    while (index < lines.length) {
      val line = lines(index)
      if (line.level < indentLevel) {
        return (statements.toList, index)
      } else if (line.level > indentLevel) {
        val (nested, nextIndex) = parseBlockStatements(lines, index, line.level)
        statements ++= nested
        index = nextIndex
      } else {
        val text = line.text
        if (isIfHeader(text)) {
          val (ifStmt, nextIndex) = parseIfChain(lines, index, indentLevel)
          statements += ifStmt
          index = nextIndex
        } else {
          val normalizedText = normalizeStatementText(text)
          val (body, nextIndex) = parseBody(lines, index + 1, indentLevel)
          if (body.nonEmpty) {
            statements += CompoundStatement(normalizedText, body)
            index = nextIndex
          } else {
            statements += SimpleStatement(normalizedText)
            index += 1
          }
        }
      }
    }
    (statements.toList, index)
  }

  private def isIfHeader(text: String): Boolean = {
    text.startsWith("if") &&
    text.endsWith(":") && {
      val afterIf = text.drop(2)
      afterIf.nonEmpty && (afterIf.head.isWhitespace || afterIf.head == '(')
    }
  }

  private def parseIfChain(lines: Vector[Line], startIndex: Int, indentLevel: Int): (IfStatement, Int) = {
    val header = lines(startIndex)
    val condition = normalizeIfCondition(header.text.stripPrefix("if").stripSuffix(":").trim)
    val (thenBranch, afterThen) = parseBody(lines, startIndex + 1, indentLevel)
    var index = afterThen
    val elifBranches = mutable.ListBuffer.empty[(String, List[Statement])]
    var elseBranch: Option[List[Statement]] = None
    var scanning = true
    while (scanning && index < lines.length) {
      val line = lines(index)
      if (line.level != indentLevel) {
        scanning = false
      } else {
        line.text match {
          case text if text.startsWith("elif ") && text.endsWith(":") =>
            val conditionText = normalizeIfCondition(text.stripPrefix("elif").stripSuffix(":").trim)
            val (branchBody, nextIndex) = parseBody(lines, index + 1, indentLevel)
            elifBranches += conditionText -> branchBody
            index = nextIndex
          case "else:" =>
            val (branchBody, nextIndex) = parseBody(lines, index + 1, indentLevel)
            elseBranch = Some(branchBody)
            index = nextIndex
            scanning = false
          case _ =>
            scanning = false
        }
      }
    }
    val nestedElse = buildNestedElseBranches(elifBranches.toList, elseBranch)
    (IfStatement(condition, thenBranch, nestedElse), index)
  }

  private def parseBody(lines: Vector[Line], startIndex: Int, parentIndent: Int): (List[Statement], Int) = {
    val walker = PythonBlockWalker.forLines(lines, _.level, _.text)
    val firstRelevant = walker.skipBlankLines(startIndex)
    if (firstRelevant >= lines.length || lines(firstRelevant).level <= parentIndent) (Nil, startIndex)
    else parseBlockStatements(lines, firstRelevant, lines(firstRelevant).level)
  }

  private def normalizeIfCondition(raw: String): String = {
    val stripped = stripOuterBalancedParens(raw.trim)
    normalizeComparisonSpacing(stripped)
  }

  private def normalizeComparisonSpacing(text: String): String = {
    val builder = new StringBuilder
    var index = 0
    var lastWasSpace = false

    while (index < text.length) {
      detectBitshiftToken(text, index) match {
        case Some((token, consumed)) =>
          if (builder.nonEmpty && !lastWasSpace) builder.append(' ')
          builder.append(token)
          builder.append(' ')
          lastWasSpace = true
          index += consumed
        case None =>
          matchComparisonOperator(text, index) match {
            case Some((operator, consumed)) =>
              if (builder.nonEmpty && !lastWasSpace) builder.append(' ')
              builder.append(operator)
              builder.append(' ')
              lastWasSpace = true
              index += consumed
            case None =>
              val ch = text.charAt(index)
              if (ch.isWhitespace) {
                if (builder.nonEmpty && !lastWasSpace) {
                  builder.append(' ')
                  lastWasSpace = true
                }
                index += 1
              } else {
                builder.append(ch)
                lastWasSpace = false
                index += 1
              }
          }
      }
    }

    builder.toString().trim
  }

  private def matchComparisonOperator(text: String, index: Int): Option[(String, Int)] = {
    if (text.startsWith("<=", index)) Some("<=" -> 2)
    else if (text.startsWith(">=", index)) Some(">=" -> 2)
    else if (text.startsWith("==", index)) Some("==" -> 2)
    else if (text.startsWith("!=", index)) Some("!=" -> 2)
    else if (text.charAt(index) == '<' && !text.startsWith("<<", index)) Some("<" -> 1)
    else if (text.charAt(index) == '>' && !text.startsWith(">>", index)) Some(">" -> 1)
    else None
  }

  private def detectBitshiftToken(text: String, index: Int): Option[(String, Int)] = {
    if (text.startsWith("<<=", index)) Some("<<=" -> 3)
    else if (text.startsWith(">>=", index)) Some(">>=" -> 3)
    else if (text.startsWith("<<", index)) Some("<<" -> 2)
    else if (text.startsWith(">>", index)) Some(">>" -> 2)
    else None
  }

  private def buildNestedElseBranches(
      elifBranches: List[(String, List[Statement])],
      finalElse: Option[List[Statement]]
  ): Option[List[Statement]] = {
    elifBranches.reverse.foldLeft(finalElse) { case (acc, (condition, body)) =>
      Some(List(IfStatement(condition, body, acc)))
    }
  }

  private def normalizeStatementText(text: String): String = {
    val withoutAugmentation = transformAugmentedAssignment(text)
    val normalized = normalizeAssignmentExpression(withoutAugmentation)
    val cleaned = normalized match {
      case ReturnPattern(body) => s"return ${stripOuterBalancedParens(body)}".trim
      case WhilePattern(condition, suffix) => s"while ${stripOuterBalancedParens(condition)}$suffix"
      case IfPattern(condition, suffix) => s"if ${stripOuterBalancedParens(condition)}$suffix"
      case other =>
        val assignmentSplit = splitSimpleAssignment(other).map { case (target, expr) =>
          s"${target.trim} = ${stripOuterBalancedParens(expr)}"
        }
        assignmentSplit.getOrElse(other)
    }

    tightenUnaryOperators(cleaned)
  }

  private def tightenUnaryOperators(text: String): String =
    text.replaceAll("""(^|[=\(])([+\-~])\s+([A-Za-z0-9_])""", "$1$2$3")

  private def transformAugmentedAssignment(text: String): String = {
    if (text.startsWith("#")) text
    else {
      text match {
        case AugmentedAssignmentPattern(target, operator, value) =>
          val base = operator.dropRight(1)
          val trimmedTarget = target.trim
          val trimmedValue = value.trim
          s"$trimmedTarget = $trimmedTarget $base $trimmedValue"
        case _ => text
      }
    }
  }

  private def normalizeAssignmentExpression(text: String): String = {
    splitSimpleAssignment(text).flatMap { case (target, expression) =>
      ArithmeticNormalization.parseArithmeticExpression(expression).map { parsedExpression =>
        s"${target.trim} = ${parsedExpression.render}"
      }
    }.getOrElse(text)
  }

  private def splitSimpleAssignment(text: String): Option[(String, String)] = {
    var index = 0
    while (index < text.length) {
      text.charAt(index) match {
        case '=' if isAssignmentEquals(text, index) =>
          val target = text.substring(0, index).trim
          val expression = text.substring(index + 1).trim
          if (target.nonEmpty && expression.nonEmpty) return Some(target -> expression)
          else return None
        case _ => index += 1
      }
    }
    None
  }

  private def isAssignmentEquals(text: String, index: Int): Boolean = {
    val previous = if (index > 0) text.charAt(index - 1) else '\u0000'
    val next = if (index + 1 < text.length) text.charAt(index + 1) else '\u0000'
    previous != '=' && previous != '!' && previous != '<' && previous != '>' && next != '='
  }

  private object ArithmeticNormalization {
    sealed trait ArithmeticExpression {
      def precedence: Int
      def render: String
    }

    final case class AtomicExpression(value: String) extends ArithmeticExpression {
      override val precedence: Int = Int.MaxValue
      override def render: String = value
    }

    final case class BinaryExpression(operator: String, left: ArithmeticExpression, right: ArithmeticExpression)
        extends ArithmeticExpression {
      override val precedence: Int = operatorPrecedence(operator)

      override def render: String = {
        val leftRendered = renderChild(left, isLeft = true)
        val rightRendered = renderChild(right, isLeft = false)
        s"$leftRendered $operator $rightRendered"
      }

      private def renderChild(child: ArithmeticExpression, isLeft: Boolean): String = {
        val needsParentheses = child match {
          case _: AtomicExpression => false
          case nested: BinaryExpression =>
            val childPrecedence = nested.precedence
            if (childPrecedence < precedence) true
            else if (childPrecedence > precedence) false
            else if (!isLeft && (operator == "-" || operator == "/" || operator == "//")) true
            else false
        }
        val rendered = child.render
        if (needsParentheses) s"($rendered)" else rendered
      }
    }

    def parseArithmeticExpression(expression: String): Option[ArithmeticExpression] = {
      val parser = new ArithmeticExpressionParser(expression)
      parser.parseExpression()
    }

    private class ArithmeticExpressionParser(expression: String) {
      private val length = expression.length
      private var index = 0

      def parseExpression(): Option[ArithmeticExpression] = {
        val parsed = parseAddSub()
        skipWhitespace()
        if (parsed.nonEmpty && index == length) parsed else None
      }

      private def parseAddSub(): Option[ArithmeticExpression] = {
        var left = parseMulDiv()
        if (left.isEmpty) return None
        var continue = true
        while (continue) {
          skipWhitespace()
          nextAddSubOperator() match {
            case Some(op) =>
              val right = parseMulDiv()
              if (right.isEmpty) return None
              left = Some(BinaryExpression(op, left.get, right.get))
            case None => continue = false
          }
        }
        left
      }

      private def parseMulDiv(): Option[ArithmeticExpression] = {
        var left = parsePrimary()
        if (left.isEmpty) return None
        var continue = true
        while (continue) {
          skipWhitespace()
          nextMulDivOperator() match {
            case Some(op) =>
              val right = parsePrimary()
              if (right.isEmpty) return None
              left = Some(BinaryExpression(op, left.get, right.get))
            case None => continue = false
          }
        }
        left
      }

      private def parsePrimary(): Option[ArithmeticExpression] = {
        skipWhitespace()
        if (index >= length) None
        else {
          expression.charAt(index) match {
            case '(' =>
              index += 1
              val inside = parseAddSub()
              skipWhitespace()
              if (inside.nonEmpty && consume(')')) inside else None
            case c if isSignedNumberStart(c) => parseNumber()
            case c if isIdentifierStart(c) => Some(parseIdentifier())
            case _ => None
          }
        }
      }

      private def parseNumber(): Option[ArithmeticExpression] = {
        val start = index
        if (expression.charAt(index) == '+' || expression.charAt(index) == '-') index += 1
        if (index >= length || !expression.charAt(index).isDigit && expression.charAt(index) != '.') return None
        while (index < length && isNumberPart(expression.charAt(index))) index += 1
        Some(AtomicExpression(expression.substring(start, index)))
      }

      private def parseIdentifier(): ArithmeticExpression = {
        val start = index
        index += 1
        while (index < length && isIdentifierPart(expression.charAt(index))) index += 1
        AtomicExpression(expression.substring(start, index))
      }

      private def nextAddSubOperator(): Option[String] = {
        if (index >= length) None
        else {
          expression.charAt(index) match {
            case '+' => index += 1; Some("+")
            case '-' => index += 1; Some("-")
            case _   => None
          }
        }
      }

      private def nextMulDivOperator(): Option[String] = {
        if (index >= length) None
        else if (expression.startsWith("//", index)) { index += 2; Some("//") }
        else {
          expression.charAt(index) match {
            case '*' => index += 1; Some("*")
            case '/' => index += 1; Some("/")
            case '%' => index += 1; Some("%")
            case _   => None
          }
        }
      }

      private def skipWhitespace(): Unit = {
        while (index < length && expression.charAt(index).isWhitespace) index += 1
      }

      private def consume(expected: Char): Boolean = {
        if (index < length && expression.charAt(index) == expected) {
          index += 1
          true
        } else false
      }

      private def isSignedNumberStart(ch: Char): Boolean = {
        if (ch == '+' || ch == '-') {
          val nextIndex = index + 1
          nextIndex < length && (expression.charAt(nextIndex).isDigit || expression.charAt(nextIndex) == '.')
        } else ch.isDigit || ch == '.'
      }

      private def isNumberPart(ch: Char): Boolean = ch.isDigit || ch == '.'

      private def isIdentifierStart(ch: Char): Boolean = ch.isLetter || ch == '_'

      private def isIdentifierPart(ch: Char): Boolean = ch.isLetterOrDigit || ch == '_' || ch == '.'
    }

    private def operatorPrecedence(operator: String): Int =
      operator match {
        case "+" | "-"              => 1
        case "*" | "/" | "//" | "%" => 2
        case _                        => Int.MaxValue
      }
  }
}
