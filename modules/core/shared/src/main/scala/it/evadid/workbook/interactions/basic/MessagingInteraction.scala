package it.evadid.workbook.interactions.basic

import it.evadid.workbook.abstractions.{InteractionFormat, *}
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookElement, WorkbookInteractionElement, *}
import it.evadid.workbook.interactions.basic.MessagingInteraction.*
import it.evadid.workbook.serialization.WorkbookElementFactory.NoContentElementFactory
import it.evadid.workbook.serialization.WorkbookElementSerializable
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.InteractionFormat.MessagingInteractionFormat
import it.evadid.workbook.model.history.InteractionVariableHistory
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
