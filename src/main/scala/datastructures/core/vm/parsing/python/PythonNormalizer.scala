package datastructures.core.vm.parsing.python

import PythonNormalizationModel.{ParsedStatementTree, RawLine}

class PythonNormalizer {

  private val DefaultIndent = 4

  def normalizeLineEndings(source: String): String =
    PythonLineNormalizationStage.normalizeLineEndingsAndDetab(source)

  def normalizePython(source: String): String = {
    val normalizedAndDetabbed = normalizeLineEndings(source)
    val rawLines = extractRawLines(normalizedAndDetabbed)
    if (rawLines.isEmpty) ""
    else {
      val statementTree = parseStatements(rawLines)
      renderNormalizedOutput(statementTree)
    }
  }

  def extractRawLines(normalizedAndDetabbed: String): List[RawLine] =
    PythonLineNormalizationStage.extractRawLines(normalizedAndDetabbed)

  def parseStatements(rawLines: List[RawLine]): ParsedStatementTree =
    PythonStatementTreeBuilder.parseStatements(rawLines, DefaultIndent)

  def renderNormalizedOutput(statementTree: ParsedStatementTree): String =
    PythonNormalizationRenderer.renderNormalizedOutput(statementTree.statements, statementTree.indentStep)
}
