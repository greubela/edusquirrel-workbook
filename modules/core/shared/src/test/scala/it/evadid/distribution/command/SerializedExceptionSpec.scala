package it.evadid.distribution.command

import munit.FunSuite
import upickle.default.*

class SerializedExceptionSpec extends FunSuite {
  test("rewrapping an already serialized exception retains its identity and entire cause chain") {
    val original = SerializedException(Exception("outer", Exception("inner", Exception("root"))))
    assert(SerializedException(original) eq original)
    assertEquals(original.cause.flatMap(_.cause).map(_.msg), Some("root"))
    assertEquals(read[SerializedException](original.toJson).cause.flatMap(_.cause).map(_.msg), Some("root"))
  }

  test("explicit wrapping retains the serialized cause stack and optional cause") {
    val original = SerializedException("original", Exception("root"))
    val wrapped = original.asCauseOf(Exception("wrapper"))
    assertEquals(wrapped.msg, "wrapper")
    assert(wrapped.cause.get eq original)
    assertEquals(wrapped.cause.get.stackTrace.toList, original.stackTrace.toList)
    val optional = SerializedException("optional", Some(original))
    assert(optional.cause.get eq original)
    assertEquals(SerializedException("absent", Option.empty[Throwable]).cause, None)
  }

  test("an explicit replacement cause does not inspect the exception's original cause") {
    val wrapper = new Exception("wrapper") {
      override def getCause(): Throwable = throw AssertionError("Replaced cause must not be inspected")
    }
    val replacement = SerializedException("replacement")
    val result = SerializedException(wrapper, replacement)
    assertEquals(result.msg, "wrapper")
    assert(result.cause.get eq replacement)
  }

  test("JSON and MessagePack preserve messages, stack frames and nested causes") {
    val original = SerializedException("outer", Exception("inner"))
    List(read[SerializedException](write(original)), readBinary[SerializedException](writeBinary(original))).foreach { restored =>
      assertEquals(restored.msg, original.msg)
      assertEquals(restored.stackTrace.toList, original.stackTrace.toList)
      assertEquals(restored.cause.map(_.msg), Some("inner"))
    }
  }
}
