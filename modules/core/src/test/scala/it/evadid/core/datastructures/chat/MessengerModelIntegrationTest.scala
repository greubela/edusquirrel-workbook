package it.evadid.core.datastructures.chat

import it.evadid.core.datastructures.language.LanguageMap
import munit.FunSuite

class MessengerModelIntegrationTest extends FunSuite {
  test("serialization roundtrip preserves message data") {
    val user = MessengerModel.BasicPerson(LanguageMap.universalMap("User"))
    val model = MessengerModel(List(MessengerModel.Message("hello", "42", user, MessengerModel.SenderRole.TEACHER)))
    val restored = MessengerModel.fromJson(model.toJson)

    assertEquals(restored.messages.head.text, "hello")
    assertEquals(restored.messages.head.senderRole, MessengerModel.SenderRole.TEACHER)
  }
}
