package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.user.User
import upickle.default.*

sealed trait FeedbackEntity derives ReadWriter {
}

object FeedbackEntity {
  @upickle.implicits.key("HumanEntity")
  case class HumanEntity(user: User) extends FeedbackEntity derives upickle.default.ReadWriter

  @upickle.implicits.key("LlmEntity")
  case object LlmEntity extends FeedbackEntity

  @upickle.implicits.key("TestEntity")
  case object TestEntity extends FeedbackEntity

  @upickle.implicits.key("SystemEntity")
  case object SystemEntity extends FeedbackEntity

}