package it.evadid.vm.parsing.java

import munit.FunSuite

class JavaToBeExpressionParserSpec extends FunSuite {
  test("converts Java statements and control flow to Be expressions") {
    val result = JavaToBeExpressionParser.parseProgram(
      """int distance = 10;
        |while (distance > 0) {
        |  forward(distance);
        |  distance -= 1;
        |}""".stripMargin
    )

    assert(result.isRight, clue = result)
    assert(!result.toOption.get.fullProgram.toString.contains("Unparsable"))
  }

  test("converts Java boolean operators") {
    val result = JavaToBeExpressionParser.parseProgram(
      "boolean valid = x > 0 && x < 10;"
    )
    assert(result.isRight, clue = result)
  }
}
