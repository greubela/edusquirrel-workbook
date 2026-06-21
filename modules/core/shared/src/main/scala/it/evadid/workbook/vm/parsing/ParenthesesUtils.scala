package it.evadid.workbook.vm.parsing

object ParenthesesUtils {

  def stripOuterBalancedParens(text: String): String = {
    var current = text.trim
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

  def parenthesesBalanced(text: String): Boolean = {
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
}
