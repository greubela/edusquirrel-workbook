package it.evadid.workbook.vm.parsing.python

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

  /** Splits a physical line into code and optional trailing inline comment text. */
  def splitCodeAndComment(line: String): (String, Option[String]) =
    PythonInlineCommentHelper.splitCodeAndComment(line)

  def findBodyIndent(lines: Vector[ParsedLine], startIndex: Int, parentIndent: Int): Int = {
    PythonBlockWalker
      .forLines(lines, _.indent, _.content)
      .findBodyIndent(startIndex, parentIndent, defaultIndentStep = 4)
  }

  def skipBlankLines(lines: Vector[ParsedLine], startIndex: Int): Int = {
    PythonBlockWalker
      .forLines(lines, _.indent, _.content)
      .skipBlankLines(startIndex)
  }
}
