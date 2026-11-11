package it.evadid.vm.conversion

import it.evadid.vm.code.errors.{ProgrammingConversionException, ProgrammingConversionLocation}

private[evadid] object ConversionResult {
  def converted[A](result: Either[String, A]): A =
    result.fold(message => throw ProgrammingConversionException.unsupported(message), identity)

  def rejectAt(message: String, source: String, start: Option[Int]): Nothing = {
    val position = start.map(ProgrammingConversionLocation.lineColumn(source, _))
    throw ProgrammingConversionException.unparsable(message, position.map(_._1), position.map(_._2))
  }
}
