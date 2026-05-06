package it.evadid.core.datastructures.chat

import it.evadid.core.datastructures.language.LanguageMap
import munit.FunSuite

class MessengerModelUnitTest extends FunSuite {
  test("orderedMessages sorts by timestamp") {
    val user = MessengerModel.BasicPerson(LanguageMap.universalMap("User"))
    val model = MessengerModel(List(
      MessengerModel.Message("later", "2", user),
      MessengerModel.Message("earlier", "1", user)
    ))

    assertEquals(model.orderedMessages.map(_.text), List("earlier", "later"))
  }
}
