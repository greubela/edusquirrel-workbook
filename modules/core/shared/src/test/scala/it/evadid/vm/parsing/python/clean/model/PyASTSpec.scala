package it.evadid.vm.parsing.python.clean.model

import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.parsing.python.clean.model.PythonType.*
import munit.FunSuite
import upickle.default.{read, write}

class PyASTSpec extends FunSuite {
  private val int = PYTHON_INTEGER
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
    PySimpleAssignment(PyTarget("x", Nil, None, Some(PYTHON_FLOAT)), Some(number("2"))),
    PyAugAssignment(name("x"), "+=", number("1")),
    PyAttributeAccess(name("obj"), "field"),
    PySubscript(name("items"), List(number("0"))),
    PyCallExpression(PyAttributeAccess(name("obj"), "method"), List(PythonLiteral("\"a\"", PYTHON_STRING))),
    PyFunctionDef("area", List(PySimpleAssignment(name("w"), None)), block, isAsync = false, Some(PYTHON_FLOAT), List("cache"), "def area(w): pass"),
    PyClassDef("Box", Nil, block, isAsync = false, "class Box: pass"),
    PyOperationBinary(name("a"), "+", number("2")),
    PyOperationUnary("not", name("ready")),
    PyTarget("field", List("self"), Some(number("0")), Some(PYTHON_LIST(PYTHON_STRING))),
    PythonLiteral("[1, 2]", PYTHON_LIST(int)),
    PYTHON_DICT(PYTHON_STRING, PYTHON_BOOL))

  private val program = PyProgram(statements.zipWithIndex.map((statement, line) => StatementWithLineNumber(statement, line)))

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
    assertEquals(PYTHON_DICT(PYTHON_STRING, PYTHON_LIST(int)).typeStringInPython, "dict[str, list[int]]")
    assertEquals(PYTHON_OPTIONAL(int).typeStringInPython, "int|None")
    assertEquals(PYTHON_UNION_TYPE(int, PYTHON_STRING).typeStringInPython, "int|str")
    assertEquals(PYTHON_SET(PYTHON_BOOL).typeStringInPython, "set[bool]")
    assertEquals(PYTHON_ARRAY(PYTHON_FLOAT).typeStringInPython, "array[float]")
    assertEquals(PYTHON_INTEGER_HEX.createLiteralUnsafe(" 0XFF ").literalValue, "0xff")
    assertEquals(PYTHON_INTEGER_BIN.createLiteralUnsafe("-0b101").literalValue, "-0b101")
    assertEquals(PYTHON_INTEGER_OCT.createLiteralUnsafe("0o17").literalValue, "0o17")
    assertEquals(PYTHON_BOOL.createLiteralUnsafe("true").literalValue, "True")
    assertEquals(PYTHON_NONE.createLiteralUnsafe("None").literalValue, "None")
    assertEquals(PYTHON_LIST(int).createLiteralUnsafe("[1,2 , 3]").literalValue, "[1, 2, 3]")
    assertEquals(PYTHON_SET(int).serializerPythonValue.deserialize("{1, 2, 2}"), Set(BigInt(1), BigInt(2)))
    assertEquals(PYTHON_DICT(PYTHON_STRING, int).serializerPythonValue.deserialize("{'a': 1, \"b:c\": 2}"),
      Map("a" -> BigInt(1), "b:c" -> BigInt(2)))
    assertEquals(PYTHON_OPTIONAL(int).serializerPythonValue.deserialize("None"), None)
    assertEquals(PYTHON_OPTIONAL(int).serializerScalaValue.serialize(Some(BigInt(3))), "Some(3)")
    val union = PYTHON_UNION_TYPE(int, PYTHON_STRING)
    assertEquals(union.serializerPythonValue.deserialize("'x'"), Right("x"))
    assertEquals(union.serializerScalaValue.serialize(Left(BigInt(1))), "Left(1)")
    assertEquals(PYTHON_UNPARSABLE_TYPE("Turtle").createLiteralUnsafe("t").literalValue, "t")
  }
}
