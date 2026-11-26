package it.evadid.vm.parsing.source

import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import munit.FunSuite

class SourceBindingSpec extends FunSuite {
  test("same-arity Java overloads retain their own definitions") {
    val source = SourceParsing.parse("class Box { int f(int n) { return n; } String f(String text) { return text; } }", SourceLanguage.Java)
    val functions = source.parsed().toOption.get.functions
    assertEquals(functions.map(_.definition.get.inputs.head.name.spelling()), Vector("n", "text"))
    assertEquals(functions.map(_.definition.get.inputs.head.variableType),
      Vector(it.evadid.vm.types.BeDataType.Numeric, it.evadid.vm.types.BeDataType.String))
  }

  test("shadowed Python functions bind by source occurrence, not name and arity") {
    val source = SourceParsing.parse("def f(outer):\n    def f(inner):\n        return inner + 1\n    return f(outer)\n", SourceLanguage.Python)
    val functions = source.parsed().functions
    assertEquals(functions.map(_.definition.get.inputs.head.name.spelling()), Vector("outer", "inner"))
  }

  test("an inlined main is not associated with a definition absent from the final tree") {
    val source = SourceParsing.parse("def main():\n    forward(10)\nmain()\n", SourceLanguage.Python)
    assertEquals(source.parsed().functions.map(_.definition), Vector(None))
  }
}
