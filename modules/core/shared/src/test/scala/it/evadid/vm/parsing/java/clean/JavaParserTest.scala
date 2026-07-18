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
}
