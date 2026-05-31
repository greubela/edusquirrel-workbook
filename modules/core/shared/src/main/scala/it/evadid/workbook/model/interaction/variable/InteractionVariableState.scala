package it.evadid.workbook.model.interaction.variable

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.sync.UpdateImportance.*

import java.time.LocalDateTime

case class InteractionVariableState[T](value: T, updateImportance: UpdateImportance, timestamp: LocalDateTime) {

  def serialized(io: Serializer[T]): SerializedExerciseVariableState =
    SerializedExerciseVariableState(io.serialize(value), updateImportance, DefaultSerializer.serializerLocalDateTimeString.serialize(timestamp))

}

object InteractionVariableState {

  def apply[T](io: Serializer[T], serializedState: SerializedExerciseVariableState): InteractionVariableState[T] =
    InteractionVariableState(io.deserialize(serializedState.serializedValue), serializedState.updateImportance, DefaultSerializer.serializerLocalDateTimeString.deserialize(serializedState.timestamp))

}

