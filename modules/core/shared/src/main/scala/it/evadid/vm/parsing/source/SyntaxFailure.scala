package it.evadid.vm.parsing.source

import it.evadid.vm.code.errors.ProgrammingConversionException
import upickle.default.ReadWriter

/** Why step 2 could not read a source: the grammar's message and the 1-based position it stopped at. */
final case class SyntaxFailure(message: String, offset: Int, line: Int, column: Int) derives ReadWriter {
  /** The same failure as an unparsable-source conversion error at its position. */
  def unparsable(summary: String = message): ProgrammingConversionException =
    ProgrammingConversionException.unparsable(summary, Some(line), Some(column))
}
