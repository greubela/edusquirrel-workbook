package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.user.User
import upickle.default.*

sealed trait FeedbackEntity derives ReadWriter {
}

object FeedbackEntity {
  case class HumanEntity(user: User) extends FeedbackEntity derives upickle.default.ReadWriter

  case object LlmEntity extends FeedbackEntity

  case object TestEntity extends FeedbackEntity

  case object SystemEntity extends FeedbackEntity

}