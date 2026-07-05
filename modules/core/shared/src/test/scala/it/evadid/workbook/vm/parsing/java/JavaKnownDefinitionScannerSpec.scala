package it.evadid.workbook.vm.parsing.java

import it.evadid.workbook.vm.code.defining.{BeDefineClass, BeDefineFunction, KnownBeDefineStructures}
import munit.FunSuite

class JavaKnownDefinitionScannerSpec extends FunSuite {
  test("scanner maps Java identifiers and operators to known definitions") {
    val result = JavaKnownDefinitionScanner.scan("""
      |class Demo {
      |  void run() {
      |    String name = "ignored + forward";
      |    if (ready && !stopped || count > 2) {
      |      System.out.println(name);
      |    }
      |  }
      |}
      |""".stripMargin)

    assert(result("String").exists(_.isInstanceOf[BeDefineClass]))
    assertEquals(result("&&"), KnownBeDefineStructures.byName("and"))
    assertEquals(result("||"), KnownBeDefineStructures.byName("or"))
    assertEquals(result("!"), KnownBeDefineStructures.byName("not"))
    assert(result(">").exists(_.isInstanceOf[BeDefineFunction]))
    assert(result("println").exists(_.isInstanceOf[BeDefineFunction]))
    assert(!result.contains("ignored"))
    assert(!result.contains("forward"))
  }

  test("scanner exposes unknown Java identifiers before cache filtering") {
    val tokens = JavaKnownDefinitionScanner.scanTokens("customValue = matrix.multiply(other); // String ignored")

    assert(tokens.contains("customValue"))
    assert(tokens.contains("matrix"))
    assert(tokens.contains("multiply"))
    assert(tokens.contains("other"))
    assert(tokens.contains("="))
    assert(!tokens.contains("ignored"))
  }
}
