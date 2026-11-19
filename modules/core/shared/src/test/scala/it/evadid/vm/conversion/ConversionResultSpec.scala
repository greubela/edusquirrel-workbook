package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.vm.code.errors.ProgrammingConversionKind
import it.evadid.vm.conversion.pythonexpression.PythonParser
import it.evadid.util.logging.LoggingLevel
import munit.FunSuite

class ConversionResultSpec extends FunSuite {
  test("conversion failures retain diagnostic categories and positions as values") {
    val result = ProgrammingStateRaw(Java, "class Main {\n public static void main(String[] args) {\n forward(;\n }\n}").convertToLanguage(Python)
    assertEquals(result.finalState, None)
    assert(result.hasLostFunctionality)
    val error = result.conversionInfo.head
    assertEquals(error.warningLevel, LoggingLevel.ERROR)
    assertEquals(error.sourceError.get.kind, ProgrammingConversionKind.Unparsable)
    assert(error.sourceError.get.line.nonEmpty)
  }

  test("first duplicate class wins and dropped definitions produce warnings") {
    val source = ProgrammingStateRaw(Python, "class Counter:\n    value: float = 1\nclass Counter:\n    value: float = 2\n")
    val result = source.convertToLanguage(Python)
    assert(result.hasLostFunctionality)
    assert(result.conversionInfo.exists(_.warningMsg.contains("Duplicate class")))
    val target = result.finalState.get.asInstanceOf[ProgrammingStatePythonStructured]
    assertEquals(target.definedClasses.size, 1)
    assert(target.toString.contains("= 1"), target)
    assert(!target.toString.contains("= 2"), target)
  }

  test("nested classes are hoisted and calls still bind to them") {
    val source = ProgrammingStateRaw(Python, "def build() -> Inner:\n    class Inner:\n        def read(self) -> float:\n            return 3\n    return Inner()\ninstance = build()\nresult = instance.read()\n")
    val normalized = source.toBeExpression.toPython
    assert(normalized.code.startsWith("class Inner:"), normalized.code)
    assert(normalized.code.contains("return Inner()"), normalized.code)
    assertEquals(normalized.toJava.toPython.code.trim, normalized.code.trim)
  }

  test("class signatures are available before any class body is bound") {
    val source = ProgrammingStateRaw(Python, "class Factory:\n    @staticmethod\n    def make() -> Item:\n        return Item(3)\nclass Item:\n    def __init__(self, value: float):\n        self.value = value\nitem = Factory.make()\n")
    val normalized = source.toBeExpression.toPython
    assertEquals(normalized.toJava.toPython.code.trim, normalized.code.trim)
  }
  test("Java Main preserves initialization order relative to other module statements") {
    val source = ProgrammingStateRaw(Python, "x = 1\nforward(x)\nx = 5\ny = x + 1\nforward(y)\n")
    val java = source.convertToLanguage(Java).finalState.get
    val expected = source.toBeExpression.turtleCommandExecutionInfo.completedCommands
    assertEquals(java.toBeExpression.turtleCommandExecutionInfo.completedCommands, expected)
    assertEquals(java.toPython.toBeExpression.turtleCommandExecutionInfo.completedCommands, expected)
  }

  test("module helpers observe global assignments in source order") {
    val source = ProgrammingStateRaw(Python, "x = 1\ndef report():\n    forward(x)\nx = 6\nreport()\n")
    val java = source.convertToLanguage(Java).finalState.get
    assertEquals(java.toBeExpression.turtleCommandExecutionInfo.completedCommands,
      source.toBeExpression.turtleCommandExecutionInfo.completedCommands)
  }

  test("integer widening is reported as a conversion loss rather than silently hidden") {
    val source = ProgrammingStateRaw(Java, "class Main { static int value = 5 / 2; public static void main(String[] args) {} }")
    val result = source.convertToLanguage(Python)
    assert(result.finalState.nonEmpty)
    assert(result.hasLostFunctionality)
    assert(result.conversionInfo.exists(_.warningMsg.contains("integer")))
    assert(!source.convertToLanguage(Java).conversionInfo.exists(_.warningMsg.contains("integer")))
  }

}
