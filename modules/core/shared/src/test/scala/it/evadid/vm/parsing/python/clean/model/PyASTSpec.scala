package it.evadid.vm.parsing.python.clean.model

import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.parsing.python.clean.model.PythonType.*
import munit.FunSuite
import upickle.default.{read, write}

class PyASTSpec extends FunSuite {
  private val int = new PYTHON_INTEGER
  private def number(value: String): PyExpression = PythonLiteral(value, int)
  private def name(value: String): PyTarget = PyTarget(value)
  private val block = PyExecutionBlock(List(PyPassStatement))

  private val statements: List[PyStatement] = List(
    PyUnparsableStatement("match x:"),
    PyPassStatement, PyBreakStatement, PyContinueStatement, PyEmptyStatement,
    PyCommentStatement("note"),
    PyRawExpression("lambda: 1"),
    PyPlainImportStatement("turtle"),
    PyImportFromStatement("math", List(name("sin")), importAll = false),
    PyRaiseStatement(Some(PyFunctionCall(name("ValueError"), Nil)), Some(name("cause"))),
    PyReturnStatement(Some(number("1"))),
    PyIfStatement(name("ready"), block, PyAST.passBlock),
    PyWhileStatement(name("running"), block, Some(block)),
    PyForStatement(name("i"), PyFunctionCall(name("range"), List(number("3"))), block, None, isAsync = true),
    PyTryStatement(block, List(
      PyExceptClauseBasic(Some(name("ValueError")), Some("err"), block),
      PyExceptClauseStar(name("TypeError"), None, block),
      PyExceptClauseFinally(block)), Some(block), "try: pass"),
    NamedExpression("count", PyFunctionCall(name("len"), List(name("items")))),
    PyTypeDefExpression(),
    PySimpleAssignment(PyTarget("x", Nil, None, Some(new PYTHON_FLOAT)), Some(number("2"))),
    PyAugAssignment(name("x"), "+=", number("1")),
    PyAttributeAccess(name("obj"), "field"),
    PySubscript(name("items"), List(number("0"))),
    PyCallExpression(PyAttributeAccess(name("obj"), "method"), List(PythonLiteral("\"a\"", new PYTHON_STRING))),
    PyFunctionDef("area", List(PySimpleAssignment(name("w"), None)), block, isAsync = false, Some(new PYTHON_FLOAT), List("cache"), "def area(w): pass"),
    PyClassDef("Box", Nil, block, isAsync = false, "class Box: pass"),
    PyOperationBinary(name("a"), "+", number("2")),
    PyOperationUnary("not", name("ready")),
    PyTarget("field", List("self"), Some(number("0")), Some(new PYTHON_LIST(new PYTHON_STRING))),
    PythonLiteral("[1, 2]", new PYTHON_LIST(int)),
    new PYTHON_DICT(new PYTHON_STRING, new PYTHON_BOOL))

  private val program = PyProgram(statements.zipWithIndex.map((statement, line) => StatementWithLineNumber(statement, line)))

  test("every Python syntax node survives the tagged JSON codec with its node kind") {
    (program :: program.statements.head :: block :: statements).foreach { node =>
      val json = write[PyAST](node)
      val restored = read[PyAST](json)
      assertEquals(restored.getClass, node.getClass, json)
      assertEquals(write[PyAST](restored), json)
    }
  }

  test("narrowed codecs accept matching nodes and reject nodes of another category") {
    val statementJson = write[PyAST](PyPassStatement)
    assertEquals(read[PyStatement](statementJson), PyPassStatement)
    val expressionJson = write[PyAST](PyOperationUnary("-", number("1")))
    assert(read[PyExpression](expressionJson).isInstanceOf[PyOperationUnary])
    assert(read[PyAtomar](write[PyAST](name("x"))).isInstanceOf[PyTarget])
    intercept[Exception](read[PyExpression](statementJson))
    intercept[Exception](read[PyAtomar](expressionJson))
    intercept[Exception](read[PyAST](statementJson.replace("PyPassStatement", "PyGotoStatement")))
  }

