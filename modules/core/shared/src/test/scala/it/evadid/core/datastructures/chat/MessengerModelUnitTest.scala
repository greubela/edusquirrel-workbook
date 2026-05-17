package it.evadid.core.datastructures.chat

import munit.FunSuite

import java.time.LocalDateTime

class MessengerModelUnitTest extends FunSuite {
  test("orderedMessages sorts by timestamp") {
    val user = Person("User", "u1", SenderRole.USER, None)
    val model = MessengerModel(List(
      Message("later", user, LocalDateTime.parse("2026-01-02T10:00:00")),
      Message("earlier", user, LocalDateTime.parse("2026-01-01T10:00:00"))
    ))

    assertEquals(model.orderedMessages.map(_.text), List("earlier", "later"))
  }
}
