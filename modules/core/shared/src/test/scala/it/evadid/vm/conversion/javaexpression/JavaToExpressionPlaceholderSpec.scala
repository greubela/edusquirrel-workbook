package it.evadid.vm.conversion.javaexpression

import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, ProgrammingConversionException}
import munit.FunSuite

class JavaToExpressionPlaceholderSpec extends FunSuite {
  private def program(body: String, members: String = "") =
    s"class Main {\n$members\n    public static void main(String[] args) {\n$body\n    }\n}\n"
  private def placeholders(java: String): Seq[String] =
    new JavaToExpression(java).convert.expression.subtree.collect {
      case BeExpressionUnsupported(source, _, reason) => s"$source | $reason"
      case BeExpressionUnparsable(source, reason, _) => s"$source | $reason"
    }.toSeq
  private def drawn(java: String): List[Double] =
    new JavaToExpression(java).convert.turtleCommandExecutionInfo.completedCommands.map(_.args.head)

  test("statements and expressions without a shared form stay visible placeholders") {
    val cases = Seq(
      program("int c = (char) 65;") -> "(String) ...",
      program("for (int i = 0; i < 3; Turtle.forward(i)) { }") -> "For-loop updates")
    val outcomes = cases.map((source, expected) => source -> scala.util.Try(placeholders(source)).fold(error => s"rejected: ${error.getMessage}",
      found => if found.exists(_.contains(expected)) then "ok" else s"placeholders $found"))
    assertEquals(outcomes.filterNot(_._2 == "ok").toList, Nil)
    // Arrays, do-while and the conditional operator are rejected by the parser before translation.
    Seq(program("double[] v = new double[2];\nv[0] = 1;"), program("do { Turtle.forward(1); } while (false);"),
      program("double x = 1;\nTurtle.forward(x > 0 ? x : -x);")).foreach { source =>
      intercept[ProgrammingConversionException](new JavaToExpression(source).convert)
    }
  }

  test("class bodies with initializer blocks are placeholders and mixed static and instance entry classes are rejected") {
    intercept[ProgrammingConversionException](new JavaToExpression("class Box { { Turtle.forward(1); } double size = 1; }").convert)
    val mixed = intercept[ProgrammingConversionException](new JavaToExpression(program("Turtle.forward(1);", "    double size = 1;")).convert)
    assert(mixed.getMessage.contains("Mixed static and instance members"), mixed.getMessage)
  }

  test("general for loops, compound updates and text emptiness translate to equivalent loops") {
    assertEquals(drawn(program("for (int i = 10; i > 0; i -= 4) { Turtle.forward(i); }")), List(10.0, 6.0, 2.0))
    assertEquals(drawn(program("int n = 0;\nfor (int i = 1; n < 3; i = i * 2) { Turtle.forward(i); n++; }")), List(1.0, 2.0, 4.0))
    assertEquals(drawn(program("for (int i = 0; i < 2; ) { Turtle.forward(i); i++; }")), List(0.0, 1.0))
    val emptiness = new JavaToExpression(program("String s = \"\";\nif (s.isEmpty()) { Turtle.forward(1); }")).convert.toPython.code
    assert(emptiness.contains("s == \"\""), emptiness)
  }
}
