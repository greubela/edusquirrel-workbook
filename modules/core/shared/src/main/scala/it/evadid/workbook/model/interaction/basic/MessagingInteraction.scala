package it.evadid.workbook.model.interaction.basic

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.basic.MessagingInteraction.{MessengerModelScaffolding, mmsSer}
import it.evadid.workbook.model.interaction.sync.UpdateImportance.MAJOR
import it.evadid.workbook.model.interaction.variable.InteractionVariable
import upickle.ReadWriter
import upickle.default.{read, readwriter, write}
import upickle.default.macroRW

case class MessagingInteraction(override val id: String) extends WorkbookInteraction[MessengerModelScaffolding] {

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