package it.evadid.vm.conversion

import it.evadid.vm.code.errors.{ProgrammingConversionError, ProgrammingConversionException}

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.util.logging.LoggingLevel

/** Conversion diagnostics are values, independent of the editor displaying them. */
case class ConversionInfo(warningMsg: String, warningLevel: LoggingLevel, leadsToLossOfFunctionality: Boolean, sourceError: Option[ProgrammingConversionError] = None)
case class ConversionResult(originalState: ProgrammingState, finalState: Option[ProgrammingState], conversionInfo: Seq[ConversionInfo]) {
  def hasLostFunctionality: Boolean = conversionInfo.exists(_.leadsToLossOfFunctionality)
}

object ConversionResult {
  def failed(source: ProgrammingState, error: Throwable): ConversionResult = ConversionResult(source, None, Seq(
    ConversionInfo(Option(error.getMessage).getOrElse(error.getClass.getSimpleName), LoggingLevel.ERROR, true,
      error match { case failure: ProgrammingConversionException => Some(failure.error); case _ => None })))

  private[evadid] def converted[A](result: Either[String, A]): A =
    result.fold(message => throw ProgrammingConversionException.unsupported(message), identity)

}
