package it.evadid.workbook.interaction.variable

import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.interaction.sync.UpdateImportance
import UpdateImportance.*

import java.time.LocalDateTime

case class InteractionVariableState[T](value: T, updateImportance: UpdateImportance, timestamp: LocalDateTime) derives ReadWriter {

  def serialized(io: Serializer[T]): InteractionVariableStateSerialized =
    InteractionVariableStateSerialized(io.serialize(value), updateImportance, timestamp)

}

object InteractionVariableState {

  def apply[T](io: Serializer[T], serializedState: InteractionVariableStateSerialized): InteractionVariableState[T] =
    InteractionVariableState(io.deserialize(serializedState.serializedValue), serializedState.updateImportance, serializedState.timestamp)

  case class DesignatedInteractionState[T](value: T, timestamp: LocalDateTime) derives ReadWriter

  case class InteractionVariableStateChanged[T](lastState: InteractionVariableState[T], newState: DesignatedInteractionState[T]) derives ReadWriter

}

