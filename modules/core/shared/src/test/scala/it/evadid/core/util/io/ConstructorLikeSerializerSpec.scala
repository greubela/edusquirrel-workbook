package it.evadid.core.util.io

import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import munit.FunSuite
import upickle.default.*

object ConstructorLikeSerializerSpec {
  case class Example(id: String, count: Int, content: String, data: Map[String, List[String]], enabled: Boolean) derives ReadWriter
}
class ConstructorLikeSerializerSpec extends FunSuite {
  import ConstructorLikeSerializerSpec.*
  private val value = Example(" id ", 7, " Quotes: \"hello\"; \\;\nGrüße )({} ", Map("key" -> List("", "\t", "\"quoted\"")), false)
  private val order = Map(
    2 -> List(VariableDisplayConfig("content", false), VariableDisplayConfig("data", false)),
    0 -> List(VariableDisplayConfig("id", true), VariableDisplayConfig("count", true)),
    3 -> List(VariableDisplayConfig("enabled", false))
  )
  private val base = Serializer.fromImplicitRW[Example]
  private val constructor = new ConstructorLikeSerializer[Example] {
    val regularSerializer = base
    val constructorName = "Example"
    val elementMapAndOrder = order
  }

  test("constructor trait matches base serializer with positional, named, empty and multiple argument groups") {
    assertEquals(base.deserialize(base.serialize(value)), value)
    val serialized = constructor.serialize(value)
    assert(serialized.startsWith("Example(\" id \", 7)()({\"content\":"))
    assertEquals(constructor.deserialize(serialized), value)
    assertEquals(read[Example](write(value)(using constructor.uPickleReadWrite))(using constructor.uPickleReadWrite), value)
    val fields = ConstructorLikeSerializer.deserialize(serialized).valuesWithOrder(order)
    assertEquals(fields("count"), ujson.Num(7))
    assertEquals(fields("data"), writeJs(value.data))
    assertEquals(fields("content"), ujson.Str(value.content))
    assertEquals(fields("enabled"), ujson.Bool(false))
  }

  test("unconfigured fields are preserved as named JSON values") {
    val layout = Map(0 -> List(VariableDisplayConfig("id", true)))
    val serialized = ConstructorLikeSerializer.serialize(layout, value, summon[Writer[Example]], "Example")
    assertEquals(ConstructorLikeSerializer.deserialize(serialized, summon[Reader[Example]], layout, "Example"), value)
  }

  test("named-only constructors and empty constructors round-trip") {
    val serialized = ConstructorLikeSerializer.serialize(Map.empty, value, summon[Writer[Example]], "Example")
    assertEquals(ConstructorLikeSerializer.deserialize(serialized, summon[Reader[Example]]), value)
    assertEquals(ConstructorLikeSerializer.deserialize("Empty()").values, Map.empty[String, ujson.Value])
    assertEquals(ConstructorLikeSerializer.serializeFields(Map.empty, Map.empty, "Empty"), "Empty()")
  }

  test("parser consumes the entire input and rejects malformed JSON and duplicate fields") {
    for (input <- List("Example({\"x\":1}) trailing", "Example({\"x\":1})(", "Example({\"x\":})", "Example")) {
      assert(ConstructorLikeParserWithJsonElements.parseString(input).isFailure, input)
    }
    intercept[IllegalArgumentException](ConstructorLikeSerializer.deserialize("Example({\"x\":1})({\"x\":2})").values)
    intercept[IllegalArgumentException](constructor.deserialize(constructor.serialize(value).replace("Example(", "Wrong(")))
    intercept[IllegalArgumentException](constructor.deserialize("Example()"))
  }
}
