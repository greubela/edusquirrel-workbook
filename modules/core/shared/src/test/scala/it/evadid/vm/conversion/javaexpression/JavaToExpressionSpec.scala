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

  test("counting for loops become repeats, ranges or rejected loops depending on their header") {
    assert(python(program("for (int i = 0; i < 3; i++) { Turtle.forward(10); }")).contains("for _ in range(3)"),
      python(program("for (int i = 0; i < 3; i++) { Turtle.forward(10); }")))
    val inclusive = python(program("for (int i = 1; i <= 4; i += 1) { Turtle.forward(i); }"))
    assert(inclusive.contains("range(1, 4 + 1)") || inclusive.contains("range(1, 5)"), inclusive)
    val exclusiveLiteral = python(program("for (int i = 2; i < 6; i++) { Turtle.forward(i); }"))
    assert(exclusiveLiteral.contains("range(2, 6)") || exclusiveLiteral.contains("range(2, 5 + 1)"), exclusiveLiteral)
    val usedCounter = python(program("for (int i = 0; i < 3; i++) { Turtle.forward(i); }"))
    assert(usedCounter.contains("for i in range"), usedCounter)
    val variableLimit = python(program("double n = 5;\nfor (int i = 0; i < n; i++) { Turtle.forward(i); }"))
    assert(variableLimit.contains("for i in range"), variableLimit)
    val plusOne = python(program("double n = 5;\nfor (int i = 0; i < n + 1; i++) { Turtle.forward(i); }"))
    assert(plusOne.contains("for i in range"), plusOne)
    assertEquals(rejected(program("for (int i = 0; i < 3; i += 2) { Turtle.forward(i); }")).error.kind,
      ProgrammingConversionKind.UnsupportedConstruct)
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
    val mixed = rejected(program("Turtle.forward(1);", "    double size = 1;"))
    assert(mixed.getMessage.contains("Mixed static and instance members"), mixed.getMessage)
    assert(rejected(program("Turtle.forward(args.length);")).getMessage.contains("Program arguments"))
    assert(rejected("class Box { static double size = 1; }").getMessage.contains("Static object fields"))
    assert(rejected("class Box extends Base { }").getMessage.contains("class Box"))
    assert(rejected(program("double[] values = new double[3];\nvalues[0] = 1;")).getMessage.nonEmpty)
    assert(python(program("char letter = 'a';")).contains("letter: str = 'a'"))
    val unparsable = rejected(program("Turtle.forward(1"))
    assertEquals(unparsable.error.kind, ProgrammingConversionKind.Unparsable)
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
}
