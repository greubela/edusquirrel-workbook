package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.workbook.abstractions.{InteractionFormat, *}
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookElement, WorkbookInteractionElement, *}
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction.*
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.NoContentElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.InteractionFormat.MessagingInteractionFormat
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import upickle.ReadWriter
case class MessagingInteraction(override val elementId: String) extends WorkbookInteractionElement[MessagingInteractionFormat.type] {

  override val associatedFactory: NoContentElementFactory[MessagingInteraction] = MessagingInteraction.factory

  lazy val childrenOfThisElement: Seq[WorkbookElement] = List()

  override val defaultValue: MessengerModelScaffolding = MessengerModelScaffolding(MessengerModel.empty)

}

object MessagingInteraction  {


  val factory: NoContentElementFactory[MessagingInteraction] = new NoContentElementFactory[MessagingInteraction]() {

    override def callConstructor(elementId: String): MessagingInteraction = MessagingInteraction(elementId)
  }

  case class MessengerModelScaffolding(messengerModel: MessengerModel) derives ReadWriter {

  }
}
