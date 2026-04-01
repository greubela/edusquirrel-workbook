package datastructures.core.vm.parsing.python

import PythonNormalizationModel.RawLine
import scala.collection.mutable

object PythonLineNormalizationStage {

  def normalizeLineEndingsAndDetab(source: String): String =
    source.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")

  def extractRawLines(source: String): List[RawLine] = {
    val rawLines = source.split("\n", -1).toList
    val trimmed = rawLines.map(_.replaceAll("\\s+$", ""))
    val nonEmpty = trimmed.filter(_.trim.nonEmpty)

    nonEmpty.flatMap { line =>
      val indentSpaces = line.takeWhile(_ == ' ').length
      val content = line.substring(indentSpaces)
      val (codePart, inlineComment) = PythonCommentScanner.splitInlineComment(content)
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
  }

  def computeIndentStep(indents: List[Int], defaultIndent: Int): Int = {
    val positive = indents.filter(_ > 0)
    val gcdValue = positive.reduceOption(gcd).getOrElse(defaultIndent)
    if (gcdValue == 0) defaultIndent else gcdValue
  }

  private def gcd(a: Int, b: Int): Int = if (b == 0) math.abs(a) else gcd(b, a % b)
}
