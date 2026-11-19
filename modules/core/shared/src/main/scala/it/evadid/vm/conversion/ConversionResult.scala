package it.evadid.vm.conversion

import it.evadid.vm.code.errors.{ProgrammingConversionException, ProgrammingConversionLocation}

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.util.logging.LoggingLevel

/** Conversion diagnostics are values, independent of the editor displaying them. */
case class ConversionInfo(warningMsg: String, warningLevel: LoggingLevel, leadsToLossOfFunctionality: Boolean)
case class ConversionResult(originalState: ProgrammingState, finalState: Option[ProgrammingState], conversionInfo: Seq[ConversionInfo]) {
  def hasLostFunctionality: Boolean = conversionInfo.exists(_.leadsToLossOfFunctionality)
}

object ConversionResult {
  private[evadid] def converted[A](result: Either[String, A]): A =
    result.fold(message => throw ProgrammingConversionException.unsupported(message), identity)

  private[evadid] def rejectAt(message: String, source: String, start: Option[Int]): Nothing = {
    val position = start.map(ProgrammingConversionLocation.lineColumn(source, _))
    throw ProgrammingConversionException.unparsable(message, position.map(_._1), position.map(_._2))
  }
}
