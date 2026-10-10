package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.user.User
import upickle.default.*

sealed trait FeedbackEntity derives ReadWriter {
}

object FeedbackEntity {
  case class HumanEntity(user: User) extends FeedbackEntity

  case class AiEntity(fullChat: MessengerModel) extends FeedbackEntity
}