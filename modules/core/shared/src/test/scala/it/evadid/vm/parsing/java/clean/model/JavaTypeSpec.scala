package it.evadid.vm.parsing.java.clean.model

import it.evadid.vm.parsing.java.clean.model.JavaType.*
import munit.FunSuite
import upickle.default.{read, write}

class JavaTypeSpec extends FunSuite {
  test("every Java type round-trips through its descriptor codec, including nested collections") {
    val types: List[JavaType[?]] = List(new JAVA_INTEGER, new JAVA_INTEGER_HEX, new JAVA_INTEGER_OCT, new JAVA_INTEGER_BIN,
      new JAVA_FLOAT, new JAVA_STRING, new JAVA_BOOL, new JAVA_ANY, new JAVA_LIST(new JAVA_ARRAY(new JAVA_INTEGER)),
      new JAVA_UNPARSABLE_TYPE("Turtle"))
    types.foreach { value =>
      val restored = read[JavaType[Any]](write[JavaType[Any]](value.asInstanceOf[JavaType[Any]]))
      assertEquals((restored.getClass, restored.typenameInCode), (value.getClass, value.typenameInCode))
    }
    assertEquals(new JAVA_LIST(new JAVA_ARRAY(new JAVA_INTEGER)).typenameInCode, "List<int[]>")
    val listJson = write[JavaType[Any]](new JAVA_LIST(new JAVA_INTEGER).asInstanceOf[JavaType[Any]])
    intercept[Exception](read[JavaType[Any]](listJson.replace("\"list\"", "\"integer\"")))
    intercept[Exception](read[JavaType[Any]](listJson.replace("\"list\"", "\"map\"")))
  }

  test("Java literals normalise through their source-language serializers") {
    assertEquals(new JAVA_INTEGER_OCT().createLiteralUnsafe("017").literalValue, "017")
    assertEquals(new JAVA_INTEGER_OCT().serializeTargetLanguageValue.deserialize("0"), BigInt(0))
    assertEquals(new JAVA_INTEGER_BIN().createLiteralUnsafe("0B11").literalValue, "0b11")
    assertEquals(new JAVA_BOOL().createLiteralUnsafe("true").literalValue, "true")
    val array = new JAVA_ARRAY(new JAVA_STRING)
    assertEquals(array.serializeTargetLanguageValue.deserialize("{\"a,b\", \"c\\\"}\"}"), List("a,b", "c\"}"))
    assertEquals(array.serializeTargetLanguageValue.serialize(List("x")), "{\"x\"}")
    assertEquals(array.serializeTargetLanguageValue.deserialize(" {} "), Nil)
    val nested = new JAVA_LIST(new JAVA_LIST(new JAVA_INTEGER))
    assertEquals(nested.serializeTargetLanguageValue.deserialize("List.of(List.of(1, 2), List.of())"), List(List(BigInt(1), BigInt(2)), Nil))
    assertEquals(nested.serializerScalaValue.serialize(List(List(BigInt(3)))), "List(List(3))")
    assertEquals(new JAVA_LIST(new JAVA_INTEGER).serializeTargetLanguageValue.deserialize("4, 5"), List(BigInt(4), BigInt(5)))
  }
}
