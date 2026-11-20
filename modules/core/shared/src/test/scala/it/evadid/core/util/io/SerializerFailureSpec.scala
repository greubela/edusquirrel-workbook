package it.evadid.core.util.io

import it.evadid.distribution.command.SerializedException
import munit.FunSuite
import upickle.default.*

class SerializerFailureSpec extends FunSuite {
  private trait Shape
  private case class Circle(radius: Int) extends Shape derives ReadWriter
  private case class Square(side: Int) extends Shape derives ReadWriter

  private val circleOnly: Serializer[Shape] = Serializer.serializerForSubclass[Circle, Shape](classOf[Circle])._2
  private val failing = new Serializer[Int] {
    def serialize(value: Int): String = if value < 0 then throw IllegalStateException("negative") else value.toString
    def deserialize(text: String): Int = text.toInt
  }

  test("try variants report failures as serialized exceptions with a bounded preview") {
    assertEquals(failing.trySerialize(4), Right("4"))
    val serializeError = failing.trySerialize(-1).swap.toOption.get
    assert(serializeError.getMessage.contains("Serialization of '-1'"), serializeError.getMessage)
    assertEquals(failing.tryDeserialize("12"), Right(12))
    val deserializeError = failing.tryDeserialize("x" * 100).swap.toOption.get
    assert(deserializeError.getMessage.startsWith("Error during Deserialisation of '" + "x" * 60 + "':"), deserializeError.getMessage)
    assertEquals(failing.trySerializeAll(List(1, -2, 3)), TypeConverter.ConverterResult(Set(-2), Set("1", "3")))
    assertEquals(failing.tryDeserializeAll(List("1", "two")), TypeConverter.ConverterResult(Set(1), Set("two")))
  }

  test("subclass serializers reject other subtypes and payloads of the wrong shape") {
    assertEquals(circleOnly.deserialize(circleOnly.serialize(Circle(2))), Circle(2))
    intercept[SerializedException](circleOnly.serialize(Square(1)))
    intercept[SerializedException](circleOnly.deserialize("""{"side":1}"""))
    val universal = Serializer.universalTraitSerializer[Shape](Map("Circle" -> circleOnly))
    val error = intercept[SerializedException](universal.serialize(Square(3)))
    assert(error.getMessage.contains("no serializer known"), error.getMessage)
    val nonObject = Serializer.universalTraitSerializer[Shape](Map("Circle" -> new Serializer[Shape] {
      def serialize(value: Shape): String = "[1]"
      def deserialize(text: String): Shape = Circle(0)
    }))
    intercept[SerializedException](nonObject.serialize(Circle(1)))
  }

  test("constructor-like serializers wrap and unwrap their payload and reject other constructors") {
    val wrapped = Serializer.constructorLikeSerializer[Int]("Box")(_.toString)(_.trim.toInt)
    assertEquals(wrapped.serialize(5), "Box(5)")
    assertEquals(wrapped.deserialize(" Box( 7 ) "), 7)
    intercept[SerializedException](wrapped.deserialize("Crate(7)"))
    intercept[SerializedException](wrapped.deserialize("Box(x)"))
  }

  test("singleton serializers accept every known spelling and reject others") {
    val none = Serializer.noneParser()
    assertEquals(none.serialize(None), "None")
    assertEquals(none.deserialize("None"), None)
    assertEquals(none.deserialize("None$"), None)
    intercept[SerializedException](none.deserialize("null"))
    intercept[SerializedException](none.serialize(Some(())))
    val unit = Serializer.singletonSerializer[String]("unit")
    assertEquals(unit.serialize("unit"), "unit")
    assertEquals(unit.deserialize("Singleton(String)"), "unit")
  }

  test("string literals accept both quote styles, triple quotes and optional bare text") {
    val strict = Serializer.stringLiteralIO()
    assertEquals(strict.serialize("text"), "\"text\"")
    assertEquals(strict.serialize("'kept'"), "'kept'")
    assertEquals(strict.deserialize(" \"\"\"multi\nline\"\"\" "), "multi\nline")
    assertEquals(strict.deserialize("'single'"), "single")
    intercept[IllegalArgumentException](strict.deserialize("bare"))
    val lenient = Serializer.stringLiteralIO(parseEverything = true)
    assertEquals(lenient.serialize("bare"), "bare")
    assertEquals(lenient.deserialize(" bare "), "bare")
  }

  test("Python booleans, plain eithers and plain options use source spellings") {
    assertEquals(Serializer.pythonBooleanIO.serialize(true), "True")
    assertEquals(Serializer.pythonBooleanIO.serialize(false), "False")
    assertEquals(Serializer.pythonBooleanIO.deserialize(" TRUE "), true)
    assertEquals(Serializer.pythonBooleanIO.deserialize("yes"), false)
    val either = Serializer.eitherPlainValueIO(Serializer.intDecimalIO, Serializer.stringLiteralIO())
    assertEquals(either.serialize(Left(BigInt(3))), "3")
    assertEquals(either.serialize(Right("a")), "\"a\"")
    assertEquals(either.deserialize("42"), Left(BigInt(42)))
    assertEquals(either.deserialize("'b'"), Right("b"))
    val option = Serializer.optionPlainValueIO(Serializer.intDecimalIO)
    assertEquals(option.serialize(None), "None")
    assertEquals(option.serialize(Some(BigInt(1))), "1")
    assertEquals(option.deserialize(" None "), None)
    assertEquals(option.deserialize("-5"), Some(BigInt(-5)))
    assertEquals(Serializer.integerBaseIO(10).deserialize("+12"), BigInt(12))
  }
}
