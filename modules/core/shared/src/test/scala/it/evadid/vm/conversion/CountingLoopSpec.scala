package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeFor, BeRepeatNr, BeWhile}
import it.evadid.vm.code.usage.{BeAssignVariable, BeUseValue}
import it.evadid.vm.conversion.printing.ModuleLayout
import it.evadid.vm.types.BeDataValueLiteral
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

/** Counting loops become the most specific shared loop: a fixed repetition when the counter is never
  * read and the bounds are literals, a counting `for` for steps of ±1, and a while loop otherwise.
  */
class CountingLoopSpec extends FunSuite {
  private enum Loop {
    case Repeat(times: Int)
    case For(variable: String, start: String, end: String)
    case While
  }

  private def literal(expression: BeExpression): String = expression match {
    case BeUseValue(BeDataValueLiteral(raw), _) => raw
    case other => other.toString
  }

  private def loops(state: ProgrammingState): Vector[Loop] = ModuleLayout.nodes(state.toBeExpression.expression).collect {
    case loop: BeRepeatNr => Loop.Repeat(loop.amount)
    case loop: BeFor => Loop.For(loop.variable.name.universalInterpretation(), literal(loop.start), literal(loop.end))
    case _: BeWhile => Loop.While
  }

  private def distances(state: ProgrammingState): Seq[Double] =
    state.toBeExpression.turtleCommandExecutionInfo.completedCommands.flatMap(_.args)

  private def python(source: String) = ProgrammingStateRaw(Python, source)
  private def java(body: String) = ProgrammingStateRaw(Java, s"class Main {\n    public static void main(String[] args) {\n$body\n    }\n}\n")

  test("Python counting loops whose counter is never read are fixed repetitions") {
    Seq("range(4)", "range(0, 4)", "range(1, 5)", "range(0, 8, 2)", "range(4, 0, -1)", "range(7, -1, -2)").foreach { range =>
      val state = python(s"for _ in $range:\n    forward(10)\n")
      assertEquals(loops(state), Vector(Loop.Repeat(4)), range)
      assertEquals(distances(state), Seq.fill(4)(10.0), range)
    }
    assertEquals(loops(python("for i in range(3):\n    forward(10)\n")), Vector(Loop.Repeat(3)), "an unread named counter")
  }

  test("Java counting for loops whose counter is not used in the body are fixed repetitions") {
    Seq("int i = 0; i < 4; i++", "int i = 0; i < 4; ++i", "int i = 1; i <= 4; i++", "int i = 4; i > 0; i--",
        "int i = 4; i >= 1; i -= 1", "int i = 0; i < 8; i += 2", "int i = 0; i < 4; i = i + 1").foreach { header =>
      val state = java(s"for ($header) {\n    Turtle.forward(10);\n}")
      assertEquals(loops(state), Vector(Loop.Repeat(4)), header)
      assertEquals(distances(state), Seq.fill(4)(10.0), header)
    }
  }

  test("counting loops that read their counter are counting for loops, also counting down") {
    val up = python("for i in range(1, 4):\n    forward(i)\n")
    assertEquals(loops(up), Vector(Loop.For("i", "1", "3")))
    assertEquals(distances(up), Seq(1.0, 2.0, 3.0))
    val down = python("for i in range(3, 0, -1):\n    forward(i)\n")
    assertEquals(loops(down), Vector(Loop.For("i", "3", "1")))
    assertEquals(distances(down), Seq(3.0, 2.0, 1.0))
    assert(down.toPython.code.contains("for i in range(3, 0, -1):"), down.toPython.code)
    assert(down.toJava.code.contains("for (int i = 3; i >= 1; i--) {"), down.toJava.code)
    val javaDown = java("for (int i = 3; i > 0; i--) {\n    Turtle.forward(i);\n}")
    assertEquals(loops(javaDown), Vector(Loop.For("i", "3", "1")))
    assertEquals(distances(javaDown), Seq(3.0, 2.0, 1.0))
  }

  test("a Python counter read after its loop keeps the counting loop") {
    val state = python("for i in range(3):\n    forward(1)\nforward(i)\n")
    assertEquals(loops(state), Vector(Loop.For("i", "0", "2")))
    assertEquals(distances(state), Seq(1.0, 1.0, 1.0, 2.0))
  }

  test("counts without literal bounds stay counting for loops, as a repeat needs a fixed number") {
    val state = python("n = 3\nfor _ in range(n):\n    forward(1)\n")
    assert(loops(state).forall(_.isInstanceOf[Loop.For]), loops(state).toString)
    assertEquals(distances(state), Seq(1.0, 1.0, 1.0))
  }

  test("empty literal ranges do not count down like Snap's for") {
    val state = python("for i in range(5, 2):\n    forward(i)\n")
    assertEquals(loops(state), Vector(Loop.Repeat(0)))
    assertEquals(distances(state), Nil)
  }

  test("other steps and loops that change their bound become an initialisation and a while loop") {
    val stepped = python("for i in range(10, 0, -3):\n    forward(i)\n")
    assertEquals(loops(stepped), Vector(Loop.While))
    assert(stepped.toBeExpression.expression.toString.contains("BeAssignVariable"), "the counter is initialised before the loop")
    assertEquals(distances(stepped), Seq(10.0, 7.0, 4.0, 1.0))
    val doubling = java("for (int i = 1; i < 100; i = i * 2) {\n    Turtle.forward(i);\n}")
    assertEquals(loops(doubling), Vector(Loop.While))
    assertEquals(distances(doubling), Seq(1.0, 2.0, 4.0, 8.0, 16.0, 32.0, 64.0))
    val shrinking = java("int n = 6;\nfor (int i = 0; i < n; i++) {\n    n = n - 1;\n    Turtle.forward(1);\n}")
    assertEquals(loops(shrinking), Vector(Loop.While))
    assertEquals(distances(shrinking), Seq(1.0, 1.0, 1.0))
    val noCondition = java("int i = 0;\nfor (; i < 2; ) {\n    Turtle.forward(5);\n    i++;\n}")
    assertEquals(loops(noCondition), Vector(Loop.While))
    assertEquals(distances(noCondition), Seq(5.0, 5.0))
  }

  test("two Java loops may declare the same counter") {
    val state = java("for (int i = 1; i < 9; i *= 2) {\n    Turtle.forward(i);\n}\nfor (int i = 2; i < 9; i *= 2) {\n    Turtle.forward(i);\n}")
    assertEquals(distances(state), Seq(1.0, 2.0, 4.0, 8.0, 2.0, 4.0, 8.0))
  }

  test("a loop that assigns its own Python counter is reported as unsupported rather than changed") {
    val result = python("for i in range(4):\n    i = i + 1\n    forward(i)\n").convertToLanguage(Java)
    assert(result.hasLostFunctionality, result.conversionInfo.toString)
  }
}
