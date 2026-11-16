package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction.*
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.NoContentElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import it.evadid.core.util.io.serializer.{InteractionFormat, DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import upickle.ReadWriter
case class MessagingInteraction(override val elementId: String) extends WorkbookInteractionElement[MessengerModelScaffolding] {

  override val associatedFactory: NoContentElementFactory[MessagingInteraction] = MessagingInteraction.factory

  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: MessengerModelScaffolding = MessengerModelScaffolding(MessengerModel.empty)

  override val serializerInteractionContent: Serializer[MessengerModelScaffolding] = Serializer.fromUpickleJson(MessengerModelScaffolding.derived$ReadWriter)
  override def format: InteractionFormat[MessengerModelScaffolding, Unit, Unit] = MessagingInteraction.format

}

object MessagingInteraction extends InteractionFormat[MessengerModelScaffolding, Unit, Unit] {

  val factory: NoContentElementFactory[MessagingInteraction] = new NoContentElementFactory[MessagingInteraction]() {

    override def callConstructor(elementId: String): MessagingInteraction = MessagingInteraction(elementId)
  }

  case class MessengerModelScaffolding(messengerModel: MessengerModel) derives ReadWriter {

  }

  override def stateSerializer: Serializer[MessengerModelScaffolding] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[MessengerModelScaffolding, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[MessengerModelScaffolding, Unit, Unit]] = Serializer.fromImplicitRW

}
