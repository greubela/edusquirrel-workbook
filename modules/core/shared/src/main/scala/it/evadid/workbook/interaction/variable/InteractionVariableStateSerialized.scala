package it.evadid.workbook.interaction.variable

import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.workbook.interaction.sync.UpdateImportance

import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given

import java.time.LocalDateTime

case class InteractionVariableStateSerialized(serializedValue: String, updateImportance: UpdateImportance, timestamp: LocalDateTime) derives ReadWriter {
  def deserialize[T](serializer: Serializer[T]): InteractionVariableState[T] = InteractionVariableState(serializer, this)
}

object InteractionVariableStateSerialized {
  def converter[T](valueSerializer: Serializer[T]): TypeConverter[InteractionVariableStateSerialized, InteractionVariableState[T]] =
    new TypeConverter[InteractionVariableStateSerialized, InteractionVariableState[T]]() {
      override def convertToO(in: InteractionVariableStateSerialized): InteractionVariableState[T] = in.deserialize(valueSerializer)

      override def convertToI(in: InteractionVariableState[T]): InteractionVariableStateSerialized = in.serialized(valueSerializer)
    }


}
