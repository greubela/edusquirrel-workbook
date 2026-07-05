package it.evadid.workbook.vm.naming

import munit.FunSuite

class KnownBeEntityNamesSpec extends FunSuite {
  test("known names are separated by role and support lookup aliases") {
    assert(KnownBeEntityNames.operatorNames.exists(_.universalInterpretation() == "+"))
    assert(KnownBeEntityNames.methodNames.exists(_.universalInterpretation() == "forward"))
    assert(KnownBeEntityNames.classNames.exists(_.universalInterpretation() == "turtle"))
    assert(KnownBeEntityNames.classNames.exists(_.universalInterpretation() == "string"))

    assertEquals(KnownBeEntityNames.byName("+").map(_.universalInterpretation()), Some("+"))
    assertEquals(KnownBeEntityNames.byName("forward").map(_.universalInterpretation()), Some("forward"))
    assertEquals(KnownBeEntityNames.byName("Integer").map(_.universalInterpretation()), Some("integer"))
  }

  test("role stores are populated with non-empty unique names") {
    val roleStores = List(
      "classes" -> KnownBeEntityNames.classNames,
      "functions" -> KnownBeEntityNames.functionNames,
      "methods" -> KnownBeEntityNames.methodNames,
      "operators" -> KnownBeEntityNames.operatorNames
    )

    roleStores.foreach { case (role, names) =>
      assert(names.nonEmpty, s"$role should not be empty")
      assert(names.forall(_.universalInterpretation().nonEmpty), s"$role should not contain empty names")
      assertEquals(names.map(_.universalInterpretation()).distinct.size, names.size, s"$role should not contain duplicate names")
    }
  }

  test("turtle commands are methods, not standalone functions") {
    val functionNames = KnownBeEntityNames.functionNames.map(_.universalInterpretation()).toSet
    val methodNames = KnownBeEntityNames.methodNames.map(_.universalInterpretation()).toSet

    assert(methodNames.contains("forward"))
    assert(methodNames.contains("pen_up"))
    assert(!functionNames.contains("forward"))
    assert(!functionNames.contains("pen_up"))
  }

  test("symbolic and Python word operator aliases de-duplicate to the same BeEntityName object") {
    val symbolicAnd = KnownBeEntityNames.byName("&&").get
    val pythonAnd = KnownBeEntityNames.byName("and").get
    val symbolicOr = KnownBeEntityNames.byName("||").get
    val pythonOr = KnownBeEntityNames.byName("or").get
    val symbolicNot = KnownBeEntityNames.byName("!").get
    val pythonNot = KnownBeEntityNames.byName("not").get

    assert(symbolicAnd eq pythonAnd)
    assert(symbolicOr eq pythonOr)
    assert(symbolicNot eq pythonNot)
  }
}
