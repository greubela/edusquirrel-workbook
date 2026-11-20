package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.command.SerializedException
import munit.FunSuite
import upickle.default.*

import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}

class DefaultSerializerSpec extends FunSuite {
  test("Try values keep successful payloads and the message of failures") {
    assertEquals(read[Try[Int]](write[Try[Int]](Success(4))), Success(4))
    val failure = read[Try[Int]](write[Try[Int]](Failure(IllegalStateException("broken"))))
    assertEquals(failure.failed.map(_.getMessage), Success("broken"))
    assertEquals(read[Try[Int]]("{}").failed.map(_.getMessage), Success("Unknown failure"))
  }

  test("local date-times use their ISO text") {
    val time = LocalDateTime.of(2026, 11, 20, 7, 5, 30)
    assertEquals(write(time), "\"2026-11-20T07:05:30\"")
    assertEquals(read[LocalDateTime](write(time)), time)
  }

  test("throwables are stored as serialized exceptions with their cause chain") {
    val original = IllegalArgumentException("outer", IllegalStateException("inner"))
    val restored = DefaultSerializer.serializerException.deserialize(DefaultSerializer.serializerException.serialize(original))
    assertEquals(restored.getMessage, "outer")
    restored match {
      case serialized: SerializedException => assertEquals(serialized.cause.map(_.getMessage), Some("inner"))
      case other => fail(s"Unexpected $other")
    }
  }

  test("string maps are JSON objects") {
    val map = Map("a" -> "1", "b" -> "x\"y")
    assertEquals(ujson.read(DefaultSerializer.serializerJsonStringMap.serialize(map)), ujson.Obj("a" -> "1", "b" -> "x\"y"))
    assertEquals(DefaultSerializer.serializerJsonStringMap.deserialize("""{"k":"v"}"""), Map("k" -> "v"))
  }
}
