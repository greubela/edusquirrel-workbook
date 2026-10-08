package it.evadid.core.datastructures.chat

import it.evadid.core.datastructures.language.AppLanguage.German
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

class ChatPackageSpec extends FunSuite {
  private val time = LocalDateTime.of(2025, 1, 1, 12, 0)
  private val author = Person("Ada", "id", SenderRole.USER, Some(" AD "))

  test("person conversions preserve identity, roles and normalized abbreviations") {
    assertEquals(author.abbreviation, Some("AD"))
    assertEquals(author.toBasic.personId, "id")
    assertEquals(author.toSerializable.abbreviation, Some("AD"))
    assertEquals(Person.SerializablePerson("Ada", "id", SenderRole.USER, " ").abbreviation, None)
    assertEquals(Person.SerializablePerson("Ada", "id", SenderRole.USER, null).abbreviation, None)
    assertEquals(Person("Ada", "id", SenderRole.USER).abbreviation, None)
    assertEquals(SenderRole.allRoles.map(_.llmName), List("user", "user", "assistant", "user"))
  }
  test("adding backdated messages keeps stored conversation order and the original model") {
    val original = MessengerModel(List(Message("later", author, time.plusSeconds(2))))
    val updated = original.addMessage("earlier", author, time)
    assertEquals(updated.messages.map(_.text), List("earlier", "later"))
    assertEquals(original.messages.map(_.text), List("later"))
    assertEquals(updated.addMessage(Message("middle", author, time.plusSeconds(1))).messages.map(_.text), List("earlier", "middle", "later"))
  }
  test("scaffolding omits whitespace-only answers but retains nonblank answers and all hints") {
    val empty = MessengerModel.getScaffoldingInitMessage(author, "exercise", " \t\n ", List("hint1", "hint2"), German)
    assert(!empty.messages.exists(_.author.personId == author.personId))
    assert(empty.messages.exists(_.text == "@assistant: hint1"))
    assert(empty.messages.exists(_.text == "@assistant: hint2"))
    assert(empty.messages.last.text.contains(German.name))
    val filled = MessengerModel.getScaffoldingInitMessage(author, "exercise", " answer ", Nil, German)
    assertEquals(filled.messages.last.text, " answer ")
    assertEquals(filled.messages.last.author, author)
    val escaped = MessengerModel.getScaffoldingInitMessage(author, "exercise", "\\s", Nil, German)
    assertEquals(escaped.messages.last.text, "\\s")
  }
  test("default message codecs use the existing application wire format") {
    val message = Message("\"Grüße\"\n", author, time)
    val model = MessengerModel(List(message))
    assertEquals(write(model), model.toJson)
    val restored = read[MessengerModel](write(model))
    assertEquals(restored.messages.head.text, message.text)
    assertEquals(restored.messages.head.timestamp, time)
    assertEquals(restored.messages.head.author.toBasic, author.toBasic)
    assertEquals(write(restored), model.toJson)
    val single = read[Message](write(message))
    assertEquals(single.author.toBasic, author.toBasic)
    assertEquals(single.timestamp, time)
  }
}
