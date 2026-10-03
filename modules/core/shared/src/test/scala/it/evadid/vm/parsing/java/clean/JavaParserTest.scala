package it.evadid.vm.parsing.java.clean

import it.evadid.vm.parsing.java.clean.model.JavaAST.*
import it.evadid.vm.parsing.java.clean.model.JavaType
import it.evadid.vm.parsing.java.turtle.{JavaTurtleSemantics, JavaTurtleSource, JavaTurtleStructure}
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

  private def turtleSource(body: String): String =
    s"""class SquareProgram {
       |  static void square(int sideLength) {
       |    $body
       |  }
       |  public static void main(String[] args) { square(40); }
       |}
       |""".stripMargin

  private def sourceProblem(source: String): JavaTurtleSource.Diagnostic =
    JavaTurtleSource.parse(source).swap.fold(_ => fail(s"Unexpectedly accepted: $source"), identity)

  private def structuredSource(source: String): JavaTurtleStructure.StructuredSource = {
    val parsed = JavaTurtleSource.parse(source).fold(problem => fail(problem.message), identity)
    JavaTurtleStructure.check(parsed).fold(problem => fail(problem.message), identity)
  }

  private def structureProblem(source: String): JavaTurtleSource.Diagnostic = {
    val parsed = JavaTurtleSource.parse(source).fold(problem => fail(problem.message), identity)
    JavaTurtleStructure.check(parsed).swap.fold(_ => fail(s"Unexpectedly accepted structure: $source"), identity)
  }

  private def turtleClass(members: String, header: String = "class Drawing"): String =
    s"$header { $members }"

  private val mainMethod = "public static void main(String[] args) {}"

  private def sourceStatement(source: String): JavaStatement = {
    val parsed = JavaTurtleSource.parse(source).fold(problem => fail(problem.message), identity)
    assertEquals(parsed.program.statements.size, 1, clue = source)
    parsed.program.statements.head.statement
  }

  private def checkedSource(source: String): JavaTurtleSemantics.TypedSource =
    JavaTurtleSemantics.check(structuredSource(source)).fold(problem => fail(s"${problem.message}\n$source"), identity)

  private def semanticProblem(source: String): JavaTurtleSource.Diagnostic =
    JavaTurtleSemantics.check(structuredSource(source)).swap.fold(_ => fail(s"Unexpectedly accepted semantics: $source"), identity)

  private def semanticSource(body: String): String =
    turtleClass(s"static void run(int distance, boolean flag) { $body } $mainMethod")

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

  test("turtle source preserves the original source and complete square AST") {
    for loop <- Seq(
      "for (int i = 0; i < 4; i = i + 1) { Turtle.forward(sideLength); Turtle.turnRight(90); }",
      "int i = 0; while (i < 4) { Turtle.forward(sideLength); Turtle.turnRight(90); i = i + 1; }"
    ) do {
      val source = "\r\n\r\n" + turtleSource(loop)
      val parsed = JavaTurtleSource.parse(source).fold(problem => fail(problem.message), identity)
      assertEquals(parsed.source, source)
      assertEquals(unsupportedStatements(parsed.program), Set.empty[String])
    }
  }

  test("turtle source skips comment contents without losing token boundaries") {
    val body =
      "int longueur = 40; int floatCount = 0; int intensity = 1; " +
        "/* long double 010 'quoted' */ Turtle.forward(longueur); // byte short\r\n"
    assert(JavaTurtleSource.parse(turtleSource(body) + "// comment at EOF").isRight)
  }

  test("turtle source rejects unterminated block comments") {
    for source <- Seq("/*", turtleSource("Turtle.forward(40);") + "/* unfinished", turtleSource("/* unfinished")) do {
      val problem = sourceProblem(source)
      assertEquals(problem.problem, JavaTurtleSource.Problem.UnclosedComment)
      assertEquals(problem.range, Some(JavaTurtleSource.SourceRange(source.indexOf("/*"), source.length)))
    }
  }

  test("turtle source rejects numeric types before the legacy AST erases them") {
    for
      name <- Seq("byte", "short", "long", "float", "double")
      prefix <- Seq("", "/* 😀 */\r\n", "/* // byte /* float */ ")
    do {
      val source = turtleSource(s"$prefix$name value = 1; Turtle.forward(40);")
      assert(JavaParser.parse(source).isRight, clue = source)
      val problem = sourceProblem(source)
      assertEquals(problem.problem, JavaTurtleSource.Problem.UnsupportedType)
      val start = source.indexOf(s"$name value")
      assertEquals(problem.range, Some(JavaTurtleSource.SourceRange(start, start + name.length)))
    }
  }

  test("turtle source accepts decimal integers and rejects other numeric forms") {
    for number <- Seq("0", "10", "40", "100") do
      assert(JavaTurtleSource.parse(turtleSource(s"Turtle.forward($number);")).isRight)
    for number <- Seq("00", "010", "0x10", "0b10", "1_000", "1L", "1.0", ".5", "1e2") do {
      val source = turtleSource(s"Turtle.forward($number);")
      val problem = sourceProblem(source)
      assertEquals(problem.problem, JavaTurtleSource.Problem.UnsupportedNumber, clue = number)
      val start = source.indexOf(s"($number)") + 1
      assertEquals(problem.range, Some(JavaTurtleSource.SourceRange(start, start + number.length)))
    }
  }

  test("turtle source rejects quoted values without relying on permissive string parsing") {
    val invalidEscape = "\"bad" + "\\" + "q\""
    for value <- Seq("\"hello\"", "'x'", "'ab'", invalidEscape, "\"line\nbreak\"") do {
      val problem = sourceProblem(turtleSource(s"String value = $value;"))
      assertEquals(problem.problem, JavaTurtleSource.Problem.UnsupportedLiteral, clue = value)
    }
  }

  test("turtle source rejects Unicode escapes even inside comments") {
    val slash = "\\"
    for body <- Seq(s"${slash}u0069nt value = 1;", s"${slash}uu0069nt value = 1;", s"// ${slash}u000a long value = 1;") do {
      val source = turtleSource(body)
      val problem = sourceProblem(source)
      assertEquals(problem.problem, JavaTurtleSource.Problem.UnicodeEscape)
      assertEquals(problem.range.map(_.start), Some(source.indexOf(slash)))
    }
  }

  test("turtle source rejects unsupported statements nested inside methods") {
    for body <- Seq("++sideLength;", "Turtle.forward(sideLength << 1);") do {
      val source = turtleSource(body)
      val problem = sourceProblem(source)
      assertEquals(problem.problem, JavaTurtleSource.Problem.UnsupportedSyntax)
      assertEquals(problem.range, None)
    }
  }

  test("turtle source parse failures refer to the untrimmed source") {
    val source = "\r\n\r\n}"
    val problem = sourceProblem(source)
    assertEquals(problem.problem, JavaTurtleSource.Problem.ParseFailure)
    assertEquals(problem.range, Some(JavaTurtleSource.SourceRange(4, 5)))
  }

  test("legacy Java parsing remains independent of the turtle source restrictions") {
    assert(JavaParser.parse("long value = 40;").isRight)
    assert(JavaParser.parse("String value = \"hello\";").isRight)
    assert(JavaParser.parse("/* unfinished").isRight)
  }

  test("turtle structure retains the complete class and ordered method definitions") {
    val source = "\r\n/* source */" + turtleSource(
      "for (int i = 0; i < 4; i = i + 1) { Turtle.forward(sideLength); Turtle.turnRight(90); }"
    )
    val checked = structuredSource(source)
    assertEquals(checked.parsedSource.source, source)
    assertEquals(checked.classDef.name, "SquareProgram")
    assertEquals(checked.methods.map(_.name), Seq("square", "main"))
    assertEquals(checked.main, checked.methods.last)
    assertEquals(checked.classDef.body.statements, checked.methods)
  }

  test("turtle structure is independent of the pilot class and method names") {
    for header <- Seq("class Drawing", "public class Pattern") do {
      val source = turtleClass(
        "static public void main(String /* type */ [ /* rank */ ] input) {} " +
          "private static void move(int distance, boolean drawing) {} " +
          "static void record(int var) {} public static void reset() {}",
        header
      )
      val checked = structuredSource(source)
      assertEquals(checked.methods.map(_.name), Seq("main", "move", "record", "reset"))
      assertEquals(checked.main.parameters.map(_.name), Seq("input"))
    }
  }

  test("turtle structure requires one complete compilation unit class") {
    val valid = turtleClass(mainMethod)
    for source <- Seq("", ";", "Turtle.forward(40);", "static void run() {}", valid + "class Other {}",
      "package demo; " + valid, "import java.util.List; " + valid, valid + ";") do {
      val problem = structureProblem(source)
      assertEquals(problem.problem, JavaTurtleSource.Problem.UnsupportedStructure, clue = source)
      assertEquals(problem.range, None)
    }
  }

  test("turtle structure rejects unsupported class modifiers and inheritance") {
    for header <- Seq("private class Drawing", "protected class Drawing", "static class Drawing", "final class Drawing",
      "abstract class Drawing", "public public class Drawing", "class Drawing extends Base", "class Drawing implements Runnable") do
      assertEquals(structureProblem(turtleClass(mainMethod, header)).problem, JavaTurtleSource.Problem.UnsupportedStructure, clue = header)
  }

  test("turtle structure excludes fields and nested classes") {
    for member <- Seq("int width = 40;", "static int width;", "class Nested {}", ";") do
      assertEquals(structureProblem(turtleClass(member + mainMethod)).problem, JavaTurtleSource.Problem.UnsupportedStructure, clue = member)
  }

  test("turtle structure requires explicit static void helper methods") {
    for header <- Seq("void move", "public void move", "static int move", "static boolean move",
      "static String move", "static static void move", "public private static void move", "protected static void move",
      "final static void move", "abstract static void move") do {
      val source = turtleClass(s"$header() {} $mainMethod")
      assertEquals(structureProblem(source).problem, JavaTurtleSource.Problem.UnsupportedStructure, clue = header)
    }
  }

  test("turtle structure requires the supported main signature") {
    assertEquals(structureProblem(turtleClass("static void move() {}")).problem, JavaTurtleSource.Problem.MissingMain)
    for header <- Seq("static void main(String[] args)", "public void main(String[] args)", "private static void main(String[] args)",
      "public static static void main(String[] args)", "public static int main(String[] args)",
      "public static void main()", "public static void main(int[] args)", "public static void main(String args)",
      "public static void main(String[][] args)", "public static void main(java.lang.String[] args)",
      "public static void main(String[] args, int extra)") do {
      val problem = structureProblem(turtleClass(s"$header {}"))
      assertEquals(problem.problem, JavaTurtleSource.Problem.InvalidMain, clue = header)
    }
  }

  test("turtle structure does not allow duplicate or overloaded method names") {
    for methods <- Seq("static void move() {} static void move() {}", "static void move(int x) {} static void move(boolean x) {}",
      s"$mainMethod public static void main(int value) {}") do {
      val source = turtleClass(methods + (if methods.contains("main") then "" else mainMethod))
      assertEquals(structureProblem(source).problem, JavaTurtleSource.Problem.DuplicateDeclaration, clue = source)
    }
  }

  test("turtle structure requires distinct parameters of supported types") {
    assertEquals(structureProblem(turtleClass(s"static void move(int x, int x) {} $mainMethod")).problem,
      JavaTurtleSource.Problem.DuplicateDeclaration)
    for parameter <- Seq("String value", "Object value", "Point value", "int[] values", "boolean[] values", "int[][] values", "List<String> values") do
      assertEquals(structureProblem(turtleClass(s"static void move($parameter) {} $mainMethod")).problem,
        JavaTurtleSource.Problem.UnsupportedType, clue = parameter)
    for method <- Seq(s"static void move(final int x) {} $mainMethod", "public static void main(final String[] args) {}") do
      assertEquals(structureProblem(turtleClass(method)).problem, JavaTurtleSource.Problem.UnsupportedStructure, clue = method)
  }

  test("turtle structure rejects unavailable class names without restricting contextual parameter names") {
    for name <- Seq("String", "Turtle", "var", "yield", "record", "sealed", "permits", "_", "const", "goto") do
      assertEquals(structureProblem(turtleClass(mainMethod, s"class $name")).problem,
        JavaTurtleSource.Problem.InvalidIdentifier, clue = name)
    assertEquals(structuredSource(turtleClass("public static void main(String[] record) {} static void var(int record) {}"))
      .main.parameters.head.name, "record")
  }

  test("turtle structure rejects reserved identifiers in method parameter and local declarations") {
    for name <- Seq("_", "const", "goto") do
      for members <- Seq(s"static void $name() {} $mainMethod", s"static void move(int $name) {} $mainMethod",
        s"public static void main(String[] $name) {}", s"static void move() { int $name = 0; } $mainMethod",
        s"static void move() { for (int $name = 0; true; ) {} } $mainMethod") do
        assertEquals(structureProblem(turtleClass(members)).problem, JavaTurtleSource.Problem.InvalidIdentifier, clue = members)
  }

  test("turtle structure finds forbidden nested declarations in every supported block") {
    for declaration <- Seq("class Nested {}", "static void nested() {}", "package demo;", "import java.util.List;") do
      for body <- Seq(declaration, s"if (true) { $declaration }", s"if (true) {} else { $declaration }",
        s"while (true) { $declaration }", s"for (; true; ) { $declaration }", s"try { $declaration } catch (Exception ex) {}",
        s"try {} catch (Exception ex) { $declaration }", s"try {} finally { $declaration }") do {
        val source = turtleClass(s"static void move() { $body } $mainMethod")
        assertEquals(structureProblem(source).problem, JavaTurtleSource.Problem.UnsupportedStructure, clue = body)
      }
  }

  test("turtle structure leaves expression types for the separate semantic phase") {
    val checked = structuredSource(turtleSource("int distance = true; unknown(distance); return false;"))
    assertEquals(checked.methods.head.body.statements.size, 3)
  }

  test("turtle source rejects constructors and omitted method return types before structure checking") {
    for members <- Seq(s"Drawing() {} $mainMethod", s"static move() {} $mainMethod", "public static main(String[] args) {}") do
      assertEquals(sourceProblem(turtleClass(members)).problem, JavaTurtleSource.Problem.ParseFailure, clue = members)
  }

  test("turtle source excludes alternative Java array parameter spellings") {
    for parameter <- Seq("String args[]", "String... args") do {
      val source = turtleClass(s"public static void main($parameter) {}")
      assert(JavaTurtleSource.parse(source).isLeft, clue = source)
    }
  }

  test("turtle parsing retains grouping while the legacy parser keeps its AST") {
    val plain = JavaTarget("distance")
    assertEquals(statement("(distance);"), plain)
    assertEquals(sourceStatement("(distance);"), JavaParenthesizedExpression(plain))
    assertEquals(sourceStatement("((distance));"), JavaParenthesizedExpression(JavaParenthesizedExpression(plain)))
    val grouped = sourceStatement("(distance + 1) * 2;").asInstanceOf[JavaOperationBinary]
    assertEquals(grouped.op, "*")
    assert(grouped.left.isInstanceOf[JavaParenthesizedExpression])
    assertEquals(grouped.left.getChildren().size, 1)
    assert(statement("(distance + 1) * 2;").asInstanceOf[JavaOperationBinary].left.isInstanceOf[JavaOperationBinary])
  }

  test("turtle parsing distinguishes parenthesized statement expressions and call receivers") {
    assert(sourceStatement("move();").isInstanceOf[JavaFunctionCall])
    assert(sourceStatement("(move());").isInstanceOf[JavaParenthesizedExpression])
    assert(sourceStatement("(distance = 1);").isInstanceOf[JavaParenthesizedExpression])
    val receiver = sourceStatement("(Turtle).forward(40);").asInstanceOf[JavaCallExpression]
      .callee.asInstanceOf[JavaAttributeAccess].receiver
    assertEquals(receiver, JavaParenthesizedExpression(JavaTarget("Turtle")))
    val callee = sourceStatement("(move)(40);").asInstanceOf[JavaCallExpression].callee
    assertEquals(callee, JavaParenthesizedExpression(JavaTarget("move")))
  }

  test("turtle parsing distinguishes the direct minimum integer operand") {
    val direct = sourceStatement("-2147483648;").asInstanceOf[JavaOperationUnary]
    val grouped = sourceStatement("-(2147483648);").asInstanceOf[JavaOperationUnary]
    assert(direct.operand.isInstanceOf[JavaLiteral[?]])
    assert(grouped.operand.isInstanceOf[JavaParenthesizedExpression])
    assert(sourceStatement("(-2147483648);").isInstanceOf[JavaParenthesizedExpression])
  }

  test("turtle parsing retains for update grouping and traverses its children") {
    val loop = sourceStatement("for (int i = 0; i < 4; (i = i + 1)) {}").asInstanceOf[JavaForStatement]
    assert(loop.update.head.isInstanceOf[JavaParenthesizedExpression])
    assert(loop.update.head.getChildren().head.isInstanceOf[JavaAssignmentExpression])
    assertEquals(sourceProblem("Turtle.forward((distance << 1));").problem, JavaTurtleSource.Problem.UnsupportedSyntax)
    assertEquals(structureProblem(turtleSource("Turtle.forward((distance)); static void nested() {}")).problem,
      JavaTurtleSource.Problem.UnsupportedStructure)
  }

  test("turtle semantics accepts the full for and while square references") {
    for body <- Seq(
      "for (int i = 0; i < 4; i = i + 1) { Turtle.forward(sideLength); Turtle.turnRight(90); }",
      "int i = 0; while (i < 4) { Turtle.forward(sideLength); Turtle.turnRight(90); i = i + 1; }"
    ) do {
      val source = "\r\n" + turtleSource(body)
      val checked = checkedSource(source)
      assertEquals(checked.structure.parsedSource.source, source)
      assertEquals(checked.structure.methods.map(_.name), Seq("square", "main"))
    }
  }

  test("turtle semantics checks numeric boolean and grouped value expressions") {
    checkedSource(semanticSource(
      "int value = +distance - (-2) * 3 / 2 % 5; value += 1; value -= 1; value *= 2; value /= 2; value %= 3; " +
        "boolean draw = !(value < 0) && value <= 40 || value >= 100; " +
        "boolean same = flag == draw; same = same != false; if (draw) { Turtle.forward(value); }"
    ))
  }

  test("turtle semantics resolves later helpers and same-class qualified calls") {
    checkedSource(turtleClass(
      "static void first(int size) { Drawing.second(size, true); } " +
        "private static void second(int size, boolean enabled) { if (enabled) { Turtle.forward(size); } } " +
        "public static void main(String[] input) { first(40); }"
    ))
    checkedSource(turtleClass("static void move(int value) {} public static void main(String[] args) { int move = 1; move(move); }"))
  }

  test("turtle semantics retains branch and loop scopes without name leakage") {
    checkedSource(semanticSource(
      "if (flag) { int size = 10; Turtle.forward(size); } else { int size = 20; Turtle.forward(size); } " +
        "for (int i = 0; i < 4; i = i + 1) { Turtle.forward(i); } " +
        "for (int i = 0; i < 2; i = i + 1) { Turtle.forward(i); } int i = 40; Turtle.forward(i);"
    ))
    for body <- Seq("Turtle.forward(missing);", "before = 1; int before;", "if (flag) { int size = 1; } Turtle.forward(size);",
      "for (int i = 0; i < 4; i = i + 1) {} Turtle.forward(i);", "while (flag) { int size = 1; flag = false; } Turtle.forward(size);") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.UnknownVariable, clue = body)
  }

  test("turtle semantics rejects overlapping local and parameter declarations") {
    for body <- Seq("int distance = 1;", "int value = 1; if (flag) { int value = 2; }", "int value; int value;",
      "int i = 0; for (int i = 0; i < 4; i = i + 1) {}") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.DuplicateDeclaration, clue = body)
    assertEquals(semanticProblem(turtleClass("public static void main(String[] args) { int args = 1; }")).problem,
      JavaTurtleSource.Problem.DuplicateDeclaration)
  }

  test("turtle semantics requires initialization before reads and compound assignments") {
    checkedSource(semanticSource("int value; value = distance; Turtle.forward(value);"))
    for body <- Seq("int value; Turtle.forward(value);", "int value = value;", "int value; value += 1;",
      "int value; boolean draw = value > 0;", "int value; if (flag) { value = 1; } Turtle.forward(value);") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.UninitializedVariable, clue = body)
  }

  test("turtle semantics joins initialized branches and handles returning paths") {
    for body <- Seq("int value; if (flag) { value = 10; } else { value = 20; } Turtle.forward(value);",
      "int value; if (flag) { return; } else { value = 20; } Turtle.forward(value);",
      "int value; if (flag) { value = 10; } else { return; } Turtle.forward(value);") do
      checkedSource(semanticSource(body))
    for body <- Seq("return; Turtle.forward(1);", "if (flag) { return; } else { return; } Turtle.forward(1);") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.UnreachableStatement, clue = body)
  }

  test("turtle semantics distinguishes constant condition flow from statement reachability") {
    for body <- Seq("int value; if (true) { value = 1; } Turtle.forward(value);",
      "int value; if (false) { Turtle.forward(value); }", "int value; if (true) { return; } Turtle.forward(value);",
      "if (false) { return; } Turtle.forward(1);") do checkedSource(semanticSource(body))
    for body <- Seq("if (false) { int value; Turtle.forward(value); }", "if (true) { return; } int value; Turtle.forward(value);") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.UninitializedVariable, clue = body)
  }

  test("turtle semantics carries short-circuit definite assignment without inventing constants") {
    for body <- Seq("boolean value; boolean result = false && value;", "boolean value; boolean result = true || value;",
      "int value; if (true || flag) { value = 1; } Turtle.forward(value);",
      "while (false && flag) {} Turtle.forward(1);", "while (true || flag) {} Turtle.forward(1);",
      "while (false && 1 / 0 > 0) {} Turtle.forward(1);", "while (true || 1 / 0 > 0) {} Turtle.forward(1);",
      "while (1 / 0 == 0) {} Turtle.forward(1);") do checkedSource(semanticSource(body))
    assertEquals(semanticProblem(semanticSource("boolean value; boolean result = flag && value;")).problem,
      JavaTurtleSource.Problem.UninitializedVariable)
  }

  test("turtle semantics respects zero iterations for assignments and for initialization") {
    for body <- Seq("int value; while (flag) { value = 1; flag = false; } Turtle.forward(value);",
      "int value; for (int i = 0; i < 4; i = i + 1) { value = 1; } Turtle.forward(value);") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.UninitializedVariable, clue = body)
    checkedSource(semanticSource("int i; for (i = 0; i < 4; i = i + 1) {} Turtle.forward(i);"))
    checkedSource(semanticSource("int value; for (int i = 0; i < 4; i = i + value) { value = 1; }"))
    assertEquals(semanticProblem(semanticSource("for (int i = 0; i < 4; i = i + value) { int value = 1; }")).problem,
      JavaTurtleSource.Problem.UnknownVariable)
  }

  test("turtle semantics checks non-completing loop bodies and constant loop reachability") {
    for body <- Seq("while (true) {}", "for (;;) {}", "for (int i = 0; i < 4; i = i + 1) { return; } Turtle.forward(1);",
      "int value; for (int i = 0; i < 4; Turtle.forward(value)) { return; }") do checkedSource(semanticSource(body))
    for body <- Seq("while (false) {}", "for (; false; ) {}", "while (true) {} Turtle.forward(1);",
      "for (;;) {} Turtle.forward(1);", "while (2147483647 + 1 < 0) {} Turtle.forward(1);") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.UnreachableStatement, clue = body)
  }

  test("turtle semantics checks decimal int boundaries with preserved operand syntax") {
    for value <- Seq("0", "2147483647", "-2147483648", "(-2147483648)", "- -2147483648", "2147483647 + 1") do
      checkedSource(semanticSource(s"int value = $value; Turtle.forward(value);"))
    for value <- Seq("2147483648", "+2147483648", "-(2147483648)", "-2147483649", "99999999999999999999999") do
      assertEquals(semanticProblem(semanticSource(s"int value = $value;")).problem, JavaTurtleSource.Problem.IntegerRange, clue = value)
  }

  test("turtle semantics folds int constants with Java overflow and division rules") {
    for condition <- Seq(
      "2147483647 + 1 == -2147483648", "-2147483648 - 1 == 2147483647",
      "2147483647 * 2147483647 == 1", "-2147483648 * -1 == -2147483648",
      "-2147483648 / -1 == -2147483648", "-2147483648 % -1 == 0",
      "-2147483648 / -2147483648 == 1", "-5 / 2 == -2", "-5 % 2 == -1", "5 % -2 == 1"
    ) do {
      checkedSource(semanticSource(s"while ($condition) {}"))
      checkedSource(semanticSource(s"int value; if ($condition) { value = 1; } Turtle.forward(value);"))
      assertEquals(semanticProblem(semanticSource(s"while (!($condition)) {}")).problem,
        JavaTurtleSource.Problem.UnreachableStatement, clue = condition)
    }
  }

  test("turtle semantics rejects mismatched assignments operations and conditions") {
    for body <- Seq("int value = true;", "boolean value = 1;", "distance = false;", "flag = 1;", "flag += true;",
      "int value = flag + 1;", "boolean value = flag < true;", "boolean value = distance == flag;",
      "boolean value = distance && flag;", "int value = !distance;", "int value = -flag;", "if (1) {}", "while (1) {}",
      "for (; 1; ) {}", "return distance;", "int value = run(distance, flag);") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.TypeMismatch, clue = body)
  }

  test("turtle semantics rejects wrong call argument counts and types") {
    for body <- Seq("Turtle.forward();", "Turtle.forward(1, 2);", "Turtle.forward(true);", "Turtle.turnRight(false);",
      "run(distance);", "run(flag, distance);") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.ArgumentMismatch, clue = body)
    assertEquals(semanticProblem(semanticSource("missing(distance);")).problem, JavaTurtleSource.Problem.UnknownMethod)
  }

  test("turtle semantics resolves static receiver shadowing at the point of use") {
    checkedSource(semanticSource("Turtle.forward(distance); int Turtle = 1; int Drawing = 2;"))
    for body <- Seq("int Turtle = 1; Turtle.forward(distance);", "int Drawing = 1; Drawing.run(distance, flag);") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.TypeMismatch, clue = body)
    assertEquals(semanticProblem(turtleClass(s"static void move(int Turtle) { Turtle.forward(1); } $mainMethod")).problem,
      JavaTurtleSource.Problem.TypeMismatch)
  }

  test("turtle semantics rejects statement and callee grouping without stripping parentheses") {
    for body <- Seq("1;", "distance + 1;", "(run(distance, flag));", "(distance = 1);", "(Turtle).forward(1);",
      "(run)(distance, flag);", "for (int i = 0; i < 4; (i = i + 1)) {}") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.UnsupportedSyntax, clue = body)
  }

  test("turtle semantics excludes assignments in value positions and unsupported constructs") {
    for body <- Seq("Turtle.forward(distance = 1);", "int value = (distance = 1);", "if (flag = true) {}",
      "if (flag && (distance = 1) > 0) {}", "while (flag) { break; }", "while (flag) { continue; }",
      "try {} finally {}", "throw distance;", "Math.abs(distance);", "new Point();", "main();", "final int value = 1;") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.UnsupportedSyntax, clue = body)
    for body <- Seq("int[] values;", "String text;", "Object value;", "List<String> values;", "int value = null;") do
      assertEquals(semanticProblem(semanticSource(body)).problem, JavaTurtleSource.Problem.UnsupportedType, clue = body)
    assertEquals(semanticProblem(turtleClass("public static void main(String[] args) { Turtle.forward(args); }")).problem,
      JavaTurtleSource.Problem.UnsupportedType)
  }

  test("turtle semantics detects direct mutual and unused helper recursion") {
    for members <- Seq(s"static void move() { move(); } $mainMethod",
      s"static void first() { second(); } static void second() { first(); } $mainMethod",
      s"static void move() { Drawing.move(); } $mainMethod") do
      assertEquals(semanticProblem(turtleClass(members)).problem, JavaTurtleSource.Problem.UnsupportedSyntax, clue = members)
  }

  test("turtle semantics distinguishes contextual calls and Object signature collisions") {
    checkedSource(turtleClass(s"static void yield() {} public static void main(String[] args) { Drawing.yield(); }"))
    assertEquals(semanticProblem(turtleClass("static void yield() {} public static void main(String[] args) { yield(); }")).problem,
      JavaTurtleSource.Problem.UnsupportedSyntax)
    for name <- Seq("wait", "notify", "notifyAll", "toString", "hashCode", "getClass", "clone", "finalize") do
      assertEquals(semanticProblem(turtleClass(s"static void $name() {} $mainMethod")).problem,
        JavaTurtleSource.Problem.UnsupportedStructure, clue = name)
    checkedSource(turtleClass(s"static void wait(int value) {} public static void main(String[] args) { wait(1); }"))
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
