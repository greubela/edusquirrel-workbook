package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookElement, WorkbookInteractionElement, *}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SingleContentElementFactory
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.InteractionFormat.LabeledCheckboxInteractionFormat
case class LabeledCheckboxInteraction(
                                       override val elementId: String,
                                       checkboxLabel: LanguageMapContentId
                                     ) extends WorkbookInteractionElement[LabeledCheckboxInteractionFormat.type ] {


  override val associatedFactory: WorkbookElementFactory[LabeledCheckboxInteraction] = LabeledCheckboxInteraction.factory

  lazy val childrenOfThisElement: Seq[WorkbookElement] = List()

  override val defaultValue: Boolean = false


}

object LabeledCheckboxInteraction {

  val factory: SingleContentElementFactory[LabeledCheckboxInteraction] = new SingleContentElementFactory[LabeledCheckboxInteraction] {

    override def readContent(infoElement: LabeledCheckboxInteraction): LanguageMapContentId = infoElement.checkboxLabel

    override def finishDeserialization(elementId: String, content: LanguageMapContentId): LabeledCheckboxInteraction = LabeledCheckboxInteraction(elementId, content)
  }
}
