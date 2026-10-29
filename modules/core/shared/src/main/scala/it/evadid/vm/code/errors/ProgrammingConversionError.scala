package it.evadid.vm.code.errors

/** Why an attempted language conversion failed (parse or translate). Unknown `toLanguage` targets use [[UnsupportedOperationException]] instead. */
enum ProgrammingConversionKind {
  case Unparsable
  case UnsupportedConstruct
}

/**
 * Conversion failure metadata. `line` and `column` are 1-based when a parser or
 * diagnostic reports them.
 */
final case class ProgrammingConversionError(
    kind: ProgrammingConversionKind,
    line: Option[Int] = None,
    column: Option[Int] = None
)

/** Conversion failure that remains an [[IllegalArgumentException]] for existing callers. */
final class ProgrammingConversionException(
    message: String,
    val error: ProgrammingConversionError
) extends IllegalArgumentException(message)

object ProgrammingConversionException {
  def unsupported(message: String, line: Option[Int] = None, column: Option[Int] = None): ProgrammingConversionException =
    new ProgrammingConversionException(message, ProgrammingConversionError(ProgrammingConversionKind.UnsupportedConstruct, line, column))

  def unparsable(message: String, line: Option[Int] = None, column: Option[Int] = None): ProgrammingConversionException =
    new ProgrammingConversionException(message, ProgrammingConversionError(ProgrammingConversionKind.Unparsable, line, column))
}

object ProgrammingConversionLocation {
  /** 1-based line and column for a character index in `source`. */
  def lineColumn(source: String, index: Int): (Int, Int) = {
    val bounded = math.max(0, math.min(index, source.length))
    val line = source.iterator.take(bounded).count(_ == '\n') + 1
    val break = if bounded == 0 then -1 else source.lastIndexOf('\n', bounded - 1)
    val column = if break < 0 then bounded + 1 else bounded - break
    (line, column)
  }

  /** Map an index in `trimmed` back onto `original` after leading whitespace was removed. */
  def inTrimmed(original: String, trimmed: String, index: Int): (Int, Int) = {
    val start = if trimmed.isEmpty then 0 else original.indexOf(trimmed)
    lineColumn(original, (if start < 0 then 0 else start) + index)
  }
}
