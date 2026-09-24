package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction.{MessengerModelScaffolding, mmsSer}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.NoContentElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.ReadWriter
import upickle.default.macroRW

case class MessagingInteraction(override val elementId: String) extends WorkbookInteractionElement[MessengerModelScaffolding] {
  override val associatedFactory: NoContentElementFactory[MessagingInteraction] = MessagingInteraction.factory

  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: MessengerModelScaffolding = MessengerModelScaffolding(MessengerModel.empty)

  override val serializerInteractionContent: Serializer[MessengerModelScaffolding] = mmsSer

}

object MessagingInteraction {

  val factory: NoContentElementFactory[MessagingInteraction] = new NoContentElementFactory[MessagingInteraction]() {

    override def callConstructor(elementId: String): MessagingInteraction = MessagingInteraction(elementId)
  }

  case class MessengerModelScaffolding(messengerModel: MessengerModel) {

  }

  private given mmRw: ReadWriter[MessengerModel] = Serializer.messengerIo.uPickleReadWrite

  private val mmsRW: ReadWriter[MessengerModelScaffolding] = macroRW
  private val mmsSer: Serializer[MessengerModelScaffolding] = Serializer.fromUpickleJson(mmsRW)


}