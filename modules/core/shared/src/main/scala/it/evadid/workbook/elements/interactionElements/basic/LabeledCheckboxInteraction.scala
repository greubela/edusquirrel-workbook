package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

case class LabeledCheckboxInteraction(
                                       override val elementId: String,
                                       checkboxLabel: LanguageMapContentId
                                     ) extends WorkbookInteractionElement[Boolean] {
  override val associatedFactory = LabeledCheckboxInteraction.factory

  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: Boolean = false

  override val serializerInteractionContent: Serializer[Boolean] = Serializer.booleanIO



}

object LabeledCheckboxInteraction {
  val factory = it.evadid.workbook.jsonFactory.WorkbookElementFactory.simple[LabeledCheckboxInteraction](e => WorkbookElementSerializable(e.elementId, classOf[LabeledCheckboxInteraction].getSimpleName, Map()).withContentIdAdded("content", e.checkboxLabel), f => LabeledCheckboxInteraction(f.elementId, f.getElementAsContentId("content")))


}
