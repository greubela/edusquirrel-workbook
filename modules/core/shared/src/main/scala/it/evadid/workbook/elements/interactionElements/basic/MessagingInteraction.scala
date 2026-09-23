package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction.{MessengerModelScaffolding, mmsSer}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import upickle.ReadWriter
import upickle.default.macroRW

case class MessagingInteraction(override val elementId: String) extends WorkbookInteractionElement[MessengerModelScaffolding] {


  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: MessengerModelScaffolding = MessengerModelScaffolding(MessengerModel.empty)

  override val serializerInteractionContent: Serializer[MessengerModelScaffolding] = mmsSer

  override def toSerializableType: WorkbookElementFactory = toFactoryBase
}

object MessagingInteraction {

  def fromFactory(factory: WorkbookElementFactory): MessagingInteraction = MessagingInteraction(factory.elementId)

  case class MessengerModelScaffolding(messengerModel: MessengerModel) {

  }

  private given mmRw: ReadWriter[MessengerModel] = Serializer.messengerIo.uPickleReadWrite

  private val mmsRW: ReadWriter[MessengerModelScaffolding] = macroRW
  private val mmsSer: Serializer[MessengerModelScaffolding] = Serializer.fromUpickleJson(mmsRW)


}