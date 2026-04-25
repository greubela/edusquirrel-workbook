package interactionPlugins.blockEnvironment.feedback.rules

import munit.FunSuite

final class PythonStaticRulesSpec extends FunSuite {

  test("PY_EMPTY should be reported for empty code") {
    val rules = PythonStaticRules.runAll("   \n  ")
    val emptyRule = rules.find(_.id == "PY_EMPTY").getOrElse(
      fail("Expected PY_EMPTY rule for empty code.")
    )
    assert(!emptyRule.passed, "PY_EMPTY should not pass for empty code.")
  }

  test("PY_NON_EMPTY should be reported for non-empty code") {
    val rules = PythonStaticRules.runAll("x = 1\nprint(x)\n")
    val nonEmptyRule = rules.find(_.id == "PY_NON_EMPTY").getOrElse(
      fail("Expected PY_NON_EMPTY rule for non-empty code.")
    )
    assert(nonEmptyRule.passed, "PY_NON_EMPTY should pass for non-empty code.")
  }

  test("PY_LONG_LINES should warn when a line is very long") {
    val longLine = List.fill(120)("a").mkString
    val rules    = PythonStaticRules.runAll(longLine)

    val longRule = rules.find(_.id == "PY_LONG_LINES").getOrElse(
      fail("Expected PY_LONG_LINES rule.")
    )

    assert(!longRule.passed, "PY_LONG_LINES should not pass when there is a very long line.")
  }
}
