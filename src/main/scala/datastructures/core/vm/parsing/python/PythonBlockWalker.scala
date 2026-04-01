package datastructures.core.vm.parsing.python

final class PythonBlockWalker[A](lines: IndexedSeq[A], indentOf: A => Int, textOf: A => String) {

  /** Skips forward while lines are blank (after trim) and returns the first non-blank index (or length). */
  def skipBlankLines(startIndex: Int): Int = {
    var index = startIndex
    while (index < lines.length && textOf(lines(index)).trim.isEmpty) {
      index += 1
    }
    index
  }

  /**
   * Finds the indentation for the next non-empty line.
   *
   * If no body line exists, falls back to `parentIndent + defaultIndentStep`.
   */
  def findBodyIndent(startIndex: Int, parentIndent: Int, defaultIndentStep: Int): Int = {
    val firstCodeIndex = skipBlankLines(startIndex)
    if (firstCodeIndex < lines.length) indentOf(lines(firstCodeIndex))
    else parentIndent + defaultIndentStep
  }

  /** True when the line at `index` is dedented relative to `indent`. */
  def isLineDedented(index: Int, indent: Int): Boolean =
    index < lines.length && indentOf(lines(index)) < indent
}

object PythonBlockWalker {
  def forLines[A](lines: IndexedSeq[A], indentOf: A => Int, textOf: A => String): PythonBlockWalker[A] =
    new PythonBlockWalker(lines, indentOf, textOf)
}
