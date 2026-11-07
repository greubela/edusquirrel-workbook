package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.user.User
import upickle.default.*

sealed trait FeedbackEntity derives ReadWriter {
}

object FeedbackEntity {
  case class HumanEntity(user: User) extends FeedbackEntity derives upickle.default.ReadWriter

  case class LlmEntity() extends FeedbackEntity derives upickle.default.ReadWriter

  case class TestEntity() extends FeedbackEntity derives upickle.default.ReadWriter

  case class SystemEntity() extends FeedbackEntity derives ReadWriter

}