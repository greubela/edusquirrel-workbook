package it.evadid.workbook.model.interaction.variable

import it.evadid.workbook.model.interaction.sync.UpdateImportance


case class SerializedExerciseVariableState(serializedValue: String, updateImportance: UpdateImportance, timestamp: String)
