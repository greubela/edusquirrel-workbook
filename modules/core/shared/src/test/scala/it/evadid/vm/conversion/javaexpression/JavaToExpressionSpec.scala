package it.evadid.vm.conversion.javaexpression

import it.evadid.vm.code.errors.{ProgrammingConversionException, ProgrammingConversionKind}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateBeExpression
import munit.FunSuite

class JavaToExpressionSpec extends FunSuite {
  private def python(java: String): String = new JavaToExpression(java).convert.toPython.code

  private def program(body: String, members: String = ""): String =
    s"class Main {\n$members\n    public static void main(String[] args) {\n$body\n    }\n}\n"

  private def rejected(java: String): ProgrammingConversionException =
    intercept[ProgrammingConversionException](new JavaToExpression(java).convert)

  private def drawn(java: String): List[Double] =
    new JavaToExpression(java).convert.turtleCommandExecutionInfo.completedCommands.map(_.args.head)

  test("counting for loops run like Java, including steps, downward counts and empty ranges") {
    assert(python(program("for (int i = 0; i < 3; i++) { Turtle.forward(10); }")).contains("for _ in range(3)"))
    assertEquals(drawn(program("for (int i = 0; i < 3; i++) { Turtle.forward(10); }")), List(10.0, 10.0, 10.0))
    assertEquals(drawn(program("for (int i = 1; i <= 4; i += 1) { Turtle.forward(i); }")), List(1.0, 2.0, 3.0, 4.0))
    assertEquals(drawn(program("for (int i = 0; i < 5; i += 2) { Turtle.forward(i); }")), List(0.0, 2.0, 4.0))
    assertEquals(drawn(program("for (int i = 3; i > 0; i--) { Turtle.forward(i); }")), List(3.0, 2.0, 1.0))
    assertEquals(drawn(program("for (int i = 5; i < 2; i++) { Turtle.forward(i); }")), Nil)
    assertEquals(drawn(program("double n = 2;\nfor (int i = 0; i < n + 1; i++) { Turtle.forward(i); }")), List(0.0, 1.0, 2.0))
  }

  test("assignments, updates and literals translate to the neutral program") {
    val code = python(program(
      """double x = 1;
        |boolean ready = true;
        |String label = null;
        |x += 2;
        |x -= 1;
        |x++;
        |x--;
        |if (ready && !(x > 3) || false) { x = Math.floor(x / 2); } else { x = Math.pow(x, 2); }
        |while (x < 10) { x = java.lang.Math.pow(x, 1) + 1; }""".stripMargin))
    Seq("x: float = 1", "ready: bool = True", "label = None", "x = x + 2", "x = x - 1", "x = x + 1", "x = x - 1",
      "(ready and (not (x > 3))) or False", "x // 2", "x ** 2", "while x < 10", "x ** 1").foreach { part =>
      assert(code.contains(part), s"'$part' missing in\n$code")
    }
  }

  test("module members drop the entry-class qualifier and static fields stay module variables") {
    val code = python(program("Main.count = Main.count + 1;\nMain.step(count);",
      "    static double count = 0;\n    static double total;\n    static void step(double size) { Turtle.forward(size); }"))
    Seq("count: float = 0", "total: float", "def step(size: float)", "count = count + 1", "step(count)").foreach { part =>
      assert(code.contains(part), s"'$part' missing in\n$code")
    }
  }

  test("loose statements outside a class are module code and imports or packages are ignored") {
    val code = python("import java.util.List;\nTurtle.forward(5);\n// note\nint size;")
    assert(code.contains("forward(5)"), code)
    assert(code.contains("# note") || code.contains("note"), code)
  }

  test("object classes keep fields, constructors, static and instance methods") {
    val code = python(
      """class Box {
        |    double size = 2;
        |    Box(double size) { this.size = size; }
        |    double grow() { size = size + 1; return size; }
        |    static double unit() { return 1; }
        |}
        |class Main {
        |    public static void main(String[] args) {
        |        Box box = new Box(3);
        |        box.size = box.grow();
        |        double unit = Box.unit();
        |    }
        |}""".stripMargin)
    Seq("class Box", "self.size = size", "self.size = (self.size + 1)", "def unit", "box = Box(3)", "box.size = box.grow()", "Box.unit()")
      .foreach(part => assert(code.contains(part), s"'$part' missing in\n$code"))
  }

