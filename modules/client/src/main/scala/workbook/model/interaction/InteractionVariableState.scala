package workbook.model.interaction

import it.evadid.core.util.io.Serializer
import workbook.model.interaction.InteractionVariableState.SerializedExerciseVariableState
import workbook.model.interaction.history.UpdateImportance
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.util.io.serializer.DefaultSerializer

import java.time.LocalDateTime

case class InteractionVariableState[T](value: T, updateImportance: UpdateImportance, timestamp: LocalDateTime) {

  def serialized(io: Serializer[T]): SerializedExerciseVariableState =
    SerializedExerciseVariableState(io.serialize(value), updateImportance, DefaultSerializer.serializerLocalDateTimeString.serialize(timestamp))

}

object InteractionVariableState {
  case class SerializedExerciseVariableState(serializedValue: String, updateImportance: UpdateImportance, timestamp: String)

  def apply[T](io: Serializer[T], serializedState: SerializedExerciseVariableState): InteractionVariableState[T] =
    InteractionVariableState(io.deserialize(serializedState.serializedValue), serializedState.updateImportance, DefaultSerializer.serializerLocalDateTimeString.deserialize(serializedState.timestamp))

}

