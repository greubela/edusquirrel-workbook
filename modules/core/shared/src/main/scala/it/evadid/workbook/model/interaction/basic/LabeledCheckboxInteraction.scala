package it.evadid.workbook.model.interaction.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.variable.InteractionVariable

case class LabeledCheckboxInteraction(override val id: String, langIdCheckboxLabel: LanguageMapContentId) extends WorkbookInteraction[Boolean] {

  override def defaultValue: Boolean = false

  override def serializer: Serializer[Boolean] = Serializer.booleanIO

  override def interactionVariable: InteractionVariable[Boolean] = InteractionVariable[Boolean](this)

}
