package contentmanagement.model.vm.parsing.python

import contentmanagement.model.vm.code.controlStructures.{BeIfElse, BeSequence}
import contentmanagement.model.vm.code.errors.BeExpressionUnsupported
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
        BeExpressionUnsupported("x = 3"),
        BeExpressionUnsupported("main(x)")
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
        BeIfElse(
          BeExpressionUnsupported("(x > 5)"),
          BeSequence(List(BeExpressionUnsupported("print(par)")), shouldEvaluateToUnit = false),
          BeSequence(List(BeExpressionUnsupported("print(\"too small\")")), shouldEvaluateToUnit = false)
        )
      ),
      shouldEvaluateToUnit = false
    )

    assertEquals(result, expected)
  }
}
