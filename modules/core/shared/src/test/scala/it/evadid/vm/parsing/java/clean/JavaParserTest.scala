package it.evadid.vm.parsing.java.clean

import it.evadid.vm.parsing.java.clean.model.JavaAST.*
import it.evadid.vm.parsing.java.clean.model.JavaType
import munit.FunSuite

class JavaParserTest extends FunSuite {
  private def unsupportedStatements(program: JavaProgram): Set[String] =
    program.traversePreOrderWithListener {
      case statement: JavaUnparsableStatement => Some(statement.source)
      case _ => None
    }.flatten

  private def completeProgram(source: String): JavaProgram = {
    val program = JavaParser.parse(source).fold(error => fail(error.getMessage), identity)
    assertEquals(unsupportedStatements(program), Set.empty[String], clue = source)
    program
  }

  private def statement(source: String): JavaStatement = {
    val statements = completeProgram(source).statements
    assertEquals(statements.size, 1, clue = source)
    statements.head.statement
  }

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

    val program = completeProgram(source)
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
  test("parses for, while, try/catch/finally, and assignments") {
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

    val method = completeProgram(source).statements.collectFirst { case StatementWithLineNumber(c: JavaClassDef, _) => c }
      .flatMap(_.body.statements.collectFirst { case m: JavaMethodDef => m }).get

    assert(method.body.statements.exists(_.isInstanceOf[JavaForStatement]))
    assert(method.body.statements.exists(_.isInstanceOf[JavaWhileStatement]))
    assert(method.body.statements.exists(_.isInstanceOf[JavaTryStatement]))
  }

  test("parses binary and augmented assignment operators") {
    for op <- Seq("+", "-", "*", "/", "%", "<", ">", "<=", ">=", "==", "!=", "&&", "||") do
      assertEquals(statement(s"a $op b;"), JavaOperationBinary(JavaTarget("a"), op, JavaTarget("b")))
    for op <- Seq("+=", "-=", "*=", "/=", "%=") do
      assertEquals(statement(s"a $op b;"), JavaAugAssignment(JavaTarget("a"), op, JavaTarget("b")))
  }

  test("preserves operator precedence, parentheses and associativity") {
    val a = JavaTarget("a")
    val b = JavaTarget("b")
    val c = JavaTarget("c")
    assertEquals(statement("a + b * c;"), JavaOperationBinary(a, "+", JavaOperationBinary(b, "*", c)))
    assertEquals(statement("(a + b) * c;"), JavaOperationBinary(JavaOperationBinary(a, "+", b), "*", c))
    assertEquals(statement("a - b - c;"), JavaOperationBinary(JavaOperationBinary(a, "-", b), "-", c))
    assertEquals(statement("a / b / c;"), JavaOperationBinary(JavaOperationBinary(a, "/", b), "/", c))
    assertEquals(statement("a || b && c;"), JavaOperationBinary(a, "||", JavaOperationBinary(b, "&&", c)))
    assertEquals(statement("a = b = c;"), JavaAssignment(a, JavaAssignmentExpression(b, "=", c)))
    assertEquals(statement("a = b == c;"), JavaAssignment(a, JavaOperationBinary(b, "==", c)))
  }

  test("parses division inside call arguments") {
    statement("forward(5 / 2);") match {
      case JavaFunctionCall(JavaTarget("forward", _, _), Seq(JavaOperationBinary(JavaLiteral("5", leftType), "/", JavaLiteral("2", rightType)))) =>
        assertEquals(leftType.typenameInCode, "int")
        assertEquals(rightType.typenameInCode, "int")
      case other => fail(s"Expected a call with integer division, got $other")
    }
  }

  test("parses consecutive unary operators separated by whitespace or comments") {
    val i = JavaTarget("i")
    for source <- Seq("+ +i;", "+/* separate tokens */+i;") do
      assertEquals(statement(source), JavaOperationUnary("+", JavaOperationUnary("+", i)))
    assertEquals(statement("- -i;"), JavaOperationUnary("-", JavaOperationUnary("-", i)))
    assertEquals(statement("!!ready;"), JavaOperationUnary("!", JavaOperationUnary("!", JavaTarget("ready"))))
    assertEquals(statement("a+-b;"), JavaOperationBinary(JavaTarget("a"), "+", JavaOperationUnary("-", JavaTarget("b"))))
  }

  test("does not split unsupported operators into supported prefixes") {
    val sources = Seq(
      "++i;", "--i;", "i++;", "i--;", "a+++b;", "a---b;",
      "a << b;", "a >> b;", "a >>> b;", "a <<= b;", "a >>= b;", "a >>>= b;", "a -> b;",
      "class Example { void run() { ++i; } }",
      "for (int i = 0; i < 4; i++) { forward(i); }"
    )
    for source <- sources do
      val parsed = JavaParser.parse(source)
      assert(parsed.isLeft || unsupportedStatements(parsed.toOption.get).nonEmpty, clue = source)
  }

