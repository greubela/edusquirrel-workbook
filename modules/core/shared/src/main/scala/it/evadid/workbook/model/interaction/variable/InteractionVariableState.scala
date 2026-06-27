package it.evadid.workbook.model.interaction.variable

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.sync.UpdateImportance.*

import java.time.LocalDateTime

case class InteractionVariableState[T](value: T, updateImportance: UpdateImportance, timestamp: LocalDateTime) {

  def serialized(io: Serializer[T]): InteractionVariableStateSerialized =
    InteractionVariableStateSerialized(io.serialize(value), updateImportance, timestamp)

}

object InteractionVariableState {

  def apply[T](io: Serializer[T], serializedState: InteractionVariableStateSerialized): InteractionVariableState[T] =
    InteractionVariableState(io.deserialize(serializedState.serializedValue), serializedState.updateImportance, serializedState.timestamp)

  case class DesignatedInteractionState[T](value: T, timestamp: LocalDateTime)

  case class InteractionVariableStateChanged[T](lastState: InteractionVariableState[T], newState: DesignatedInteractionState[T])

}

