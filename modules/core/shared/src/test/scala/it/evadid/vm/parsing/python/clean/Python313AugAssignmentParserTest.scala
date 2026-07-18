package it.evadid.vm.parsing.python.clean

import it.evadid.vm.parsing.python.clean.PyAST.*
import munit.FunSuite

class Python313AugAssignmentParserTest extends FunSuite {

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
    assertEquals(assignment.expression.asInstanceOf[PythonLiteral[?]].literalAsString, "1")
  }

  test("parses PyAugAssignment for attribute targets and multi-character operators") {
    val assignment = parseOne("bucket.count //= 2").asInstanceOf[PyAugAssignment]

    assertEquals(assignment.target.name, "count")
    assertEquals(assignment.target.locationString, List("bucket"))
    assertEquals(assignment.augOperator, "//=")
    assertEquals(assignment.expression.asInstanceOf[PythonLiteral[?]].literalAsString, "2")
  }
}