  test("parses a complete parameterized square program") {
    val source =
      """class SquareProgram {
        |  static void square(int sideLength) {
        |    for (int i = 0; i < 4; i = i + 1) {
        |      Turtle.forward(sideLength);
        |      Turtle.turnRight(90);
        |    }
        |  }
        |  public static void main(String[] args) {
        |    square(40);
        |  }
        |}
        |""".stripMargin
    val program = completeProgram(source)
    assertEquals(program.statements.size, 1)
    val clazz = program.statements.head.statement.asInstanceOf[JavaClassDef]
    assertEquals(clazz.name, "SquareProgram")
    assertEquals(clazz.body.statements.size, 2)
    val square = clazz.body.statements.head.asInstanceOf[JavaMethodDef]
    assertEquals(square.name, "square")
    assertEquals(square.modifiers, Seq("static"))
    assertEquals(square.returnType.map(_.typenameInCode), Some("void"))
    assertEquals(square.parameters.map(p => (p.name, p.javaType.typenameInCode)), Seq(("sideLength", "int")))
    assertEquals(square.body.statements.size, 1)
    val loop = square.body.statements.head.asInstanceOf[JavaForStatement]
    val init = loop.init.head.asInstanceOf[JavaVariableDeclaration]
    assertEquals(init.name, "i")
    assertEquals(init.javaType.typenameInCode, "int")
    assertEquals(init.value.map(_.asInstanceOf[JavaLiteral[?]].literalValue), Some("0"))
    loop.condition.get match {
      case JavaOperationBinary(JavaTarget("i", _, _), "<", JavaLiteral("4", _)) => ()
      case other => fail(s"Unexpected loop condition: $other")
    }
    loop.update match {
      case Seq(JavaAssignmentExpression(JavaTarget("i", _, _), "=", JavaOperationBinary(JavaTarget("i", _, _), "+", JavaLiteral("1", _)))) => ()
      case other => fail(s"Unexpected loop update: $other")
    }
    loop.bodyBlock.statements match {
      case Seq(
            JavaCallExpression(JavaAttributeAccess(JavaTarget("Turtle", _, _), "forward"), Seq(JavaTarget("sideLength", _, _))),
            JavaCallExpression(JavaAttributeAccess(JavaTarget("Turtle", _, _), "turnRight"), Seq(JavaLiteral("90", _)))
          ) => ()
      case other => fail(s"Unexpected turtle calls: $other")
    }
    val main = clazz.body.statements(1).asInstanceOf[JavaMethodDef]
    assertEquals(main.name, "main")
    assertEquals(main.modifiers, Seq("public", "static"))
    assertEquals(main.returnType.map(_.typenameInCode), Some("void"))
    assertEquals(main.parameters.map(p => (p.name, p.javaType.typenameInCode)), Seq(("args", "String[]")))
    main.body.statements match {
      case Seq(JavaFunctionCall(JavaTarget("square", _, _), Seq(JavaLiteral("40", _)))) => ()
      case other => fail(s"Unexpected main call: $other")
    }
  }

  test("parses a square with a while loop and explicit counter assignment") {
    val source =
      """class SquareProgram {
        |  static void square(int sideLength) {
        |    int i = 0;
        |    while (i < 4) {
        |      Turtle.forward(sideLength);
        |      Turtle.turnRight(90);
        |      i = i + 1;
        |    }
        |  }
        |  public static void main(String[] args) { square(40); }
        |}
        |""".stripMargin
    val clazz = completeProgram(source).statements.head.statement.asInstanceOf[JavaClassDef]
    val square = clazz.body.statements.head.asInstanceOf[JavaMethodDef]
    val loop = square.body.statements(1).asInstanceOf[JavaWhileStatement]
    loop.condition match {
      case JavaOperationBinary(JavaTarget("i", _, _), "<", JavaLiteral("4", _)) => ()
      case other => fail(s"Unexpected while condition: $other")
    }
    loop.bodyBlock.statements match {
      case Seq(
            JavaCallExpression(JavaAttributeAccess(JavaTarget("Turtle", _, _), "forward"), Seq(JavaTarget("sideLength", _, _))),
            JavaCallExpression(JavaAttributeAccess(JavaTarget("Turtle", _, _), "turnRight"), Seq(JavaLiteral("90", _))),
            JavaAssignment(JavaTarget("i", _, _), JavaOperationBinary(JavaTarget("i", _, _), "+", JavaLiteral("1", _)))
          ) => ()
      case other => fail(s"Unexpected while body: $other")
    }
  }

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
