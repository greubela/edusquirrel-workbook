package contentmanagement.model.vm.parsing.python

import scala.collection.mutable

class PythonNormalizer {

  def normalizeLineEndings(source: String): String =
    source.replace("\r\n", "\n").replace('\r', '\n')

  private val AugmentedAssignmentPattern =
    """^(.+?)\s*(\+=|-=|\*=|/=|//=|%=|\*\*=|<<=|>>=|&=|\|=|\^=)\s*(.+)$""".r

  def normalizePython(source: String): String = {
    val unifiedNewlines = normalizeLineEndings(source)
    val detabbed = unifiedNewlines.replace("\t", "    ")
    val rawLines = detabbed.split("\n", -1).toList
    val trimmed = rawLines.map(_.replaceAll("\\s+$", ""))
    val nonEmpty = trimmed.filter(_.trim.nonEmpty)
    if (nonEmpty.isEmpty) ""
    else {
    val processed = nonEmpty.flatMap { line =>
      val indentSpaces = line.takeWhile(_ == ' ').length
      val content = line.substring(indentSpaces)
      val (codePart, inlineComment) = splitInlineComment(content)
      val entries = mutable.ListBuffer[RawLine]()
      val codeText = codePart.trim
      if (codeText.nonEmpty) {
        entries += RawLine(indentSpaces, codeText)
      }
      inlineComment.foreach { commentText =>
        val normalizedComment = if (commentText.nonEmpty) s"# $commentText" else "#"
        entries += RawLine(indentSpaces, normalizedComment)
      }
      if (entries.isEmpty) List(RawLine(indentSpaces, "")) else entries.toList
    }
      val indentStep = computeIndentStep(processed.map(_.indent))
      val lines = processed.map(raw => Line(raw.indent / indentStep, raw.text)).toVector
      val (statements, _) = parseStatements(lines, 0, 0)
      renderStatements(statements, 0, indentStep).mkString("\n")
    }
  }

  private case class RawLine(indent: Int, text: String)
  private case class Line(level: Int, text: String)

  private sealed trait Statement
  private case class SimpleStatement(text: String) extends Statement
  private case class CompoundStatement(header: String, body: List[Statement]) extends Statement
  private case class IfStatement(condition: String, thenBranch: List[Statement], elseBranch: Option[List[Statement]])
      extends Statement

  private def splitInlineComment(line: String): (String, Option[String]) = {
    var index = 0
    var commentIndex = -1
    var stringDelimiter: Option[String] = None
    val length = line.length
    while (index < length && commentIndex == -1) {
      stringDelimiter match {
        case Some(delimiter) if delimiter.length == 1 =>
          if (line.charAt(index) == '\\') {
            index = math.min(index + 2, length)
          } else if (line.charAt(index) == delimiter.head) {
            stringDelimiter = None
            index += 1
          } else {
            index += 1
          }
        case Some(delimiter) =>
          if (line.startsWith(delimiter, index)) {
            stringDelimiter = None
            index += delimiter.length
          } else {
            index += 1
          }
        case None =>
          if (line.startsWith("\"\"\"", index)) {
            stringDelimiter = Some("\"\"\"")
            index += 3
          } else if (line.startsWith("'''", index)) {
            stringDelimiter = Some("'''")
            index += 3
          } else {
            line.charAt(index) match {
              case '\\' => index = math.min(index + 2, length)
              case '\"' =>
                stringDelimiter = Some("\"")
                index += 1
              case '\'' =>
                stringDelimiter = Some("'")
                index += 1
              case '#' =>
                commentIndex = index
              case _ =>
                index += 1
            }
          }
      }
    }
    if (commentIndex >= 0) {
      val codePart = line.substring(0, commentIndex)
      val commentText = line.substring(commentIndex + 1).trim
      (codePart, Some(commentText))
    } else {
      (line, None)
    }
  }

  private sealed trait ArithmeticExpression {
    def precedence: Int
    def render: String
  }

  private case class AtomicExpression(value: String) extends ArithmeticExpression {
    override val precedence: Int = Int.MaxValue
    override def render: String = value
  }

