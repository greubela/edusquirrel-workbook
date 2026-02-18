package workbook.model.interaction

import workbook.model.interaction.history.UpdateImportance


case class InteractionVariableState[T](value: T, epochTimestampMillis: Long, updateImportance: UpdateImportance)

