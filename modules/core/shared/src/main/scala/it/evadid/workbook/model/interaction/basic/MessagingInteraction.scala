package it.evadid.workbook.model.interaction.basic

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.variable.InteractionVariable

case class MessagingInteraction(override val id: String, override val defaultValue: MessengerModel) extends WorkbookInteraction[MessengerModel]{

  override val serializer: Serializer[MessengerModel] = Serializer.messengerIo


}
