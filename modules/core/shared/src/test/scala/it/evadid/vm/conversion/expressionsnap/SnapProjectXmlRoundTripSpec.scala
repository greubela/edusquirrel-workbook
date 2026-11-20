package it.evadid.vm.conversion.expressionsnap

import it.evadid.core.datastructures.language.AppLanguage.{Python, SnapLanguage}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import munit.FunSuite

class SnapProjectXmlRoundTripSpec extends FunSuite {
  private def commands(state: ProgrammingState) = state.toBeExpression.turtleCommandExecutionInfo.completedCommands

  /** Snap variables are untyped, so the round trip is compared by the drawing and the kept control structure. */
  private val programs = Seq(
    "negated condition" -> ("ready = 0\nif not ready:\n    forward(10)\n", "if not ready:"),
    "variable condition" -> ("ready = 1\nwhile ready:\n    forward(10)\n    ready = 0\n", "while ready:"),
    "comparison chain" -> ("x = 3\nif x >= 2 and x != 5 or x <= 0:\n    left(90)\nelse:\n    right(90)\n", "((x >= 2) and (x != 5)) or (x <= 0)"),
    "nested arithmetic arguments" -> ("size = 10\nforward((size + 2) * 3 - size / 2 % 4)\n", "((size + 2) * 3) - ((size / 2) % 4)"),
    "counting loop" -> ("for i in range(1, 4):\n    forward(i * 10)\n", "for i in range(1, 3 + 1):"),
    "defined helper with parameters" -> ("def square(side):\n    for _ in range(4):\n        forward(side)\n        left(90)\nsquare(20)\n", "square(20)"))

  for ((label, (code, kept)) <- programs) {
    test(s"$label survives Snap XML") {
      val python = ProgrammingStateRaw(Python, code)
      val snap = python.toSnapXml
      assertEquals(snap.language, SnapLanguage)
      assert(snap.toPython.code.contains(kept), snap.toPython.code)
      assertEquals(commands(snap), commands(python))
    }
  }

  test("helper functions that return values have no Snap command representation") {
    intercept[IllegalArgumentException](ProgrammingStateRaw(Python, "def ready():\n    return 1\nif ready() == 1:\n    forward(5)\n").toSnapXml)
  }

  test("the empty and mini projects are complete Snap documents") {
    assert(SnapProjectXml.empty.startsWith("<project"), SnapProjectXml.empty.take(80))
    assert(SnapProjectXml.mini.contains("<scripts>"), SnapProjectXml.mini.take(200))
  }
}
