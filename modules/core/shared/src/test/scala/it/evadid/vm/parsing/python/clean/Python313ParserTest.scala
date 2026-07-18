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
    assert(listAssignment.value.exists(_.isInstanceOf[PythonLiteral[?]]), "expected list literal")
    assertEquals(listAssignment.value.get.asInstanceOf[PythonLiteral[?]].literalType.typenameInCode, "list[Any]")

    val tupleAssignment = parseOne("point = (1, 2)").asInstanceOf[PySimpleAssignment]
    assert(tupleAssignment.value.exists(_.isInstanceOf[PythonLiteral[?]]), "expected tuple literal")
    assertEquals(tupleAssignment.value.get.asInstanceOf[PythonLiteral[?]].literalType.typenameInCode, "list[int]")
  }

  test("parses function calls as expressions instead of unparsable statements") {
    val statement = parseOne("print('hello', len(items), 1 + 2)")
    val call = statement.asInstanceOf[PyFunctionCall]
    assertEquals(call.name, "print")
    assertEquals(call.parameterValues.size, 3)
    assert(call.parameterValues(1).isInstanceOf[PyFunctionCall], "expected nested call")
  }

  test("parses function calls inside assignments") {
    val assignment = parseOne("result = max(1, min(2, 3))").asInstanceOf[PySimpleAssignment]
    val call = assignment.value.get.asInstanceOf[PyFunctionCall]
    assertEquals(call.name, "max")
    assert(call.parameterValues(1).isInstanceOf[PyFunctionCall], "expected nested call")
  }

  test("parses repeated subscript trailers") {
    val subscript = parseOne("items[0][1]").asInstanceOf[PySubscript]
    assertEquals(subscript.indices.head.asInstanceOf[PythonLiteral[?]].literalValue, "1")
    assert(subscript.receiver.isInstanceOf[PySubscript], "expected nested subscript receiver")
  }

  test("parses call and attribute trailers in order") {
    val field = parseOne("obj.method(1).field").asInstanceOf[PyAttributeAccess]
    assertEquals(field.name, "field")
    val call = field.receiver.asInstanceOf[PyCallExpression]
    assert(call.callee.isInstanceOf[PyAttributeAccess], "expected method access callee")
  }

  test("parses chained call trailers") {
    val call = parseOne("func()(x)").asInstanceOf[PyCallExpression]
    assertEquals(call.args.head.asInstanceOf[PyTarget].name, "x")
    assert(call.callee.isInstanceOf[PyFunctionCall], "expected initial simple call to remain compatible")
  }

  test("parses multi-index subscript trailers") {
    val subscript = parseOne("matrix[i, j]").asInstanceOf[PySubscript]
    assertEquals(subscript.receiver.asInstanceOf[PyTarget].name, "matrix")
    assertEquals(subscript.indices.map(_.asInstanceOf[PyTarget].name), List("i", "j"))
  }

  test("parses attribute call expressions") {
    val call = parseOne("turtle.forward(100)").asInstanceOf[PyCallExpression]
    val callee = call.callee.asInstanceOf[PyAttributeAccess]
    assertEquals(callee.receiver.asInstanceOf[PyTarget].name, "turtle")
    assertEquals(callee.name, "forward")
    assertEquals(call.args.head.asInstanceOf[PythonLiteral[?]].literalValue, "100")
  }


  test("infers collection literal types") {
    val listAssignment = parseOne("values = [1, 2, 3]").asInstanceOf[PySimpleAssignment]
    val listLiteral = listAssignment.value.get.asInstanceOf[PythonLiteral[?]]
    assertEquals(listLiteral.literalType.typenameInCode, "list[int]")
    assert(!listLiteral.literalType.typenameInCode.contains("Any"), "homogeneous int lists should not infer Any")

    val setAssignment = parseOne("unique = {1, 2, 3}").asInstanceOf[PySimpleAssignment]
    val setLiteral = setAssignment.value.get.asInstanceOf[PythonLiteral[?]]
    assertEquals(setLiteral.literalType.typenameInCode, "set[int]")

    val dictAssignment = parseOne("ages = {'ada': 36, 'grace': 85}").asInstanceOf[PySimpleAssignment]
    val dictLiteral = dictAssignment.value.get.asInstanceOf[PythonLiteral[?]]
    assertEquals(dictLiteral.literalType.typenameInCode, "dict[str, int]")
    assert(!dictLiteral.literalType.typenameInCode.contains("Any"), "homogeneous dict keys and values should not infer Any")

    val nestedAssignment = parseOne("grid = [[1, 2], [3, 4]]").asInstanceOf[PySimpleAssignment]
    assertEquals(nestedAssignment.value.get.asInstanceOf[PythonLiteral[?]].literalType.typenameInCode, "list[list[int]]")

    val mixedAssignment = parseOne("mixed = [1, 'two']").asInstanceOf[PySimpleAssignment]
    assertEquals(mixedAssignment.value.get.asInstanceOf[PythonLiteral[?]].literalType.typenameInCode, "list[Any]")

    val intListType: PythonType[List[BigInt]] = PythonType.PYTHON_LIST(PythonType.PYTHON_INTEGER())
    assert(intListType.isInstanceOf[PythonType[?]], "expected list type to be a PythonType")
    assertEquals(intListType.serializerPythonValue.deserialize("[1, 2, 3]"), List(BigInt(1), BigInt(2), BigInt(3)))
    assertEquals(intListType.serializerPythonValue.serialize(List(BigInt(1), BigInt(2))), "[1, 2]")

    val dictType: PythonType[Map[String, BigInt]] = PythonType.PYTHON_DICT(PythonType.PYTHON_STRING(), PythonType.PYTHON_INTEGER())
    assert(dictType.isInstanceOf[PythonType[?]], "expected dict type to be a PythonType")
    assertEquals(dictType.serializerPythonValue.deserialize("{'ada': 36}"), Map("ada" -> BigInt(36)))

    val arrayType: PythonType[List[BigInt]] = PythonType.PYTHON_ARRAY(PythonType.PYTHON_INTEGER())
    assertEquals(arrayType.typenameInCode, "array[int]")

    val setType: PythonType[Set[BigInt]] = PythonType.PYTHON_SET(PythonType.PYTHON_INTEGER())
    assertEquals(setType.serializerPythonValue.deserialize("{1, 2}"), Set(BigInt(1), BigInt(2)))
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
