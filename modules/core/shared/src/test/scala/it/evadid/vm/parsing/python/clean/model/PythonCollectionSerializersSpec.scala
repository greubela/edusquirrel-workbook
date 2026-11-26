package it.evadid.vm.parsing.python.clean.model

import it.evadid.vm.parsing.python.clean.model.PythonType.*
import munit.FunSuite

class PythonCollectionSerializersSpec extends FunSuite {
  private val text = PYTHON_STRING
  private val int = PYTHON_INTEGER

  test("lists split only at top-level commas outside strings and nested brackets") {
    val nested = PYTHON_LIST(PYTHON_LIST(int)).serializerPythonValue
    assertEquals(nested.deserialize("[[1, 2], [], [3]]"), List(List(BigInt(1), BigInt(2)), Nil, List(BigInt(3))))
    assertEquals(nested.serialize(List(List(BigInt(1)), Nil)), "[[1], []]")
    val strings = PYTHON_LIST(text).serializerPythonValue
    assertEquals(strings.deserialize("""['a, b', "c\"]", 'd\\']"""), List("a, b", "c\\\"]", "d\\\\"))
    assertEquals(strings.deserialize(" [ ] "), Nil)
    assertEquals(PYTHON_LIST(int).serializerScalaValue.deserialize("List(1, 2)"), List(BigInt(1), BigInt(2)))
    assertEquals(PYTHON_LIST(int).serializerPythonValue.deserialize("4, 5"), List(BigInt(4), BigInt(5)))
  }

  test("sets and dictionaries read nested values, empty literals and colons inside strings") {
    val sets = PYTHON_SET(int).serializerPythonValue
    assertEquals(sets.deserialize("{3, 1, 3}"), Set(BigInt(1), BigInt(3)))
    assertEquals(sets.deserialize("{}"), Set.empty[BigInt])
    assertEquals(sets.serialize(Set(BigInt(2))), "{2}")
    assertEquals(sets.deserialize("7"), Set(BigInt(7)))
    val dict = PYTHON_DICT(text, PYTHON_LIST(int)).serializerPythonValue
    assertEquals(dict.deserialize("{'a:b': [1, 2], 'c': []}"), Map("a:b" -> List(BigInt(1), BigInt(2)), "c" -> Nil))
    assertEquals(dict.serialize(Map("k" -> List(BigInt(1)))), "{\"k\": [1]}")
    assertEquals(dict.deserialize(" {} "), Map.empty[String, List[BigInt]])
    assertEquals(PYTHON_DICT(text, int).serializerPythonValue.deserialize("'x': 1"), Map("x" -> BigInt(1)))
  }
}