  test("constructs without a shared representation are rejected with an explanation") {
    val cases = Seq(
      program("Turtle.forward(args.length);") -> "Program arguments",
      "class Box { static double size = 1; }" -> "Static object fields",
      program("double[] values = new double[3];\nvalues[0] = 1;") -> "",
      program("Turtle.forward(1") -> "")
    val outcomes = cases.map { (source, expected) =>
      scala.util.Try(new JavaToExpression(source).convert).failed.toOption match {
        case Some(error) => Option.when(!error.getMessage.contains(expected))(s"$source -> ${error.getMessage}")
        case None => Some(s"$source was accepted")
      }
    }
    assertEquals(outcomes.flatten.toList, Nil)
    assert(python(program("char letter = 'a';")).contains("letter: str = 'a'"))
    val inheritance = new JavaToExpression("class Box extends Base { }").convert.expression.toString
    assert(inheritance.contains("BeExpressionUnsupported(class Box") && inheritance.contains("Inheritance"), inheritance)
    assertEquals(rejected(program("Turtle.forward(1")).error.kind, ProgrammingConversionKind.Unparsable)
  }

  test("declared types map to neutral types, including dates and named classes") {
    val code = python(program("Date today;\nObject anything = 1;\nBox box = null;"))
    assert(code.contains("anything: Any = 1"), code)
    assert(code.contains("today: date"), code)
    assert(code.contains("box = None"), code)
  }

  test("full class programs are detected and need the checked runner for turtle commands") {
    val classProgram = new JavaToExpression("class Main { public static void main(String[] args) { Turtle.forward(5); } }")
    assert(classProgram.isClassProgram)
    intercept[IllegalArgumentException](classProgram.toLegacyTurtleCommands)
    assert(classProgram.compileForExecution.isRight)
    val fragment = new JavaToExpression("Turtle.forward(5);\nTurtle.turnLeft(90);")
    assert(!fragment.isClassProgram)
    assertEquals(fragment.toLegacyTurtleCommands.size, 2)
    assertEquals(fragment.preparedSource.code, "Turtle.forward(5);\nTurtle.turnLeft(90);")
  }

  test("array reads, chained calls and parenthesised values translate or are rejected explicitly") {
    assert(python(program("double x = (1 + 2) * 3;")).contains("x = (1 + 2) * 3"), python(program("double x = (1 + 2) * 3;")))
    assert(rejected(program("double[] v = new double[2];\nTurtle.forward(v[0]);")).getMessage.nonEmpty)
    val chained = python(
      """class Pen { Pen self() { return this; } void draw() { Turtle.forward(1); } }
        |class Main { public static void main(String[] args) { Pen pen = new Pen(); pen.self().draw(); } }""".stripMargin)
    assert(chained.contains("pen.self().draw()"), chained)
  }

  test("static methods cannot call instance methods without an object and other class bodies are rejected") {
    val error = intercept[IllegalArgumentException](python("class Box { void grow() { } static void make() { grow(); } }"))
    assert(error.getMessage.contains("needs an object"), error.getMessage)
    assert(rejected("class Box { static { Turtle.forward(1); } }").getMessage.nonEmpty)
    assert(rejected(program("do { Turtle.forward(1); } while (false);")).getMessage.nonEmpty)
    val nested = python("class Outer { class Inner { double size = 1; } public static void main(String[] args) { Turtle.forward(1); } }")
    assert(nested.contains("class Inner"), nested)
  }

  test("Math methods map to Python built-ins or stay explicit placeholders instead of unimported math calls") {
    val code = python(program("Turtle.forward(Math.abs(-3) + Math.max(1, 2) + Math.min(4, 5) + java.lang.Math.abs(-1));\nTurtle.forward(Math.pow(2, 3) + Math.floor(7.0 / 2));"))
    Seq("abs(-3)", "max(1, 2)", "min(4, 5)", "abs(-1)", "2 ** 3", "7.0 // 2").foreach(part => assert(code.contains(part), s"'$part' missing in\n$code"))
    assert(!code.contains("math."), code)
    assertEquals(drawn(program("Turtle.forward(Math.abs(-3) + Math.max(1, 2));")), List(5.0))
    for method <- Seq("sqrt(16)", "round(2.5)", "ceil(2.1)", "sin(0)") do {
      val tree = new JavaToExpression(program(s"Turtle.forward(Math.$method);")).convert.expression.toString
      assert(tree.contains(s"BeExpressionUnsupported(Math.${method.takeWhile(_ != '(')}(...)"), tree)
    }
  }
}
