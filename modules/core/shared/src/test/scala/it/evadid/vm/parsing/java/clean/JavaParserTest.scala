package it.evadid.vm.parsing.java.clean

import it.evadid.vm.parsing.java.clean.JavaAST.*
import munit.FunSuite

class JavaParserTest extends FunSuite {
  test("Java AST nodes expose child nodes for traversal") {
    val target = JavaTarget("values", sliceExpr = Some(JavaLiteral("0", JavaType.JAVA_INTEGER())))
    val assignment = JavaAssignment(
      target,
      JavaOperationBinary(JavaLiteral("1", JavaType.JAVA_INTEGER()), "+", JavaLiteral("2", JavaType.JAVA_INTEGER()))
    )
    assertEquals(assignment.getChildren(), Seq(target, assignment.value), "assignment children")

    val method = JavaMethodDef(
      name = "run",
      modifiers = Seq("public"),
      returnType = Some(JavaType.JAVA_UNPARSABLE_TYPE("void")),
      parameters = Seq(JavaVariableDeclaration("count", JavaType.JAVA_INTEGER(), None)),
      body = JavaExecutionBlock(Seq(assignment))
    )
    assertEquals(method.getChildren(), method.parameters ++ Seq(method.body), "method children")

    val tryStatement = JavaTryStatement(
      body = JavaExecutionBlock(Seq(JavaFunctionCall(JavaTarget("risky"), Seq.empty))),
      catches = Seq(
        JavaCatchClause(
          JavaVariableDeclaration("ex", JavaType.JAVA_UNPARSABLE_TYPE("Exception"), None),
          JavaExecutionBlock(Seq(JavaThrowStatement(JavaTarget("ex"))))
        )
      ),
      finallyBlock = Some(JavaExecutionBlock(Seq(JavaFunctionCall(JavaTarget("cleanup"), Seq.empty))))
    )
    assertEquals(
      tryStatement.getChildren(),
      Seq(tryStatement.body) ++ tryStatement.catches ++ tryStatement.finallyBlock.toList,
      "try children"
    )
  }

  test("parses Java programs into clean AST statements") {
    val source =
      """
        |package demo.workbook;
        |import java.util.List;
        |
        |public class TurtleProgram extends BaseProgram implements Runnable {
        |  private int x = 50;
        |
        |  public void run() {
        |    if (x > 10) {
        |      turtle.forward(x + 5);
        |    } else {
        |      x = 0;
        |    }
        |  }
        |}
        |""".stripMargin

    val parsed = JavaParser.parse(source)
    assert(parsed.isRight, parsed.left.toOption.getOrElse(""))

    val program = parsed.toOption.get
    assert(program.statements.collect { case StatementWithLineNumber(_: JavaPackageStatement, _) => 1 }.size == 1)
    assert(program.statements.collect { case StatementWithLineNumber(_: JavaImportStatement, _) => 1 }.size == 1)

    val clazz = program.statements.collectFirst { case StatementWithLineNumber(c: JavaClassDef, _) => c }.get
    assert(clazz.name == "TurtleProgram")
    assert(clazz.extendsType.map(_.typenameInCode).contains("BaseProgram"))
    assert(clazz.implementsTypes.map(_.typenameInCode) == Seq("Runnable"))
    assert(clazz.body.statements.collect { case _: JavaVariableDeclaration => 1 }.size == 1)

    val method = clazz.body.statements.collectFirst { case m: JavaMethodDef => m }.get
    assert(method.name == "run")
    assert(method.body.statements.exists(_.isInstanceOf[JavaIfStatement]))
  }
  test("parses Java list and array types") {
    val parsed = JavaParser.parse("List<String> names; int[] scores;")
    assert(parsed.isRight, parsed.left.toOption.getOrElse(""))

    val declarations = parsed.toOption.get.statements.map(_.statement).collect { case declaration: JavaVariableDeclaration => declaration }
    assertEquals(declarations.map(_.javaType.typenameInCode), Seq("List<String>", "int[]"))
    assert(declarations.head.javaType.isInstanceOf[JavaType[?]], "expected List type to be a JavaType")
  }
  test("parses for, while, try/catch/finally, and assignments".ignore) {
    val source =
      """
        |class Example {
        |  void run() {
        |    for (int i = 0; i < 3; i = i + 1) {
        |      total += i;
        |    }
        |    while (total < 10) {
        |      total = total + 1;
        |    }
        |    try {
        |      risky();
        |    } catch (Exception ex) {
        |      throw ex;
        |    } finally {
        |      cleanup();
        |    }
        |  }
        |}
        |""".stripMargin

  test("parses chained Java call, attribute, and subscript trailers") {
    val parsed = JavaParser.parse("factory().create(1).items[0];")
    assert(parsed.isRight, parsed.left.toOption.getOrElse(""))

    val expression = parsed.toOption.get.statements.head.statement.asInstanceOf[JavaExpression]
    val subscript = expression.asInstanceOf[JavaSubscript]
    assertEquals(subscript.indices.head.asInstanceOf[JavaLiteral[?]].literalValue, "0")

    val itemsAccess = subscript.receiver.asInstanceOf[JavaAttributeAccess]
    assertEquals(itemsAccess.name, "items")

    val createCall = itemsAccess.receiver.asInstanceOf[JavaCallExpression]
    assertEquals(createCall.arguments.head.asInstanceOf[JavaLiteral[?]].literalValue, "1")
    assert(createCall.callee.isInstanceOf[JavaAttributeAccess], "expected create call callee to be an attribute access")

    val factoryCall = createCall.callee.asInstanceOf[JavaAttributeAccess].receiver.asInstanceOf[JavaFunctionCall]
    assertEquals(factoryCall.name.name, "factory")
  }

  test("serializes Java list and array types") {
    val listType = JavaType.JAVA_LIST(JavaType.JAVA_INTEGER())
    assertEquals(listType.serializerJavaValue.serialize(List(BigInt(1), BigInt(2))), "List.of(1, 2)")
    assertEquals(listType.serializerJavaValue.deserialize("List.of(1, 2)"), List(BigInt(1), BigInt(2)))

    val arrayType = JavaType.JAVA_ARRAY(JavaType.JAVA_INTEGER())
    assertEquals(arrayType.serializerJavaValue.serialize(List(BigInt(1), BigInt(2))), "{1, 2}")
    assertEquals(arrayType.serializerJavaValue.deserialize("{1, 2}"), List(BigInt(1), BigInt(2)))
  }

}
