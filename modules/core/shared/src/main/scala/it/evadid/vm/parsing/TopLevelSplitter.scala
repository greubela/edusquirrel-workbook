package it.evadid.vm.parsing

import it.evadid.core.util.io.Serializer

/** Splits source literals at separators outside quoted text (with backslash escapes) and outside the nesting
  * characters `openers`/`closers`. Parts are trimmed; only a trailing empty part is dropped.
  */
final case class TopLevelSplitter(openers: Set[Char], closers: Set[Char]) {
  import TopLevelSplitter.Scan

  def split(text: String, separator: Char): List[String] = {
    val cuts = text.indices.foldLeft(Scan.start)((scan, index) => step(scan, text(index), index, separator)).cuts
    val parts = ((-1 +: cuts) zip (cuts :+ text.length)).map((from, to) => text.substring(from + 1, to).trim).toList
    if parts.last.isEmpty then parts.init else parts
  }

  /** `open` elements `close`, e.g. `[1, 2]`; elements are split at top-level commas. */
  def collectionSerializer[Element](element: Serializer[Element], open: String, close: String): Serializer[List[Element]] =
    new Serializer[List[Element]] {
      override def serialize(obj: List[Element]): String = obj.map(element.serialize).mkString(open, ", ", close)
      override def deserialize(serialized: String): List[Element] = elements(serialized, open, close).map(element.deserialize)
    }

  /** The top-level comma-separated elements inside optional `open`/`close` delimiters. */
  def elements(serialized: String, open: String, close: String): List[String] = {
    val trimmed = serialized.trim
    val inner = if trimmed.startsWith(open) && trimmed.endsWith(close) then trimmed.drop(open.length).dropRight(close.length).trim else trimmed
    if inner.isEmpty then Nil else split(inner, ',')
  }

  private def step(scan: Scan, char: Char, index: Int, separator: Char): Scan = scan.quote match {
    case Some(_) if scan.escaped => scan.copy(escaped = false)
    case Some(_) if char == '\\' => scan.copy(escaped = true)
    case Some(active) => if char == active then scan.copy(quote = None) else scan
    case None if char == '\'' || char == '"' => scan.copy(quote = Some(char))
    case None if openers(char) => scan.copy(nesting = scan.nesting + 1)
    case None if closers(char) => scan.copy(nesting = scan.nesting - 1)
    case None if char == separator && scan.nesting == 0 => scan.copy(cuts = scan.cuts :+ index)
    case None => scan
  }
}

object TopLevelSplitter {
  /** Round, square and curly brackets. */
  val brackets: TopLevelSplitter = TopLevelSplitter(Set('[', '(', '{'), Set(']', ')', '}'))

  private final case class Scan(cuts: Vector[Int], nesting: Int, quote: Option[Char], escaped: Boolean)
  private object Scan {
    val start: Scan = Scan(Vector.empty, 0, None, escaped = false)
  }
}
