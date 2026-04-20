package workbook.model.interaction

import util.serializing.Serializer
import workbook.model.interaction.InteractionVariableState.SerializedExerciseVariableState
import workbook.model.interaction.history.UpdateImportance


case class InteractionVariableState[T](value: T, epochTimestampMillis: Long, updateImportance: UpdateImportance) {

  def serialized(io: Serializer[T]): SerializedExerciseVariableState = SerializedExerciseVariableState(io.serialize(value), epochTimestampMillis, updateImportance)

}

object InteractionVariableState {
  case class SerializedExerciseVariableState(serializedValue: String, epochTimestampMillis: Long, updateImportance: UpdateImportance)

  def apply[T](io: Serializer[T], serializedState: SerializedExerciseVariableState): InteractionVariableState[T] = 
    InteractionVariableState(io.deserialize(serializedState.serializedValue), serializedState.epochTimestampMillis, serializedState.updateImportance)
  
}

