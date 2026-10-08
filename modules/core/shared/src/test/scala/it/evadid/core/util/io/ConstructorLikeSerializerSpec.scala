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

  test("positional and named fields share one group without changing JSON types") {
    val layout = Map(0 -> List(VariableDisplayConfig("id", true), VariableDisplayConfig("content", false),
      VariableDisplayConfig("count", true), VariableDisplayConfig("enabled", false), VariableDisplayConfig("data", false)))
    val tricky = value.copy(id = "{\"number\":42}", content = "null", data = Map("true" -> List("false", "123", "[1,2]")))
    val serialized = ConstructorLikeSerializer.serialize(layout, tricky, summon[Writer[Example]], "Example")
    assertEquals(ConstructorLikeSerializer.deserialize(serialized, summon[Reader[Example]], layout, "Example"), tricky)
    val fields = ConstructorLikeSerializer.deserialize(serialized).valuesWithOrder(layout)
    assertEquals(fields("id"), ujson.Str(tricky.id))
    assertEquals(fields("content"), ujson.Str("null"))
    assertEquals(fields("data"), writeJs(tricky.data))
  }

  test("constructor fields preserve null, nested arrays and objects and numeric values") {
    val fields = Map[String, ujson.Value](
      "nothing" -> ujson.Null, "number" -> ujson.Num(-1.25e12), "flag" -> ujson.Bool(true),
      "nested" -> ujson.Arr(ujson.Obj("text" -> ")(\"\\\\\\n"), ujson.Null, ujson.Arr(1, false)))
    val layout = Map(0 -> List(VariableDisplayConfig("nothing", true), VariableDisplayConfig("number", true),
      VariableDisplayConfig("flag", false), VariableDisplayConfig("nested", false)))
    val serialized = ConstructorLikeSerializer.serializeFields(layout, fields, "Values")
    assertEquals(ConstructorLikeSerializer.deserialize(serialized).valuesWithOrder(layout), fields)
  }

  test("duplicate named fields cannot overwrite positional fields or other named fields") {
    val layout = Map(0 -> List(VariableDisplayConfig("id", true)))
    intercept[IllegalArgumentException] {
      ConstructorLikeSerializer.deserialize("Example(1, {\"id\":2})").valuesWithOrder(layout)
    }
    intercept[IllegalArgumentException] {
      ConstructorLikeSerializer.deserialize("Example({\"id\":1}, {\"id\":2})").values
    }
    intercept[IllegalArgumentException] {
      ConstructorLikeSerializer.deserialize("Example(1, 2)").valuesWithOrder(layout)
    }
  }

  test("invalid display layouts fail instead of silently dropping fields") {
    for (layout <- List(
      Map(-1 -> List(VariableDisplayConfig("id", true))),
      Map(0 -> List(VariableDisplayConfig("id", true)), 1 -> List(VariableDisplayConfig("id", false))))) {
      intercept[IllegalArgumentException] {
        ConstructorLikeSerializer.serialize(layout, value, summon[Writer[Example]], "Example")
      }
    }
  }

}
