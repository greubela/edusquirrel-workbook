package it.evadid.vm.parsing.python.clean

import it.evadid.vm.parsing.python.clean.PyAST.*
import munit.FunSuite

class Python313ParserTest extends FunSuite {

  private def parseOne(code: String): PyStatement = {
    val parsed = Python313Parser.parse(code)
    assert(parsed.isRight, parsed.left.getOrElse("parser returned Left"))
    val statements = parsed.toOption.get.statements.map(_.statement).filterNot(_ == PyEmptyStatement)
    assertEquals(statements.size, 1)
    statements.head
  }

  test("parses assignments and primitive literals") {
    assert(parseOne("answer = 42").isInstanceOf[PyAssignment])
    assert(parseOne("enabled = True").isInstanceOf[PyAssignment])
    assert(parseOne("name = 'Ada'").isInstanceOf[PyAssignment])
  }

  test("parses None as a literal") {
    val assignment = parseOne("value = None").asInstanceOf[PyAssignment]
    assertEquals(assignment.value.map(_.asInstanceOf[PyLiteral[?]].pythonTyp), Some(PythonType.PYTHON_NONE))
  }

  test("parses list and tuple literals") {
    val listAssignment = parseOne("values = [1, 2, int('3')]").asInstanceOf[PyAssignment]
    assert(listAssignment.value.exists(_.isInstanceOf[PyListLiteral]))

    val tupleAssignment = parseOne("point = (1, 2)").asInstanceOf[PyAssignment]
    assert(tupleAssignment.value.exists(_.isInstanceOf[PyTupleLiteral]))
  }

  test("parses function calls as expressions instead of unparsable statements") {
    val statement = parseOne("print('hello', len(items), 1 + 2)")
    val call = statement.asInstanceOf[PyFunctionCall]
    assertEquals(call.name.name, "print")
    assertEquals(call.parameterValues.size, 3)
    assert(call.parameterValues(1).isInstanceOf[PyFunctionCall])
  }

  test("parses function calls inside assignments") {
    val assignment = parseOne("result = max(1, min(2, 3))").asInstanceOf[PyAssignment]
    val call = assignment.value.get.asInstanceOf[PyFunctionCall]
    assertEquals(call.name.name, "max")
    assert(call.parameterValues(1).isInstanceOf[PyFunctionCall])
  }

  test("parses while loops with break and continue control statements") {
    val code =
      """while running:
        |    continue
        |    break
        |""".stripMargin
    val loop = parseOne(code).asInstanceOf[PyWhileStatement]
    assertEquals(loop.bodyBlock.statements, Seq(PyContinueStatement, PyBreakStatement))
  }

  test("parses if/elif/else, for, try, function, and class structures") {
    assert(parseOne("""if ready:
      |    print('go')
      |elif waiting:
      |    print('wait')
      |else:
      |    print('stop')
      |""".stripMargin).isInstanceOf[PyIfStatement])

    assert(parseOne("""for item in items:
      |    print(item)
      |""".stripMargin).isInstanceOf[PyForStatement])

    assert(parseOne("""try:
      |    run()
      |except Error as error:
      |    handle(error)
      |finally:
      |    cleanup()
      |""".stripMargin).isInstanceOf[PyTryStatement])

    assert(parseOne("""def greet(name: str):
      |    return print(name)
      |""".stripMargin).isInstanceOf[PyFunctionDef])

    assert(parseOne("""class Greeter:
      |    def greet(self):
      |        print('hello')
      |""".stripMargin).isInstanceOf[PyClassDef])
  }
}
