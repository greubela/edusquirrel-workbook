package it.evadid.core.datastructures.chat

import munit.FunSuite

import java.time.Instant

class MessengerModelUnitTest extends FunSuite {
  test("orderedMessages sorts by timestamp") {
    val user = ChatParticipant("User", "u1", SenderRole.USER, None)
    val model = MessengerModel(List(
      Message("later", user, java.time.Instant.parse("2026-01-02T10:00:00Z")),
      Message("earlier", user, java.time.Instant.parse("2026-01-01T10:00:00Z"))
    ))

    assertEquals(model.orderedMessages.map(_.text), List("earlier", "later"))
  }
}
