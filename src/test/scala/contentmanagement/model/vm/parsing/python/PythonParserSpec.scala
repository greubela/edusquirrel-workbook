package contentmanagement.model.vm.parsing.python

import contentmanagement.model.vm.expressions.{BeExpressionUnkown, BeSequence}
import contentmanagement.model.vm.expressions.controlStructures.BeExpressionIfElse
import munit.FunSuite

class PythonParserSpec extends FunSuite {

  test("parse empty source as empty sequence") {
    val result = PythonParser.parsePython("")
    assertEquals(result, BeSequence(List.empty, shouldEvaluateToUnit = false))
  }

  test("parse simple statements into sequence of unknown expressions") {
    val source =
      """x = 3
        |main(x)
        |""".stripMargin

    val result = PythonParser.parsePython(source)

    val expected = BeSequence(
      List(
        BeExpressionUnkown("x = 3"),
        BeExpressionUnkown("main(x)")
      ),
      shouldEvaluateToUnit = false
    )

    assertEquals(result, expected)
  }

  test("parse if statements with optional leading whitespace and body") {
    val source =
      """    if(x > 5):
        |        print(par)
        |else:
        |    print("too small")
        |""".stripMargin

    val result = PythonParser.parsePython(source)

    val expected = BeSequence(
      List(
        BeExpressionIfElse(
          BeExpressionUnkown("(x > 5)"),
          BeSequence(List(BeExpressionUnkown("print(par)")), shouldEvaluateToUnit = false),
          BeSequence(List(BeExpressionUnkown("print(\"too small\")")), shouldEvaluateToUnit = false)
        )
      ),
      shouldEvaluateToUnit = false
    )

    assertEquals(result, expected)
  }
}
