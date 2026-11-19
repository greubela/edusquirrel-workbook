package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.vm.code.errors.ProgrammingConversionKind
import it.evadid.vm.conversion.pythonexpression.PythonParser
import it.evadid.util.logging.LoggingLevel
import munit.FunSuite

class ConversionResultSpec extends FunSuite {
  test("conversion failures retain diagnostic categories and positions as values") {
    val result = ProgrammingStateJavaString("class Main {\n public static void main(String[] args) {\n forward(;\n }\n}").convertToLanguage(Python)
    assertEquals(result.finalState, None)
    assert(result.hasLostFunctionality)
    val error = result.conversionInfo.head
    assertEquals(error.warningLevel, LoggingLevel.ERROR)
    assertEquals(error.sourceError.get.kind, ProgrammingConversionKind.Unparsable)
    assert(error.sourceError.get.line.nonEmpty)
  }

  test("first duplicate class wins and dropped definitions produce warnings") {
    val source = ProgrammingStatePythonString("class Counter:\n    value: float = 1\nclass Counter:\n    value: float = 2\n")
    val result = source.convertToLanguage(Python)
    assert(result.hasLostFunctionality)
    assert(result.conversionInfo.exists(_.warningMsg.contains("Duplicate class")))
    val target = result.finalState.get.asInstanceOf[ProgrammingStatePythonStructured]
    assertEquals(target.definedClasses.size, 1)
    assert(target.toString.contains("= 1"), target)
    assert(!target.toString.contains("= 2"), target)
  }

  test("nested classes are hoisted and calls still bind to them") {
    val source = ProgrammingStatePythonString("def build() -> Inner:\n    class Inner:\n        def read(self) -> float:\n            return 3\n    return Inner()\ninstance = build()\nresult = instance.read()\n")
    val normalized = source.toBeExpression.toPython
    assert(normalized.code.startsWith("class Inner:"), normalized.code)
    assert(normalized.code.contains("return Inner()"), normalized.code)
    assertEquals(normalized.toJava.toPython.code.trim, normalized.code.trim)
  }

  test("class signatures are available before any class body is bound") {
    val source = ProgrammingStatePythonString("class Factory:\n    @staticmethod\n    def make() -> Item:\n        return Item(3)\nclass Item:\n    def __init__(self, value: float):\n        self.value = value\nitem = Factory.make()\n")
    val normalized = source.toBeExpression.toPython
    assertEquals(normalized.toJava.toPython.code.trim, normalized.code.trim)
  }
}
