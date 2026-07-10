package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction.{MessengerModelScaffolding, mmsSer}
import upickle.ReadWriter
import upickle.default.macroRW

case class MessagingInteraction(override val id: String) extends WorkbookInteractionElement[MessengerModelScaffolding] {


  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: MessengerModelScaffolding = MessengerModelScaffolding(MessengerModel.empty)

  override val serializer: Serializer[MessengerModelScaffolding] = mmsSer

}

object MessagingInteraction {

  case class MessengerModelScaffolding(messengerModel: MessengerModel) {

  }

  private given mmRw: ReadWriter[MessengerModel] = Serializer.messengerIo.uPickleReadWrite

  private val mmsRW: ReadWriter[MessengerModelScaffolding] = macroRW
  private val mmsSer: Serializer[MessengerModelScaffolding] = Serializer.fromUpickleJson(mmsRW)


}