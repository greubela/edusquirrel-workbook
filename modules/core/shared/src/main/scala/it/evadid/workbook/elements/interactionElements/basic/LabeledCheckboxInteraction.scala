package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}

case class LabeledCheckboxInteraction(override val id: String, checkboxLabel: LanguageMapContentId) extends WorkbookInteractionElement[Boolean] {

  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: Boolean = false

  override val serializer: Serializer[Boolean] = Serializer.booleanIO

}
