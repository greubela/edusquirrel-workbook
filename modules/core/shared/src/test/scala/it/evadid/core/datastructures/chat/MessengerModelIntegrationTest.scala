package it.evadid.core.datastructures.chat

import munit.FunSuite

import java.time.LocalDateTime

class MessengerModelIntegrationTest extends FunSuite {
  test("serialization roundtrip preserves message data") {
    val user = Person("Teacher", "42", SenderRole.TEACHER, None)
    val model = MessengerModel(List(Message("hello", user, LocalDateTime.parse("2026-01-01T12:00:00"))))
    val restored = MessengerModel.fromJson(model.toJson)

    assertEquals(restored.messages.head.text, "hello")
    assertEquals(restored.messages.head.author.role, SenderRole.TEACHER)
  }
}
