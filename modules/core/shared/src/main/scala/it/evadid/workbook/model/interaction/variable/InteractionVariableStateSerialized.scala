package it.evadid.workbook.model.interaction.variable

import it.evadid.core.util.io.{Serializer, TypeConverter}
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.model.interaction.sync.UpdateImportance

import java.time.LocalDateTime

case class InteractionVariableStateSerialized(serializedValue: String, updateImportance: UpdateImportance, timestamp: LocalDateTime) {
  def deserialize[T](serializer: Serializer[T]): InteractionVariableState[T] = InteractionVariableState(serializer, this)
}

object InteractionVariableStateSerialized {

  def converter[T](valueSerializer: Serializer[T]): TypeConverter[InteractionVariableStateSerialized, InteractionVariableState[T]] =
    new TypeConverter[InteractionVariableStateSerialized, InteractionVariableState[T]]() {
      override def convertToO(in: InteractionVariableStateSerialized): InteractionVariableState[T] = in.deserialize(valueSerializer)

      override def convertToI(in: InteractionVariableState[T]): InteractionVariableStateSerialized = in.serialized(valueSerializer)
    }


}
