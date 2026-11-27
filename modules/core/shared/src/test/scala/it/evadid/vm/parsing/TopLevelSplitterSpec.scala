package it.evadid.vm.parsing

import munit.FunSuite

class TopLevelSplitterSpec extends FunSuite {
  test("splits outside brackets and quotes, trimming parts and dropping only a trailing empty part") {
    assertEquals(TopLevelSplitter.brackets.split(" a, (b, c), [d, {e, f}] ,, g, ", ','), List("a", "(b, c)", "[d, {e, f}]", "", "g"))
    assertEquals(TopLevelSplitter.brackets.split("", ','), Nil)
    assertEquals(TopLevelSplitter.brackets.split("'x: y': \"z\\\": w\": v", ':'), List("'x: y'", "\"z\\\": w\"", "v"))
  }

  test("nesting characters are configurable") {
    val generics = TopLevelSplitter(Set('<', '('), Set('>', ')'))
    assertEquals(generics.split("Map<A, B>, f(x, y)", ','), List("Map<A, B>", "f(x, y)"))
    assertEquals(TopLevelSplitter.brackets.split("Map<A, B>", ','), List("Map<A", "B>"))
  }

  test("collection elements are read inside optional delimiters") {
    assertEquals(TopLevelSplitter.brackets.elements("List.of(1, (2, 3))", "List.of(", ")"), List("1", "(2, 3)"))
    assertEquals(TopLevelSplitter.brackets.elements(" { } ", "{", "}"), Nil)
    assertEquals(TopLevelSplitter.brackets.elements("4, 5", "[", "]"), List("4", "5"))
  }
}
