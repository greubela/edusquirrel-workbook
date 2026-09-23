package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory

case class LabeledCheckboxInteraction(override val elementId: String, checkboxLabel: LanguageMapContentId) extends WorkbookInteractionElement[Boolean] {

  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: Boolean = false

  override val serializerInteractionContent: Serializer[Boolean] = Serializer.booleanIO

  override val toSerializableType: WorkbookElementFactory = toFactoryBase.withContentIdAdded("content", checkboxLabel)

}
