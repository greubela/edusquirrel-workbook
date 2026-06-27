package it.evadid.workbook.model.interaction.variable

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.core.util.io.{Serializer, TypeConverter}

case class InteractionVariableHistorySerialized(states: Set[InteractionVariableStateSerialized]) {

  override lazy val toString: String = DefaultSerializer.serializerInteractionVariableHistoryIgnoreErrors.serialize(this)

  def lastState: InteractionVariableStateSerialized = states.maxBy(_.timestamp)

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

}

object InteractionVariableHistorySerialized {

  lazy val empty: InteractionVariableHistorySerialized = InteractionVariableHistorySerialized(Set())

}
