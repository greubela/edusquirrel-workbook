package datastructures.core.vm.parsing.python

object PythonLexerLike {

  final case class ParsedLine(indent: Int, content: String)

  def toParsedLines(source: String): Vector[ParsedLine] = {
    val lines = source.split("\n", -1)
    lines.toVector.map { rawLine =>
      val indent = rawLine.takeWhile(_ == ' ').length
      val content = rawLine.drop(indent)
      ParsedLine(indent, content)
    }
  }

  def splitInlineComment(line: String): (String, Option[String]) = {
    var index = 0
    var commentIndex = -1
    var stringDelimiter: Option[String] = None
    val length = line.length
    while (index < length && commentIndex == -1) {
      stringDelimiter match {
        case Some(delimiter) if delimiter.length == 1 =>
          val current = line.charAt(index)
          if (current == '\\') {
            index += 2
          } else if (current == delimiter.head) {
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
            val current = line.charAt(index)
            current match {
              case '\\' => index += 2
              case '"' =>
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
      (codePart, if (commentText.nonEmpty) Some(commentText) else Some(""))
    } else {
      (line, None)
    }
  }

  def determineBodyIndent(lines: Vector[ParsedLine], startIndex: Int, parentIndent: Int): Int = {
    var index = startIndex
    while (index < lines.length) {
      val line = lines(index)
      if (line.content.trim.nonEmpty) {
        return line.indent
      }
      index += 1
    }
    parentIndent + 4
  }

  def skipEmptyLines(lines: Vector[ParsedLine], startIndex: Int): Int = {
    var index = startIndex
    while (index < lines.length && lines(index).content.trim.isEmpty) {
      index += 1
    }
    index
  }
}
