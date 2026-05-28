package datastructures.core.chat

import datastructures.core.chat.MessengerModel.{BasicPerson, Message, SenderRole}
import datastructures.core.language.LanguageMap
import munit.FunSuite

class MessengerModelSpec extends FunSuite:

  private val alice = BasicPerson(LanguageMap.universalMap("Alice"))
  private val bob = BasicPerson(LanguageMap.universalMap("Bob"))

  private def modelWithMixedTimestamps(): MessengerModel =
    MessengerModel(
      List(
        Message("later", "25", alice, SenderRole.USER),
        Message("invalid", "not-a-number", bob, SenderRole.TEACHER),
        Message("early", "5", alice, SenderRole.USER),
        Message("also-invalid", "5x", bob, SenderRole.TEACHER)
      )
    )

  test("orderedMessages sorts numeric timestamps and treats invalid timestamps as 0") {
    val ordered = modelWithMixedTimestamps().orderedMessages

    assertEquals(
      ordered.map(message => (message.text, message.timestampEpochMillis)),
      List(
        ("invalid", "not-a-number"),
        ("also-invalid", "5x"),
        ("early", "5"),
        ("later", "25")
      )
    )
  }

  test("addMessage(message) appends after existing orderedMessages") {
    val initial = modelWithMixedTimestamps()
    val newMessage = Message("new-tail", "12", alice, SenderRole.USER)

    val updated = initial.addMessage(newMessage)

    assertEquals(updated.messages.init.map(_.text), initial.orderedMessages.map(_.text))
    assertEquals(updated.messages.last, newMessage)
  }

  test("toJson and fromJson round-trip preserves text, SenderRole, and BasicPerson author type") {
    val original = MessengerModel(
      List(
        Message("hello-user", "100", alice, SenderRole.USER),
        Message("hello-teacher", "200", bob, SenderRole.TEACHER)
      )
    )

    val roundTripped = MessengerModel.fromJson(original.toJson)

    assertEquals(roundTripped.messages.map(_.text), original.messages.map(_.text))
    assertEquals(roundTripped.messages.map(_.senderRole), original.messages.map(_.senderRole))
    roundTripped.messages.foreach { message =>
      assert(message.author.isInstanceOf[BasicPerson], s"Expected BasicPerson, got: ${message.author.getClass.getSimpleName}")
    }
  }

  test("addMessage(text, author, role, timestamp) keeps provided timestamp in serialized form") {
    val timestamp = 1700000123456L
    val updated = MessengerModel(List()).addMessage(
      text = "explicit-time",
      author = alice,
      senderRole = SenderRole.TEACHER,
      timestampEpochMillis = timestamp
    )

    val parsed = MessengerModel.fromJson(updated.toJson)
    assertEquals(parsed.messages.head.timestampEpochMillis, timestamp.toString)
    assertEquals(parsed.messages.head.senderRole, SenderRole.TEACHER)
  }
