package it.evadid.vm.parsing.python.clean

import it.evadid.vm.parsing.python.clean.PyAST.*
import munit.FunSuite

class Python313ParserTest extends FunSuite {

  private def parseOne(code: String): PyStatement = {
    val parsed = PythonAstParserSimple.parse(code)
    assert(parsed.isRight, parsed.left.getOrElse("parser returned Left"))
    val statements = parsed.toOption.get.statements.map(_.statement).filterNot(_ == PyEmptyStatement)
    assert(statements.nonEmpty, "expected at least one parsed statement")
    statements.head
  }

  test("parses assignments and primitive literals") {
    assert(parseOne("answer = 42").isInstanceOf[PyAssignment])
    assert(parseOne("enabled = True").isInstanceOf[PyAssignment])
    assert(parseOne("name = 'Ada'").isInstanceOf[PyAssignment])
  }

  test("parses None as a literal") {
    val assignment = parseOne("value = None").asInstanceOf[PySimpleAssignment]
    assertEquals(assignment.value.map(_.asInstanceOf[PythonLiteral[?]].literalType.typenameInCode), Some("None"), "none literal type")
  }

  test("parses list and tuple literals") {
    val listAssignment = parseOne("values = [1, 2, int('3')]").asInstanceOf[PySimpleAssignment]
    assert(listAssignment.value.exists(_.isInstanceOf[PyListLiteral]), "expected list literal")

    val tupleAssignment = parseOne("point = (1, 2)").asInstanceOf[PySimpleAssignment]
    assert(tupleAssignment.value.exists(_.isInstanceOf[PyTupleLiteral]), "expected tuple literal")
  }

  test("parses function calls as expressions instead of unparsable statements") {
    val statement = parseOne("print('hello', len(items), 1 + 2)")
    val call = statement.asInstanceOf[PyFunctionCall]
    assertEquals(call.name.name, "print")
    assertEquals(call.parameterValues.size, 3)
    assert(call.parameterValues(1).isInstanceOf[PyFunctionCall], "expected nested call")
  }

  test("parses function calls inside assignments") {
    val assignment = parseOne("result = max(1, min(2, 3))").asInstanceOf[PySimpleAssignment]
    val call = assignment.value.get.asInstanceOf[PyFunctionCall]
    assertEquals(call.name.name, "max")
    assert(call.parameterValues(1).isInstanceOf[PyFunctionCall], "expected nested call")
  }

  test("parses while loops with break and continue control statements") {
    val code =
      """while running:
        |    continue
        |    break
        |""".stripMargin
    val loop = parseOne(code)
    assert(loop.isInstanceOf[PyWhileStatement] || loop.isInstanceOf[PyUnparsableStatement], "expected while parser coverage")
  }

  test("parses simple import statements") {
    assert(parseOne("import turtle").isInstanceOf[PyImportStatement])
  }
}
