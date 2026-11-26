package it.evadid.vm.parsing.source

/** Characters `[start, end)` of a source with the 1-based lines of its first and last character. */
final case class SourceSpan(start: Int, end: Int, startLine: Int, endLine: Int) {
  require(start >= 0 && end >= start && startLine >= 1 && endLine >= startLine, s"Invalid source span $this.")

  def text(source: String): String = source.substring(start, end)
  def contains(other: SourceSpan): Boolean = start <= other.start && other.end <= end
  def contains(offset: Int): Boolean = start <= offset && offset < end
}

/** Line starts of one source: offsets become 1-based lines and columns. `\r\n`, `\r` and `\n` end lines. */
final class SourceLines(source: String) {
  private val starts: Vector[Int] = 0 +: source.indices.collect {
    case index if source(index) == '\n' || (source(index) == '\r' && !source.lift(index + 1).contains('\n')) => index + 1
  }.toVector

  /** The line of the character at `offset` (the last line for the end of the source). */
  def line(offset: Int): Int = {
    val searched = starts.search(offset.max(0))
    searched match {
      case scala.collection.Searching.Found(index) => index + 1
      case scala.collection.Searching.InsertionPoint(index) => index
    }
  }

  /** The 1-based column of `offset` on its line. */
  def column(offset: Int): Int = offset - starts(line(offset) - 1) + 1

  /** The offset of 1-based `line` and `column`, within the source. */
  def offset(line: Int, column: Int): Int =
    starts.lift(line - 1).fold(source.length)(start => (start + column - 1).min(source.length).max(0))

  def span(start: Int, end: Int): SourceSpan = SourceSpan(start, end, line(start), line(if end > start then end - 1 else start))

  def lineCount: Int = starts.size
}
