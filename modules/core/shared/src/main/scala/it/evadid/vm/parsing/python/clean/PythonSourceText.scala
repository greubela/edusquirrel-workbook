package it.evadid.vm.parsing.python.clean

/** Parser normalization with an explicit map back to the original UTF-16 offsets. */
private[clean] final case class PythonSourceText(text: String, offsets: Vector[Int], end: Int) derives upickle.default.ReadWriter {
  def originalOffset(index: Int): Int = offsets.lift(index).getOrElse(end)
}

private[clean] object PythonSourceText {
  def apply(source: String): PythonSourceText = {
    val normalized = source.indices.flatMap { index => source(index) match {
      case '\n' if index > 0 && source(index - 1) == '\r' => Vector.empty
      case '\r' => Vector('\n' -> index)
      case '\t' => Vector.fill(4)(' ' -> index)
      case char => Vector(char -> index)
    }}.toVector
    val breaks = normalized.indices.filter(index => normalized(index)._1 == '\n').toVector
    val lines = (0 +: breaks.map(_ + 1)).zip(breaks.map(_ + 1) :+ normalized.size)
      .map((start, end) => normalized.slice(start, end))
    val indent = lines.filter(_.exists((char, _) => !char.isWhitespace))
      .map(_.takeWhile(_._1 == ' ').size).minOption.getOrElse(0)
    val dedented = lines.flatMap(line => line.drop(indent.min(line.takeWhile(_._1 != '\n').size)))
    val trimmed = dedented.dropWhile(_._1 <= ' ').reverse.dropWhile(_._1 <= ' ').reverse
    val end = trimmed.lastOption.fold(0)(_._2 + 1)
    PythonSourceText(trimmed.map(_._1).mkString + "\n", trimmed.map(_._2) :+ end, end)
  }
}
