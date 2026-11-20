package it.evadid.vm.compilation.java.turtle

import munit.FunSuite

class JavaTurtleConstantFlowSpec extends FunSuite {
  private def compile(body: String) =
    JavaTurtleCompiler.compile(s"class Main { public static void main(String[] args) { $body } }")
  private def accepted(body: String): Unit = assert(compile(body).isRight, compile(body).swap.map(_.message).getOrElse(""))
  private def rejected(body: String, message: String): Unit = assertEquals(mismatch(body, message), None)
  private def mismatch(body: String, message: String): Option[String] = compile(body) match {
    case Left(problem) => Option.when(!problem.message.contains(message))(s"$body: ${problem.message}")
    case Right(_) => Some(s"$body was accepted")
  }

  test("constant conditions decide definite assignment like the Java compiler") {
    accepted("int x; if (2.0 - 1.0 >= 1.0) { x = 1; } Turtle.forward(x);")
    rejected("int x; if (2.0 * 1.0 <= 1.0) { x = 1; } Turtle.forward(x);", "on every path")
    accepted("int x; if (true && !false) { x = 1; } Turtle.forward(x);")
    accepted("int x; if (false || true) { x = 1; } Turtle.forward(x);")
    rejected("int x; if (true && false) { x = 1; } Turtle.forward(x);", "on every path")
    accepted("int x; if (true == true) { x = 1; } Turtle.forward(x);")
    accepted("int x; if (1 != 2.0) { x = 1; } Turtle.forward(x);")
    accepted("int x; if (-2 < -1 && -1.5 < +1.5) { x = 1; } Turtle.forward(x);")
    accepted("int x; if (7 % 4 == 3 && 7 / 2 == 3 && 7.0 / 2 == 3.5 && 7.5 % 2 == 1.5) { x = 1; } Turtle.forward(x);")
  }

  test("unsupported operators, targets and literals are explained") {
    val cases = Seq(
      ("int x = 1; x <<= 1;", "not supported"),
      ("int x = 1; int y = ~x;", "not supported"),
      ("int x = 1; int y = x & 1;", "not supported"),
      ("int x = 2147483648;", "outside the int range"),
      ("String s = \"a\";", ""),
      ("int x = 1; x += 0.5;", "Mixed double-to-int"),
      ("boolean b = true; b += 1;", "Arithmetic assignments need an int or double variable"),
      ("double d = 1; d += true;", "Arithmetic assignments need an int or double value"),
      ("boolean b = true; b++;", "Increment and decrement need an int or double variable"),
      ("int x; x++;", "on every path"),
      ("int x = -true;", "Unary + and - need"),
      ("boolean b = !1;", "The ! operator needs a boolean value"),
      ("boolean b = 1 == true;", "Compare two numeric values or two boolean values"),
      ("boolean b = true < 1;", "needs int or double operands"),
      ("int x = Turtle.forward(1);", "void method call does not produce a value"))
    assertEquals(cases.flatMap(mismatch.tupled).toList, Nil)
  }
}
