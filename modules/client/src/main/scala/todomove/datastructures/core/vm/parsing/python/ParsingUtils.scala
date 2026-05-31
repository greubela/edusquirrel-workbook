package todomove.datastructures.core.vm.parsing.python

import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.code.controlStructures.BeSequence

import scala.collection.mutable.ListBuffer

object ParsingUtils {

  private val CallNamePattern = """^[A-Za-z_][A-Za-z0-9_\.]*$""".r

  def stripTrailingWhitespace(value: String): String =
    value.reverse.dropWhile(_.isWhitespace).reverse

  def keepExpression(expr: BeExpression): Boolean = {
    expr match {
      case BeSequence(body, seqInfo) => body.nonEmpty || seqInfo.mustEvaluateTo.nonEmpty
      case _ => true
    }
  }


  def unwrapRedundantParentheses(value: String): String = {
    var current = value.trim
    var continue = true

    while (continue && isParenthesized(current)) {
      val inner = current.substring(1, current.length - 1).trim
      if (inner.isEmpty) continue = false
      else current = inner
    }

    current
  }


  def isParenthesized(value: String): Boolean = {
    val trimmed = value.trim
    if (trimmed.length <= 1 || trimmed.head != '(' || trimmed.last != ')') false
    else {
      var depth = 0
      var index = 0
      var balanced = false
      while (index < trimmed.length && !balanced) {
        trimmed.charAt(index) match {
          case '(' => depth += 1
          case ')' =>
            depth -= 1
            if (depth == 0 && index != trimmed.length - 1) return false
            balanced = depth == 0 && index == trimmed.length - 1
          case _ =>
        }
        index += 1
      }
      balanced && depth == 0
    }
  }

  def splitTopLevelBinary(expression: String, operators: List[String]): Option[(String, String, String)] = {
    if (operators.isEmpty) None
    else {
      val sortedOperators = operators.sortBy(-_.length)
      var index = 0
      var parenDepth = 0
      var bracketDepth = 0
      var braceDepth = 0
      var inSingleQuote = false
      var inDoubleQuote = false
      val length = expression.length

      while (index < length) {
        val ch = expression.charAt(index)
        val prev = if (index > 0) expression.charAt(index - 1) else '\u0000'

        if (!inSingleQuote && !inDoubleQuote) {
          ch match {
            case '(' => parenDepth += 1
            case ')' => parenDepth = math.max(0, parenDepth - 1)
            case '[' => bracketDepth += 1
            case ']' => bracketDepth = math.max(0, bracketDepth - 1)
            case '{' => braceDepth += 1
            case '}' => braceDepth = math.max(0, braceDepth - 1)
            case _ =>
          }
        }

        inSingleQuote =
          if (inSingleQuote) {
            if (ch == '\'' && prev != '\\') false else true
          } else if (!inDoubleQuote && ch == '\'' && prev != '\\') true
          else false

        inDoubleQuote =
          if (inDoubleQuote) {
            if (ch == '"' && prev != '\\') false else true
          } else if (!inSingleQuote && ch == '"' && prev != '\\') true
          else false

        if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && !inSingleQuote && !inDoubleQuote) {
          sortedOperators.find(op => expression.startsWith(op, index)) match {
            case Some(op) =>
              val beforeIndex = index - 1
              val afterIndex = index + op.length
              val beforeChar = if (beforeIndex >= 0) Some(expression.charAt(beforeIndex)) else None
              val afterChar = if (afterIndex < length) Some(expression.charAt(afterIndex)) else None

              val startsWithLetter = op.headOption.exists(_.isLetter)
              val endsWithLetter = op.lastOption.exists(_.isLetter)

              val isIdentifierChar: Char => Boolean = ch => ch.isLetterOrDigit || ch == '_'

              val beforeOk =
                if (startsWithLetter) beforeChar.forall(ch => !isIdentifierChar(ch))
                else true

              val afterOk =
                if (endsWithLetter) afterChar.forall(ch => !isIdentifierChar(ch))
                else true

              val overlapsWithBitshift =
                (op == "<" && (beforeChar.contains('<') || afterChar.contains('<'))) ||
                (op == ">" && (beforeChar.contains('>') || afterChar.contains('>')))

              if (beforeOk && afterOk && !overlapsWithBitshift) {
                val left = expression.substring(0, index)
                val right = expression.substring(index + op.length)
                if (left.trim.nonEmpty && right.trim.nonEmpty) {
                  return Some((left, op, right))
                }
              }
            case None =>
          }
        }

        index += 1
      }
      None
    }
  }

  def splitTopLevelArguments(arguments: String): List[String] = {
    val parts = ListBuffer[String]()
    var index = 0
    var start = 0
    var parenDepth = 0
    var bracketDepth = 0
    var braceDepth = 0
    var inSingleQuote = false
    var inDoubleQuote = false

    while (index < arguments.length) {
      val ch = arguments.charAt(index)
      val prev = if (index > 0) arguments.charAt(index - 1) else '\u0000'

      if (!inSingleQuote && !inDoubleQuote) {
        ch match {
          case '(' => parenDepth += 1
          case ')' => parenDepth = math.max(0, parenDepth - 1)
          case '[' => bracketDepth += 1
          case ']' => bracketDepth = math.max(0, bracketDepth - 1)
          case '{' => braceDepth += 1
          case '}' => braceDepth = math.max(0, braceDepth - 1)
          case ',' if parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 =>
            parts += arguments.substring(start, index)
            start = index + 1
          case _ =>
        }
      }

      if (!inDoubleQuote && ch == '\'' && prev != '\\') {
        inSingleQuote = !inSingleQuote
      } else if (!inSingleQuote && ch == '"' && prev != '\\') {
        inDoubleQuote = !inDoubleQuote
      }

      index += 1
    }

    if (start <= arguments.length) {
      parts += arguments.substring(start)
    }
    parts.toList
  }

  def findTopLevelCall(expression: String): Option[(String, String)] = {
    val trimmed = expression.trim
    if (!trimmed.endsWith(")")) return None

    var index = 0
    var inSingleQuote = false
    var inDoubleQuote = false
    var parenDepth = 0
    var openIndex = -1

    while (index < trimmed.length) {
      val ch = trimmed.charAt(index)
      val prev = if (index > 0) trimmed.charAt(index - 1) else '\u0000'

      if (!inDoubleQuote && ch == '\'' && prev != '\\') {
        inSingleQuote = !inSingleQuote
      } else if (!inSingleQuote && ch == '"' && prev != '\\') {
        inDoubleQuote = !inDoubleQuote
      } else if (!inSingleQuote && !inDoubleQuote) {
        ch match {
          case '(' =>
            if (openIndex == -1) openIndex = index
            parenDepth += 1
          case ')' =>
            if (parenDepth > 0) {
              parenDepth -= 1
              if (parenDepth == 0 && openIndex != -1) {
                val after = trimmed.substring(index + 1).trim
                if (after.isEmpty) {
                  val name = trimmed.substring(0, openIndex).trim
                  if (name.nonEmpty && CallNamePattern.matches(name)) {
                    val inner = trimmed.substring(openIndex + 1, index)
                    return Some((name, inner))
                  } else {
                    return None
                  }
                } else {
                  return None
                }
              }
            }
          case _ =>
        }
      }

      index += 1
    }
    None
  }
}
