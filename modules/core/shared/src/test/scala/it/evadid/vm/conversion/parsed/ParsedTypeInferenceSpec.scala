package it.evadid.vm.conversion.parsed

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.workbook.interactions.programming.state.*
import munit.FunSuite

/** Untyped Python functions receive the types their evidence agrees on, so Java gets compilable signatures. */
class ParsedTypeInferenceSpec extends FunSuite {
  private def java(python: String): String = ProgrammingStateRaw(Python, python).convertToLanguage(Java).finalState
    .getOrElse(fail(s"No Java for:\n$python")).toString.linesIterator.takeWhile(!_.startsWith("class Turtle")).mkString("\n")
  private def signatures(python: String): List[String] = java(python).linesIterator.map(_.trim).filter(line =>
    line.startsWith("static") && line.contains("(") && !line.contains("main(")).toList

  test("a parameter used as a drawing argument or arithmetic operand is a number") {
    assertEquals(signatures("def side(length):\n    forward(length)\n    left(120)\n\nside(10)\n"), List("static void side(double length) {"))
    assertEquals(signatures("def scaled(x):\n    forward(x * 3)\n\nscaled(1)\n"), List("static void scaled(double x) {"))
  }

  test("arguments at call sites type a parameter: text, numbers and truth values") {
    assertEquals(signatures("def greet(name):\n    print(\"Hello \" + name)\n\ngreet(\"Ada\")\n"), List("static void greet(String name) {"))
    assertEquals(signatures("def maybe(flag):\n    if flag:\n        forward(1)\n\nmaybe(True)\n"), List("static void maybe(boolean flag) {"))
  }

  test("a function returning a value takes the type of what it returns, never void") {
    assertEquals(signatures("def double(x):\n    return x * 2\n\nforward(double(5))\n"), List("static double double_(double x) {"))
    assertEquals(signatures("def big(n):\n    return n > 10\n\nif big(20):\n    forward(1)\n"), List("static boolean big(double n) {"))
    assertEquals(signatures("def first(a, b):\n    if a:\n        return 1\n    return \"no\"\n\nprint(first(True, 2))\n"),
      List("static Object first(boolean a, double b) {"), "returns that disagree have no common type, but still a result")
  }

  test("conflicting evidence leaves a parameter untyped, and annotations are kept") {
    assertEquals(signatures("def show(v):\n    print(v)\n\nshow(1)\nshow(\"one\")\n"), List("static void show(Object v) {"))
    assertEquals(signatures("def half(v: int) -> float:\n    return v / 2\n\nforward(half(4))\n"), List("static double half(double v) {"))
  }

  test("types flow through chains of helper functions") {
    assertEquals(signatures("def twice(v):\n    return inner(v) * 2\n\ndef inner(w):\n    return w + 1\n\nforward(twice(3))\n"),
      List("static double twice(double v) {", "static double inner(double w) {"))
  }

  test("inferred Java draws what the Python program draws") {
    val python = "def side(length):\n    forward(length)\n    left(90)\n\ndef double(x):\n    return x * 2\n\nfor i in range(4):\n    side(double(i + 1))\n"
    val source = ProgrammingStateRaw(Python, python)
    val converted = source.convertToLanguage(Java).finalState.get
    assertEquals(converted.toBeExpression.turtleCommandExecutionInfo.completedCommands,
      source.toBeExpression.turtleCommandExecutionInfo.completedCommands)
  }
}
