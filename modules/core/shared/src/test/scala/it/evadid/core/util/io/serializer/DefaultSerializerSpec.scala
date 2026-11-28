package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.command.SerializedException
import munit.FunSuite
import upickle.default.*

import java.time.Instant

class DefaultSerializerSpec extends FunSuite {

  test("instants are written as UTC text with exactly nine fractional digits") {
    val time = Instant.parse("2026-11-20T07:05:30Z")
    assertEquals(write(time), "\"2026-11-20T07:05:30.000000000Z\"")
    assertEquals(DefaultSerializer.instantText(time.plusMillis(5).plusNanos(7)), "2026-11-20T07:05:30.005000007Z")
    for (instant <- List(time, time.plusNanos(1), Instant.EPOCH, Instant.MIN, Instant.MAX))
      assertEquals(read[Instant](write(instant)), instant)
    val ordered = List(Instant.EPOCH, time, time.plusNanos(1), time.plusSeconds(1))
    assertEquals(ordered.map(DefaultSerializer.instantText).sorted, ordered.map(DefaultSerializer.instantText), "four-digit years sort as text")
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
