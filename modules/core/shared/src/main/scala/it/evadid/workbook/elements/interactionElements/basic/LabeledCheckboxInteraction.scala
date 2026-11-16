package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement, WorkbookInteractionElementFull}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SingleContentElementFactory

import it.evadid.core.util.io.serializer.{InteractionFormat, DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
case class LabeledCheckboxInteraction(
                                       override val elementId: String,
                                       checkboxLabel: LanguageMapContentId
                                     ) extends WorkbookInteractionElement[Boolean] {

  override def format: InteractionFormat[Boolean, Unit, Unit] = LabeledCheckboxInteraction

  override val associatedFactory: WorkbookElementFactory[LabeledCheckboxInteraction] = LabeledCheckboxInteraction.factory

  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: Boolean = false


}

object LabeledCheckboxInteraction extends InteractionFormat[Boolean, Unit, Unit] {
  override def stateSerializer: Serializer[Boolean] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[Boolean, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[Boolean, Unit, Unit]] = Serializer.fromImplicitRW

  val factory: SingleContentElementFactory[LabeledCheckboxInteraction] = new SingleContentElementFactory[LabeledCheckboxInteraction] {

    override def readContent(infoElement: LabeledCheckboxInteraction): LanguageMapContentId = infoElement.checkboxLabel

    override def finishDeserialization(elementId: String, content: LanguageMapContentId): LabeledCheckboxInteraction = LabeledCheckboxInteraction(elementId, content)
  }
}
