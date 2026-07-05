package it.evadid.workbook.vm.parsing.python

import it.evadid.workbook.vm.code.defining.BeDefineFunction
import munit.FunSuite

class PythonKnownDefinitionScannerSpec extends FunSuite {
  test("scanner maps Python identifiers and operators to known definitions") {
    val result = PythonKnownDefinitionScanner.scan("""
      |from turtle import *
      |forward(20 + 3)
      |name = str(42)
      |if name is not None and name not in []:
      |  print(name)
      |""".stripMargin)

    assert(result("forward").exists(_.isInstanceOf[BeDefineFunction]))
    assert(result("+").collect { case fn: BeDefineFunction => fn }.size >= 5)
    assert(result("str").exists(_.isInstanceOf[BeDefineFunction]))
    assert(result("is not").exists(_.isInstanceOf[BeDefineFunction]))
    assert(result("not in").exists(_.isInstanceOf[BeDefineFunction]))
    assert(result("and").exists(_.isInstanceOf[BeDefineFunction]))
    assert(!result.contains("name"))
  }

  test("scanner exposes every potential token before consulting the known definition cache") {
    val tokens = PythonKnownDefinitionScanner.scanTokens("custom_name = value @ matrix # forward(\"ignored\")")

    assert(tokens.contains("custom_name"))
    assert(tokens.contains("value"))
    assert(tokens.contains("@"))
    assert(tokens.contains("matrix"))
    assert(!tokens.contains("ignored"))
  }

  test("scanner maps symbolic boolean operators through the de-duplicated aliases") {
    val result = PythonKnownDefinitionScanner.scan("ready && valid and other || more or ! stopped not done")

    assertEquals(result("&&"), result("and"))
    assertEquals(result("||"), result("or"))
    assertEquals(result("!"), result("not"))
  }
}
