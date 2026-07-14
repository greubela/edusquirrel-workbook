package it.evadid.workbook.interaction.variable

import it.evadid.core.util.InfoUtil
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.core.util.io.{Serializer, TypeConverter}

case class InteractionVariableHistorySerialized(states: Set[InteractionVariableStateSerialized]) {

  //def lastState: InteractionVariableStateSerialized = states.maxBy(_.timestamp)

  def lastStateOption: Option[InteractionVariableStateSerialized] = states.maxByOption(_.timestamp)

  def tryDeserialize[T](valueSerializer: Serializer[T]): (InteractionVariableHistory[T], InteractionVariableHistorySerialized) = {
    val converter: TypeConverter[InteractionVariableStateSerialized, InteractionVariableState[T]] = InteractionVariableStateSerialized.converter(valueSerializer)
    val converterResult: TypeConverter.ConverterResult[InteractionVariableStateSerialized, InteractionVariableState[T]] = converter.tryConvertAllToO(states)
    val success = InteractionVariableHistory[T](converterResult.outputAfterOpteration)
    val failed = InteractionVariableHistorySerialized(converterResult.inputAfterOperation)
    (success, failed)
  }

  def deserializeIgnoreErrors[T](valueSerializer: Serializer[T]): InteractionVariableHistory[T] = {
    tryDeserialize(valueSerializer)._1
  }

  override val toString: String = s"InteractionVariableHistoryTyped(${states.size} states, latest: ${InfoUtil.datetimeFormattedForLog(lastStateOption.map(_.timestamp))})"

}

object InteractionVariableHistorySerialized {

  lazy val empty: InteractionVariableHistorySerialized = InteractionVariableHistorySerialized(Set())

}
