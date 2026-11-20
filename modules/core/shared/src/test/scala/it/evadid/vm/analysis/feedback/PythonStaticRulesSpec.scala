package it.evadid.vm.analysis.feedback

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

  private def rule(code: String, id: String): RuleResult =
    PythonStaticRules.runAll(code, it.evadid.core.datastructures.language.AppLanguage.English)
      .find(_.id == id).getOrElse(fail(s"Expected rule $id"))

  test("metrics count non-empty lines and report trailing whitespace and blank-line streaks by line") {
    val code = "x = 1  \n\n\n\n\ny = 2\t\nprint(x)"
    assertEquals(rule(code, "PY_LINECOUNT").details, Some("3"))
    val trailing = rule(code, "PY_TRAILING_WHITESPACE")
    assert(!trailing.passed)
    assertEquals(trailing.details, Some("Zeilen: 1, 6"))
    val blank = rule(code, "PY_BLANK_LINES")
    assert(!blank.passed)
    assertEquals(blank.details, Some("Maximaler Block leerer Zeilen: 4"))
    val tidy = "x = 1\n\ny = 2\n"
    assert(rule(tidy, "PY_TRAILING_WHITESPACE").passed)
    assert(rule(tidy, "PY_BLANK_LINES").passed)
    assert(rule(tidy, "PY_LONG_LINES").passed)
  }

  test("risky runtime calls produce warnings only when present") {
    val code = "import time, random\nwhile True:\n    time.sleep(1)\n    x = input()\n    print(random.choice([1]))\n"
    val ids = PythonStaticRules.runAll(code).map(_.id).toSet
    assert(Set("PY_INPUT_USAGE", "PY_WHILE_TRUE", "PY_SLEEP_USAGE", "PY_RANDOM_USAGE").subsetOf(ids))
    val quiet = PythonStaticRules.runAll("x = 1").map(_.id).toSet
    assert(Set("PY_INPUT_USAGE", "PY_WHILE_TRUE", "PY_SLEEP_USAGE", "PY_RANDOM_USAGE").intersect(quiet).isEmpty)
    assert(!quiet.contains("PY_UNUSED_IMPORTS"))
    assert(!quiet.contains("PY_MISSING_RETURN"))
  }

  test("print spam is reported above three print calls") {
    val many = (1 to 4).map(i => s"print($i)").mkString("\n")
    val spam = rule(many, "PY_PRINT_SPAM")
    assert(!spam.passed)
    assertEquals(spam.details, Some("4"))
    assert(rule("print(1)\nprint (2)", "PY_PRINT_SPAM").passed)
  }

  test("messages follow the requested human language") {
    val german = PythonStaticRules.runAll("", it.evadid.core.datastructures.language.AppLanguage.German).head
    assert(german.message.startsWith("Es wurde kein Python-Code"))
    val english = PythonStaticRules.runAll(null, it.evadid.core.datastructures.language.AppLanguage.English).head
    assertEquals(english.id, "PY_EMPTY")
    assert(english.message.startsWith("No Python code found"))
  }

  test("unused imports honour aliases, multiple modules and from-imports") {
    assert(rule("import math as m\nprint(m.sqrt(4))", "PY_UNUSED_IMPORTS").passed)
    assert(rule("import numpy as np, os.path\nnp.zeros(os.path.sep)", "PY_UNUSED_IMPORTS").passed)
    assert(rule("from math import sqrt as root, pi\nprint(root(pi))", "PY_UNUSED_IMPORTS").passed)
    assert(rule("from math import *\nprint(pi)", "PY_UNUSED_IMPORTS").passed)
    assert(rule("from math import (floor,\n    ceil)\nprint(floor(1))", "PY_UNUSED_IMPORTS").passed)

    val unused = rule("import os, sys\nimport math as m\nfrom math import pi, tau\nprint(os.name, pi, sys)", "PY_UNUSED_IMPORTS")
    assert(!unused.passed)
    assertEquals(unused.details, Some("line 2: 'm', line 3: 'tau'"))
    assertEquals(rule("from math import pi\npipe = 1", "PY_UNUSED_IMPORTS").details, Some("line 1: 'pi'"))
    assertEquals(rule("import math\nfrom math import pi\nprint(pi)", "PY_UNUSED_IMPORTS").details, Some("line 1: 'math'"))
  }

  test("missing return checks only each top-level function body") {
    val code = "def helper(x):\n    print(x)\n\ndef stub():\n    pass\n\nprint(helper(1))\n"
    val missing = rule(code, "PY_MISSING_RETURN")
    assert(!missing.passed)
    assertEquals(missing.details, Some("'helper' (line 1)"))

    val ok = "async def load():\n    value = 1\n    return value\n\ndef empty():\n    # todo\n    ...\n"
    assert(rule(ok, "PY_MISSING_RETURN").passed)
    val commentOnly = "def f():\n    x = 1  # return later\n    returned = x\n"
    assertEquals(rule(commentOnly, "PY_MISSING_RETURN").details, Some("'f' (line 1)"))
    assert(rule("def f(x):\n    if x: return 1\n    return 2", "PY_MISSING_RETURN").passed)
  }
}
