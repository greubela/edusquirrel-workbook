package it.evadid.workbook.model.interaction.variable

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.sync.UpdateImportance

case class InteractionVariableStateSerialized(serializedValue: String, updateImportance: UpdateImportance, timestamp: String) {
  def deserialize[T](serializer: Serializer[T]): InteractionVariableState[T] = InteractionVariableState(serializer, this)
}