  test("children expose the nested syntax for traversal") {
    val call = PyFunctionCall(PyTarget("forward", List("turtle")), List(number("10")))
    assertEquals(call.name, "turtle.forward")
    assertEquals(call.getChildren(), List(number("10"), PyTarget("forward", List("turtle"))))
    val loop = PyForStatement(name("i"), name("items"), block, Some(block), isAsync = false)
    assertEquals(loop.getChildren(), List(name("i"), name("items"), block, block))
    assertEquals(PyRaiseStatement(None, None).getChildren(), Nil)
    assertEquals(PyReturnStatement(None).getChildren(), Nil)
    assertEquals(PySubscript(name("m"), List(number("1"), number("2"))).getChildren().size, 3)
    assertEquals(PyImportFromStatement("math", List(name("pi")), importAll = false).name, "math")
    assertEquals(PyOperationBinary(name("a"), "and", name("b")).name, "and")
    assertEquals(block.withAdded(List(PyBreakStatement)).statements, List(PyPassStatement, PyBreakStatement))
    val names = PyProgram(List(StatementWithLineNumber(PyFunctionDef("draw", Nil, PyExecutionBlock(List(call)), isAsync = false), 0)))
      .allNamedElements().keySet
    assertEquals(names, Set("draw", "turtle.forward"))
  }

  test("Python types describe themselves and normalise literals through their serializers") {
    assertEquals(new PYTHON_DICT(new PYTHON_STRING, new PYTHON_LIST(int)).typeStringInPython, "dict[str, list[int]]")
    assertEquals(new PYTHON_OPTIONAL(int).typeStringInPython, "int|None")
    assertEquals(PYTHON_UNION_TYPE(int, new PYTHON_STRING).typeStringInPython, "int|str")
    assertEquals(new PYTHON_SET(new PYTHON_BOOL).typeStringInPython, "set[bool]")
    assertEquals(new PYTHON_ARRAY(new PYTHON_FLOAT).typeStringInPython, "array[float]")
    assertEquals(new PYTHON_INTEGER_HEX().createLiteralUnsafe(" 0XFF ").literalValue, "0xff")
    assertEquals(new PYTHON_INTEGER_BIN().createLiteralUnsafe("-0b101").literalValue, "-0b101")
    assertEquals(new PYTHON_INTEGER_OCT().createLiteralUnsafe("0o17").literalValue, "0o17")
    assertEquals(new PYTHON_BOOL().createLiteralUnsafe("true").literalValue, "True")
    assertEquals(new PYTHON_NONE().createLiteralUnsafe("None").literalValue, "None")
    assertEquals(new PYTHON_LIST(int).createLiteralUnsafe("[1,2 , 3]").literalValue, "[1, 2, 3]")
    assertEquals(new PYTHON_SET(int).serializerPythonValue.deserialize("{1, 2, 2}"), Set(BigInt(1), BigInt(2)))
    assertEquals(new PYTHON_DICT(new PYTHON_STRING, int).serializerPythonValue.deserialize("{'a': 1, \"b:c\": 2}"),
      Map("a" -> BigInt(1), "b:c" -> BigInt(2)))
    assertEquals(new PYTHON_OPTIONAL(int).serializerPythonValue.deserialize("None"), None)
    assertEquals(new PYTHON_OPTIONAL(int).serializerScalaValue.serialize(Some(BigInt(3))), "Some(3)")
    val union = PYTHON_UNION_TYPE(int, new PYTHON_STRING)
    assertEquals(union.serializerPythonValue.deserialize("'x'"), Right("x"))
    assertEquals(union.serializerScalaValue.serialize(Left(BigInt(1))), "Left(1)")
    assertEquals(new PYTHON_UNPARSABLE_TYPE("Turtle").createLiteralUnsafe("t").literalValue, "t")
  }

  test("Python type codecs round-trip nested descriptors and reject malformed ones") {
    val types: List[PythonType[?]] = List(int, new PYTHON_INTEGER_HEX, new PYTHON_INTEGER_OCT, new PYTHON_INTEGER_BIN, new PYTHON_FLOAT,
      new PYTHON_STRING, new PYTHON_BOOL, new PYTHON_NONE, new PYTHON_ANY, new PYTHON_ARRAY(int), new PYTHON_SET(int),
      new PYTHON_OPTIONAL(new PYTHON_STRING), new PYTHON_DICT(new PYTHON_STRING, new PYTHON_LIST(int)),
      PYTHON_UNION_TYPE(int, new PYTHON_NONE), new PYTHON_UNPARSABLE_TYPE("Turtle"))
    types.foreach { value =>
      val json = write[PythonType[Any]](value.asInstanceOf[PythonType[Any]])
      val restored = read[PythonType[Any]](json)
      assertEquals(restored.typeStringInPython, value.typeStringInPython)
      assertEquals(restored.getClass, value.getClass)
    }
    val listJson = write[PythonType[Any]](new PYTHON_LIST(int).asInstanceOf[PythonType[Any]])
    intercept[Exception](read[PythonType[Any]](listJson.replace("\"list\"", "\"dict\"")))
    intercept[Exception](read[PythonType[Any]](listJson.replace("\"list\"", "\"tuple\"")))
  }
}
