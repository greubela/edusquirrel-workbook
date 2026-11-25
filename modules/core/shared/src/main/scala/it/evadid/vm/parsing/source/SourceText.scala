package it.evadid.vm.parsing.source

/** Text operations on (parts of) sources; nothing here parses. */
object SourceText {
  /** `source` without `ranges`; a range inside another one is removed with it. */
  def remove(source: String, ranges: Seq[(Int, Int)]): String = {
    val outer = ranges.sortBy(_._1).foldLeft(Vector.empty[(Int, Int)]) { (kept, next) =>
      if kept.lastOption.exists(_._2 >= next._2) then kept else kept :+ next
    }
    outer.reverse.foldLeft(source) { case (text, (start, end)) => text.take(start) + text.drop(end) }
  }

  /** Removes shared leading spaces and tabs; unlike Python's tokenizer it keeps tabs in the text. */
  def dedent(source: String): String = {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toVector
    val indent = lines.filter(_.trim.nonEmpty).map(_.takeWhile(c => c == ' ' || c == '\t').length).minOption.getOrElse(0)
    lines.map(_.drop(indent)).mkString("\n").trim
  }

  /** Indents every non-empty line, so blank lines carry no trailing whitespace. */
  def indent(source: String, spaces: Int = 4): String =
    source.linesIterator.map(line => if line.isEmpty then line else " " * spaces + line).mkString("\n")

  /** The text without whitespace, to compare sources regardless of layout. */
  def compact(text: String): String = text.filterNot(_.isWhitespace)
}
