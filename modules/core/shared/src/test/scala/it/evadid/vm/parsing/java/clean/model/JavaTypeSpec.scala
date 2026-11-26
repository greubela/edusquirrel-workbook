package it.evadid.vm.parsing.java.clean.model

import it.evadid.vm.parsing.java.clean.model.JavaType.*
import munit.FunSuite
import upickle.default.{read, write}

class JavaTypeSpec extends FunSuite {

  test("Java literals normalise through their source-language serializers") {
    assertEquals(JAVA_INTEGER_OCT.createLiteralUnsafe("017").literalValue, "017")
    assertEquals(JAVA_INTEGER_OCT.serializeTargetLanguageValue.deserialize("0"), BigInt(0))
    assertEquals(JAVA_INTEGER_BIN.createLiteralUnsafe("0B11").literalValue, "0b11")
    assertEquals(JAVA_BOOL.createLiteralUnsafe("true").literalValue, "true")
    val array = JAVA_ARRAY(JAVA_STRING)
    assertEquals(array.serializeTargetLanguageValue.deserialize("{\"a,b\", \"c\\\"}\"}"), List("a,b", "c\"}"))
    assertEquals(array.serializeTargetLanguageValue.serialize(List("x")), "{\"x\"}")
    assertEquals(array.serializeTargetLanguageValue.deserialize(" {} "), Nil)
    val nested = JAVA_LIST(JAVA_LIST(JAVA_INTEGER))
    assertEquals(nested.serializeTargetLanguageValue.deserialize("List.of(List.of(1, 2), List.of())"), List(List(BigInt(1), BigInt(2)), Nil))
    assertEquals(nested.serializerScalaValue.serialize(List(List(BigInt(3)))), "List(List(3))")
    assertEquals(JAVA_LIST(JAVA_INTEGER).serializeTargetLanguageValue.deserialize("4, 5"), List(BigInt(4), BigInt(5)))
  }
}
