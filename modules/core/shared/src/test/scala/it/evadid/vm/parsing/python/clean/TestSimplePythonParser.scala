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

  test("parses required typed and untyped function parameters with optional defaults") {
    val function = parseOne(
      "def draw(length: float, count, enabled: bool = True, size: int = 3) -> None:\n    return"
    ).asInstanceOf[PyFunctionDef]
    assertEquals(function.name, "draw")
    assertEquals(function.parameters.map(_.target.name), List("length", "count", "enabled", "size"))
    assertEquals(function.parameters.map(_.target.typeHint.map(_.typenameInCode)), List(Some("float"), None, Some("bool"), Some("int")))
    assertEquals(function.parameters.take(2).map(_.value), List(None, None))
    assertEquals(function.parameters.drop(2).flatMap(_.value).map(_.asInstanceOf[PythonLiteral[?]].literalValue), List("True", "3"))
    assertEquals(function.block.statements, Seq(PyReturnStatement(None)))
    val main = parseOne("def main() -> None:\n    pass").asInstanceOf[PyFunctionDef]
    assertEquals(main.parameters, List.empty[PyAssignment])
  }

  test("parses grouped method calls and comparisons inside conditions") {
    val grouped = parseOne("(java_turtle.compare(\"eq\", n[0], 0.0, True))").asInstanceOf[PyCallExpression]
    assertEquals(grouped.callee, PyAttributeAccess(PyTarget("java_turtle"), "compare"))
    assertEquals(grouped.args.size, 4)
    val condition = parseOne("if (java_turtle.compare(\"eq\", n[0], 0.0, True) == False):\n    pass")
      .asInstanceOf[PyIfStatement].condition.asInstanceOf[PyOperationBinary]
    assertEquals(condition.op, "==")
    assert(condition.left.isInstanceOf[PyCallExpression])
    assertEquals(condition.right.asInstanceOf[PythonLiteral[?]].literalValue, "False")
    val nested = parseOne("while ((ready()) and (value() > 0)):\n    pass").asInstanceOf[PyWhileStatement]
    assertEquals(nested.condition.asInstanceOf[PyOperationBinary].op, "and")
  }

  test("distinguishes grouped calls from tuples containing calls") {
    assert(parseOne("(f(1))").isInstanceOf[PyFunctionCall])
    for source <- Seq("(f(1),)", "(f(1), g(2))", "((f(1)), g(2),)") do {
      val tuple = parseOne(source).asInstanceOf[PythonLiteral[?]]
      assertEquals(tuple.literalType.typenameInCode, "list[Any]", clue = source)
    }
    val sum = parseOne("(f(1)+g(2))*3").asInstanceOf[PyOperationBinary]
    assertEquals(sum.op, "*")
    assertEquals(sum.left.asInstanceOf[PyOperationBinary].op, "+")
  }

  test("recognizes boolean and None literals before identifier expressions") {
    for (source, kind) <- Seq("True" -> "bool", "False" -> "bool", "None" -> "None") do
      assertEquals(parseOne(source).asInstanceOf[PythonLiteral[?]].literalType.typenameInCode, kind)
    for name <- Seq("TrueValue", "FalseValue", "NoneValue") do
      assertEquals(parseOne(name), PyTarget(name))
  }

}
