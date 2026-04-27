package workbook.model.interaction

import it.evadid.core.util.io.Serializer
import workbook.model.interaction.InteractionVariableState.SerializedExerciseVariableState
import workbook.model.interaction.history.UpdateImportance

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*

case class InteractionVariableState[T](value: T, epochTimestampMillis: Long, updateImportance: UpdateImportance) {

  def serialized(io: Serializer[T]): SerializedExerciseVariableState = SerializedExerciseVariableState(io.serialize(value), epochTimestampMillis, updateImportance)

}

object InteractionVariableState {
  case class SerializedExerciseVariableState(serializedValue: String, epochTimestampMillis: Long, updateImportance: UpdateImportance)

  def apply[T](io: Serializer[T], serializedState: SerializedExerciseVariableState): InteractionVariableState[T] = 
    InteractionVariableState(io.deserialize(serializedState.serializedValue), serializedState.epochTimestampMillis, serializedState.updateImportance)
  
}

