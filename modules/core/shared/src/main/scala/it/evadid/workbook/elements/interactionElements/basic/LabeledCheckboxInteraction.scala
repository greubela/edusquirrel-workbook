package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SingleContentElementFactory

case class LabeledCheckboxInteraction(
                                       override val elementId: String,
                                       checkboxLabel: LanguageMapContentId
                                     ) extends WorkbookInteractionElement[Boolean] {

  override val associatedFactory: WorkbookElementFactory[LabeledCheckboxInteraction] = LabeledCheckboxInteraction.factory

  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: Boolean = false

  override val serializerInteractionContent: Serializer[Boolean] = Serializer.booleanIO


}

object LabeledCheckboxInteraction {
  val factory: SingleContentElementFactory[LabeledCheckboxInteraction] = new SingleContentElementFactory[LabeledCheckboxInteraction] {

    override def readContent(infoElement: LabeledCheckboxInteraction): LanguageMapContentId = infoElement.checkboxLabel

    override def finishDeserialization(elementId: String, content: LanguageMapContentId): LabeledCheckboxInteraction = LabeledCheckboxInteraction(elementId, content)
  }

}
