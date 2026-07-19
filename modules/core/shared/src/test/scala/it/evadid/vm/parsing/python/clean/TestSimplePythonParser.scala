package it.evadid.vm.parsing.python.clean

import it.evadid.vm.parsing.python.clean.model.PyAST.*
import munit.FunSuite

class TestSimplePythonParser extends FunSuite {

  private def parseOne(code: String): PyStatement = {
    val parsed = PythonAstParserSimple.parse(code)
    assert(parsed.isRight, parsed.left.getOrElse("parser returned Left"))
    val statements = parsed.toOption.get.statements.map(_.statement).filterNot(_ == PyEmptyStatement)
    assertEquals(statements.size, 1)
    statements.head
  }

  test("parses PyAugAssignment for simple targets") {
    val assignment = parseOne("total += 1").asInstanceOf[PyAugAssignment]

    assertEquals(assignment.target.name, "total")
    assertEquals(assignment.augOperator, "+=")
    assertEquals(assignment.expression.asInstanceOf[PythonLiteral[?]].literalValue, "1")
  }

  test("parses PyAugAssignment for attribute targets and multi-character operators") {
    val assignment = parseOne("bucket.count //= 2").asInstanceOf[PyAugAssignment]

    assertEquals(assignment.target.name, "bucket.count")
    assertEquals(assignment.target.locationString, List("bucket"))
    assertEquals(assignment.augOperator, "//=")
    assertEquals(assignment.expression.asInstanceOf[PythonLiteral[?]].literalValue, "2")
  }

  test("parses a function with a nested expr as parameter") {
    val funccall = parseOne("func(int(3)+3)").asInstanceOf[PyFunctionCall]

    assertEquals(funccall.target.name, "func")
    assertEquals(funccall.target.locationString, List())
    assertEquals(funccall.parameterValues.size, 1)
  }

}
