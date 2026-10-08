package it.evadid.core.util.io

import it.evadid.distribution.command.SerializedException
import munit.FunSuite
import upickle.default.*

class SerializerPackageSpec extends FunSuite {
  test("option projections preserve empty, short and nested payloads") {
    val io = Serializer.optionProjectionIO(Serializer.stringIO)
    List(None, Some(""), Some("x"), Some("a(b)c"), Some(" Grüße ")).foreach(v => assertEquals(io.deserialize(io.serialize(v)), v))
    val nested = Serializer.optionProjectionIO(io)
    List(None, Some(None), Some(Some("value"))).foreach(v => assertEquals(nested.deserialize(nested.serialize(v)), v))
    intercept[IllegalArgumentException](io.deserialize("Some(x"))
  }
  test("either projections preserve both branches and nested option payloads") {
    val io = Serializer.eitherProjectionIO(Serializer.stringIO, Serializer.optionProjectionIO(Serializer.stringIO))
    val values: List[Either[String, Option[String]]] = List(Left(""), Left("x"), Left("a(b)c"), Right(None), Right(Some("Grüße")))
    values.foreach(v => assertEquals(io.deserialize(io.serialize(v)), v))
    intercept[IllegalArgumentException](io.deserialize("Middle(x)"))
  }
  for ((label, io, prefix, radix) <- List(
    ("binary", Serializer.intBinaryIO, "0b", 2), ("hex", Serializer.intHexIO, "0x", 16), ("octal", Serializer.intOctalIO, "0o", 8))) {
    test(s"$label integer formatting uses valid prefixes and keeps the sign outside the prefix") {
      for (value <- List(BigInt(0), BigInt(15), BigInt(-15), BigInt("123456789012345678901234567890"))) {
        val expected = (if (value < 0) "-" else "") + prefix + value.abs.toString(radix)
        assertEquals(io.serialize(value), expected)
        assertEquals(io.deserialize(expected), value)
        assertEquals(io.deserialize("  " + expected.toUpperCase + "  "), value)
      }
    }
  }
  test("legacy numeric prefixes remain readable, including historically octal hex output") {
    assertEquals(Serializer.intBinaryIO.deserialize("Ob-1111"), BigInt(-15))
    assertEquals(Serializer.intHexIO.deserialize("Ox17"), BigInt(15))
    assertEquals(Serializer.intOctalIO.deserialize("O17"), BigInt(15))
    assertEquals(Serializer.intHexIO.deserialize("0xff"), BigInt(255))
  }
  test("constructor-like wrappers trim outer whitespace without losing payload characters") {
    val io = Serializer.constructorLikeSerializer("Value", Serializer.stringIO)
    assertEquals(io.deserialize("  Value( payload )  "), " payload ")
    assertEquals(io.deserialize(io.serialize("a(b)c")), "a(b)c")
    assertEquals(io.deserialize("Value()"), "")
  }
  test("safe sequence decoding retains valid entries around serialized failures") {
    val io = new Serializer[Int] {
      def serialize(value: Int): String = value.toString
      def deserialize(value: String): Int = if (value == "bad") throw SerializedException("bad") else value.toInt
    }
    assertEquals(read[Seq[Int]]("[\"1\",\"bad\",\"2\"]")(using io.safeSeqReadWriter), Seq(1, 2))
    assertEquals(write[Seq[Int]](Seq(1, 2))(using io.safeSeqReadWriter), "[\"1\",\"2\"]")
  }
  test("optional string serialization preserves significant whitespace") {
    val value = Some(" Grüße ")
    assertEquals(Serializer.stringOptionIO.deserialize(Serializer.stringOptionIO.serialize(value)), value)
  }
  test("plain optional and either values select the appropriate underlying reader") {
    val opt = Serializer.optionPlainValueIO(Serializer.intDecimalIO)
    assertEquals(opt.deserialize("None"), None)
    assertEquals(opt.deserialize(opt.serialize(Some(BigInt(123)))), Some(BigInt(123)))
    val either = Serializer.eitherPlainValueIO(Serializer.intDecimalIO, Serializer.stringIO)
    assertEquals(either.deserialize("123"), Left(BigInt(123)))
    assertEquals(either.deserialize("text"), Right("text"))
  }
  test("Java and Python numeric type serializers emit language-appropriate octal syntax") {
    import it.evadid.vm.parsing.java.clean.model.JavaType
    import it.evadid.vm.parsing.python.clean.model.PythonType
    val java = JavaType.JAVA_INTEGER_OCT().serializerJavaValue
    val python = PythonType.PYTHON_INTEGER_OCT().serializerPythonValue
    assertEquals(java.serialize(BigInt(15)), "017")
    assertEquals(java.serialize(BigInt(-15)), "-017")
    assertEquals(java.deserialize("0"), BigInt(0))
    assertEquals(java.deserialize("017"), BigInt(15))
    assertEquals(python.serialize(BigInt(15)), "0o17")
    assertEquals(python.deserialize("0o17"), BigInt(15))
  }

  test("mapped serializers retain the original failure cause") {
    val io = Serializer.intDecimalIO.map(_.toInt, BigInt(_))
    val error = intercept[SerializedException](io.deserialize("invalid"))
    assert(error.cause.nonEmpty)
    assert(error.cause.get.msg.contains("invalid"))
    val rejecting = Serializer.stringIO.map(identity, (_: String) => throw new IllegalArgumentException("rejected"))
    val serializationError = intercept[SerializedException](rejecting.serialize("value"))
    assertEquals(serializationError.cause.get.msg, "rejected")
  }
  test("serializer adapters do not hide fatal errors behind a parsing fallback") {
    val fatal = new Serializer[String] {
      def serialize(value: String): String = throw new LinkageError("fatal serialize")
      def deserialize(value: String): String = throw new LinkageError("fatal read")
    }
    val mapped = fatal.map(identity, identity)
    val writeError = try { mapped.serialize("x"); None } catch { case e: LinkageError => Some(e) }
    assertEquals(writeError.map(_.getMessage), Some("fatal serialize"))
    val either = Serializer.eitherPlainValueIO(fatal, Serializer.stringIO)
    val readError = try { either.deserialize("x"); None } catch { case e: LinkageError => Some(e) }
    assertEquals(readError.map(_.getMessage), Some("fatal read"))
  }

}