  private case class BinaryExpression(operator: String, left: ArithmeticExpression, right: ArithmeticExpression)
      extends ArithmeticExpression {
    override val precedence: Int = operatorPrecedence(operator)

    override def render: String = {
      val leftRendered = renderChild(left, isLeft = true)
      val rightRendered = renderChild(right, isLeft = false)
      s"$leftRendered $operator $rightRendered"
    }

    private def renderChild(child: ArithmeticExpression, isLeft: Boolean): String = {
      val needsParentheses = child match {
        case atomic: AtomicExpression => false
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

  private def computeIndentStep(indents: List[Int]): Int = {
    val positive = indents.filter(_ > 0)
    val gcdValue = positive.reduceOption(gcd).getOrElse(DefaultIndent)
    if (gcdValue == 0) DefaultIndent else gcdValue
  }

  private def gcd(a: Int, b: Int): Int = if (b == 0) math.abs(a) else gcd(b, a % b)

  private def parseStatements(lines: Vector[Line], startIndex: Int, indentLevel: Int): (List[Statement], Int) = {
    val statements = mutable.ListBuffer[Statement]()
    var index = startIndex
    while (index < lines.length) {
      val line = lines(index)
      if (line.level < indentLevel) {
        return (statements.toList, index)
      } else if (line.level > indentLevel) {
        val (nested, nextIndex) = parseStatements(lines, index, line.level)
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

  private def normalizeIfCondition(raw: String): String = {
    val stripped = stripOuterParentheses(raw.trim)
    normalizeComparisonSpacing(stripped)
  }

  private def stripOuterParentheses(text: String): String = {
    var current = text
    var continue = true
    while (
      continue &&
      current.length >= 2 &&
      current.head == '(' &&
      current.last == ')' &&
      parenthesesBalanced(current.substring(1, current.length - 1))
    ) {
      current = current.substring(1, current.length - 1).trim
    }
    current
  }

  private def parenthesesBalanced(text: String): Boolean = {
    var depth = 0
    var index = 0
    var balanced = true
    while (index < text.length && balanced) {
      text.charAt(index) match {
        case '(' => depth += 1
        case ')' =>
          if (depth == 0) balanced = false else depth -= 1
        case _ =>
      }
      index += 1
    }
    balanced && depth == 0
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

  private def parseBody(lines: Vector[Line], startIndex: Int, parentIndent: Int): (List[Statement], Int) = {
    if (startIndex >= lines.length) (Nil, startIndex)
    else {
      val nextLine = lines(startIndex)
      if (nextLine.level <= parentIndent) (Nil, startIndex)
      else parseStatements(lines, startIndex, nextLine.level)
    }
  }

  private def renderStatements(statements: List[Statement], indentLevel: Int, indentStep: Int): List[String] = {
    val rendered = mutable.ListBuffer[String]()
    statements.foreach {
      case SimpleStatement(text) =>
        rendered += formatLine(indentLevel, indentStep, text)
      case CompoundStatement(header, body) =>
        rendered += formatLine(indentLevel, indentStep, header)
        rendered ++= renderStatements(body, indentLevel + 1, indentStep)
      case IfStatement(condition, thenBranch, elseBranch) =>
        rendered += formatLine(indentLevel, indentStep, s"if $condition:")
        rendered ++= renderStatements(ensureNonEmpty(thenBranch), indentLevel + 1, indentStep)
        elseBranch.foreach { branch =>
          rendered += formatLine(indentLevel, indentStep, "else:")
          rendered ++= renderStatements(ensureNonEmpty(branch), indentLevel + 1, indentStep)
        }
    }
    rendered.toList
  }

  private def ensureNonEmpty(body: List[Statement]): List[Statement] =
    if (body.nonEmpty) body else List(SimpleStatement("pass"))

  private def formatLine(indentLevel: Int, indentStep: Int, text: String): String = {
    val indent = " " * (indentLevel * indentStep)
    indent + text
  }

  private def normalizeStatementText(text: String): String = {
    val withoutAugmentation = transformAugmentedAssignment(text)
    val normalized = normalizeAssignmentExpression(withoutAugmentation)
    val cleaned = normalized match {
      case ReturnPattern(body) => s"return ${stripOuterParentheses(body)}".trim
      case WhilePattern(condition, suffix) => s"while ${stripOuterParentheses(condition)}$suffix"
      case IfPattern(condition, suffix) => s"if ${stripOuterParentheses(condition)}$suffix"
      case other =>
        val assignmentSplit = splitSimpleAssignment(other).map { case (target, expr) =>
          s"${target.trim} = ${stripOuterParentheses(expr)}"
        }
        assignmentSplit.getOrElse(other)
    }

    tightenUnaryOperators(cleaned)
  }

  private val ReturnPattern = """return\s+(.+)""".r
  private val WhilePattern = """while\s+(.+)(:)""".r
  private val IfPattern = """if\s+(.+)(:)""".r

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
      parseArithmeticExpression(expression).map { parsedExpression =>
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

  private def parseArithmeticExpression(expression: String): Option[ArithmeticExpression] = {
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
      case "+" | "-"           => 1
      case "*" | "/" | "//" | "%" => 2
      case _                     => Int.MaxValue
    }

  private val DefaultIndent = 4
}
