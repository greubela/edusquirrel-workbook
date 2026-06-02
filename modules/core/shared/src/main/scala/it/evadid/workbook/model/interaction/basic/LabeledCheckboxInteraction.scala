package it.evadid.workbook.model.interaction.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.variable.InteractionVariable

case class LabeledCheckboxInteraction(override val id: String, checkboxLabel: LanguageMapContentId) extends WorkbookInteraction[Boolean] {

  override val defaultValue: Boolean = false

  override val serializer: Serializer[Boolean] = Serializer.booleanIO
  
}
