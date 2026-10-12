package it.evadid.workbook.elements.interactionElements.programming
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*

import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.vm.naming.NamingStyle
import it.evadid.vm.parsing.python.PythonParser
import it.evadid.vm.types.BeDataType
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtlePythonBridge
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString}
import munit.FunSuite

class PythonTypeHintConversionSpec extends FunSuite {
  private def converted(source: String): ProgrammingStatePythonString =
    ProgrammingStatePythonString(source).toBeExpressionState.toPython

  private def assertStablePython(state: ProgrammingStatePythonString): Unit = {
    var current = state
    (1 to 3).foreach { _ =>
      current = current.toBeExpressionState.toPython
      assertEquals(current.code.trim, state.code.trim)
      val restored = ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(current.toBeExpressionState)
      )
      assertEquals(restored.toPython.code.trim, state.code.trim)
    }
  }

  test("Java declared types take precedence over initializer inference and survive reassignment") {
    val python = ProgrammingStateJavaString(
      """final double distance = 12;
        |Object value = 1;
        |distance = distance + 5;
        |value = "text";
        |""".stripMargin
    ).toPython
    assert(python.code.contains("distance: float = 12"), clue = python.code)
    assert(python.code.contains("distance: float = distance + 5"), clue = python.code)
    assert(python.code.contains("value: Any = 1"), clue = python.code)
    assert(python.code.contains("value: Any = \"text\""), clue = python.code)
    assertEquals(python.code.linesIterator.count(_ == "from typing import Any"), 1)
    assertStablePython(python)
  }

  test("typed parameters and local variables survive nested control flow and Java round trips") {
    val python = converted(
      """def advance(distance: float, enabled: bool) -> None:
        |    remaining: float = distance
        |    if enabled:
        |        while remaining > 0:
        |            remaining = remaining - 1
        |            forward(remaining)
        |advance(3, True)
        |""".stripMargin
    )
    assert(python.code.contains("def advance(distance: float, enabled: bool) -> None:"), clue = python.code)
    assert(python.code.contains("remaining: float = distance"), clue = python.code)
    assert(python.code.contains("remaining: float = remaining - 1"), clue = python.code)
    assertEquals(python.toJava.toPython.code.trim, python.code.trim)
    assertStablePython(python)
  }

  test("union hints retain every member and add required imports exactly once") {
    val python = converted(
      """from typing import Any
        |from datetime import date
        |def choose(value: Any | date) -> float | bool:
        |    return True
        |first: Any | date = 1
        |second: date | Any = 2
        |""".stripMargin
    )
    val header = python.code.linesIterator.find(_.startsWith("def choose(")).get
    assertEquals(header, "def choose(value: Any|date) -> bool|float:")
    assertEquals(python.code.linesIterator.count(_ == "from typing import Any"), 1)
    assertEquals(python.code.linesIterator.count(_ == "from datetime import date"), 1)
    assertStablePython(python)
  }

  test("builtin annotations do not introduce imports") {
    val python = converted(
      """def identity(value: str, enabled: bool) -> str:
        |    return value
        |amount: float = 1
        |""".stripMargin
    )
    assert(!python.code.contains("import "), clue = python.code)
    assertStablePython(python)
  }

  test("annotation-like text in string values does not introduce imports") {
    val python = converted("message: str = \"note: Any | date\"")
    assert(!python.code.contains("import "), clue = python.code)
    assert(python.code.contains("\"note: Any | date\""), clue = python.code)
    assertStablePython(python)
  }

  test("multiple uninitialized declarations survive reparsing without metadata comments") {
    val python = ProgrammingStateJavaString(
      "double distance; boolean enabled; String label; Date day; Object value;"
    ).toPython
    List("distance : float", "enabled : bool", "label : str", "day : date", "value : Any").foreach { hint =>
      assert(python.code.contains(hint), clue = python.code)
    }
    assert(!python.code.contains("EvaEntityName"), clue = python.code)
    assertStablePython(python)
  }

  test("annotation imports are accepted by Snap but unrelated imports remain unsupported") {
    val python = converted("def line(length):\n    forward(length)\nline(10)")
    assert(SnapTurtlePythonBridge.applyPython(python.code).isRight, clue = python.code)
    assert(SnapTurtlePythonBridge.applyPython("from typing import cast\nforward(10)").isLeft)
    assert(SnapTurtlePythonBridge.applyPython("import os\nforward(10)").isLeft)
  }

  test("a printer-shaped comment cannot bypass checked compilation of a static Java main") {
    val source = """class Drawing {
      |  //EvaEntityName(drawing)
      |  public static void main(String[] args) { float distance = 1; }
      |}
      |""".stripMargin
    val java = ProgrammingStateJavaString(source)
    val stored = ProgrammingExercise.StateSerializer.serialize(java)
    assert(java.isClassProgram)
    assert(java.toJavaVmProgram.isLeft)
    intercept[IllegalArgumentException](java.toBeExpressionState)
    intercept[IllegalArgumentException](java.toPython)
    intercept[IllegalArgumentException](java.toSnapXml)
    assertEquals(java.code, source)
    assertEquals(ProgrammingExercise.StateSerializer.serialize(java), stored)
  }

  test("printer hints do not enable unchecked Turtle-qualified calls") {
    val source = """class Legacy {
      |  //EvaEntityName(legacy)
      |  void line() { Turtle.forward(12); }
      |}
      |""".stripMargin
    val java = ProgrammingStateJavaString(source)
    assert(java.isClassProgram)
    assert(java.toJavaVmProgram.isLeft)
    intercept[IllegalArgumentException](java.toBeExpressionState)
    intercept[IllegalArgumentException](java.toPython)
    intercept[IllegalArgumentException](java.toSnapXml)
    assertEquals(java.code, source)
  }

  test("an entity hint inside a string cannot enable legacy class conversion") {
    val source = """class Legacy { String label() { return "//EvaEntityName(label)"; } }"""
    val java = ProgrammingStateJavaString(source)
    assert(java.isClassProgram)
    assert(java.toJavaVmProgram.isLeft)
    intercept[IllegalArgumentException](java.toBeExpressionState)
    intercept[IllegalArgumentException](java.toPython)
    intercept[IllegalArgumentException](java.toSnapXml)
    assertEquals(java.code, source)
  }

  test("printer hints do not permit unsupported nodes in a legacy class") {
    val source = """class Legacy {
      |  //EvaEntityName(legacy)
      |  Object create() { return new Object(); }
      |}
      |""".stripMargin
    val java = ProgrammingStateJavaString(source)
    assert(java.isClassProgram)
    assert(java.toJavaVmProgram.isLeft)
    intercept[IllegalArgumentException](java.toBeExpressionState)
    intercept[IllegalArgumentException](java.toPython)
    intercept[IllegalArgumentException](java.toSnapXml)
    assertEquals(java.code, source)
  }

  test("a printer-generated instance method named main remains convertible") {
    val original = ProgrammingStatePythonString("class Calculator:\n    def main(self, value: float) -> float:\n        return value\n").toBeExpressionState
    val java = original.toJava
    assert(java.code.contains("//EvaEntityName("), clue = java.code)
    assert(java.isClassProgram)
    assert(java.toJavaVmProgram.isLeft)
    assertEquals(java.toPython.code.trim, original.toPython.code.trim)
    intercept[IllegalArgumentException](java.toSnapXml)
  }

  test("Python class with two typed methods round trips through Java") {
    val source =
      """class Calculator:
        |    scale: float = 2
        |
        |    def twice(self, value: float) -> float:
        |        result: float = value * 2
        |        return result
        |
        |    def label(self, text: str) -> str:
        |        return text
        |""".stripMargin
    val original = ProgrammingStatePythonString(source).toBeExpressionState
    val java = original.toJava
    val roundTripped = java.toPython

    assert(java.code.contains("class calculator"), clue = java.code)
    assert(java.code.contains("double twice("), clue = java.code)
    assert(java.code.contains("String label("), clue = java.code)
    assert(java.code.contains("//EvaEntityName("), clue = java.code)
    assert(java.isClassProgram)
    assert(java.toJavaVmProgram.isLeft)
    intercept[IllegalArgumentException](java.toSnapXml)
    assertEquals(roundTripped.code.trim, original.toPython.code.trim, clue = java.code)

    val parsed = PythonParser.parsePythonWithDetails(roundTripped.code)
    assertEquals(parsed.definedClasses.size, 1, clue = roundTripped.code)
    val standaloneNames = parsed.definedFunctions
      .map(_.functionTypeInfo.displayName.getNameIn(English, NamingStyle.SnakeCase)).toSet
    assert(!standaloneNames.contains("twice") && !standaloneNames.contains("label"))
    val clazz = parsed.definedClasses.head
    assertEquals(clazz.name.getNameIn(English, NamingStyle.SnakeCase), "calculator")
    assertEquals(clazz.attributes.map(_.variableType), List(BeDataType.Numeric))
    assertEquals(clazz.methods.map(_.functionTypeInfo.displayName.getNameIn(English, NamingStyle.SnakeCase)),
      List("twice", "label"))
    assertEquals(clazz.methods.map(_.outputs.get.variableType), List(BeDataType.Numeric, BeDataType.String))
    assertEquals(clazz.methods.map(_.inputs.map(_.variableType)),
      List(List(BeDataType.AnyType, BeDataType.Numeric), List(BeDataType.AnyType, BeDataType.String)))
    assert(clazz.methods.forall(_.functionTypeInfo.isMethodInClass.nonEmpty))
    assert(roundTripped.code.contains("result: float = value * 2"), clue = roundTripped.code)
    assert(roundTripped.code.contains("return result"), clue = roundTripped.code)
    assert(roundTripped.code.contains("return text"), clue = roundTripped.code)
    assertStablePython(roundTripped)
  }

}
