package it.evadid.vm.parsing.cpp

import it.evadid.core.datastructures.language.AppLanguage.Cpp
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.conversion.parsed.DefaultDefinitions
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference}
import munit.FunSuite
import upickle.default.{read, write}

final class CppParserSpec extends FunSuite {
  private def variable(name: String, dataType: BeDataType) = BeDefineVariable(BeEntityName.fromUniversalNameInParts(name), dataType)
  private def literal(text: String) = BeUseValue(BeDataValueLiteral(text), None)
  private def reference(v: BeDefineVariable) = BeUseValue(BeUseValueReference(v), None)
  private def body(source: String): Seq[BeExpression] = CppParser.parseCpp(source).body

  /** A parsed call as its function name and raw argument texts. */
  private def call(expression: BeExpression): (BeEntityName, Seq[String]) = expression match {
    case c: BeFunctionCall =>
      val args = c.funcDef.inputs.map(c.parameterValueMap(_)).map {
        case BeUseValue(BeDataValueLiteral(text), Some(_)) => text
        case other => fail(s"Unexpected argument $other")
      }
      (c.funcDef.functionTypeInfo.isNamed.get, args)
    case other => fail(s"Expected a call, got $other")
  }

  private def named(name: String, args: String*): (BeEntityName, Seq[String]) = (BeEntityName.fromUniversalNameInParts(name), args)

  private def assertComparison(condition: BeSequence, symbol: String, left: BeExpression, right: BeExpression): Unit = {
    val operator = DefaultDefinitions.operatorDefinitionsBySymbolAndArity(symbol -> 2)
    assertEquals(condition, BeSequence.conditionalBody(List(BeFunctionCall.fromDefinition(operator,
      Map(operator.inputs(0) -> left, operator.inputs(1) -> right)))))
  }

  test("declarations map C++ types and keep initial values; later assignments reuse the declared variable") {
    val x = variable("x", BeDataType.Int)
    val parsed = body("int x = 5; const double ratio = 0.5; std::string name; bool flag; uint8_t pin = 3; Foo f; String s = \"a\"; x = x;")
    assertEquals(parsed, Seq(
      BeAssignVariable(x, literal("5")),
      BeAssignVariable(variable("ratio", BeDataType.Numeric), literal("0.5")),
      variable("name", BeDataType.String),
      variable("flag", BeDataType.Boolean),
      BeAssignVariable(variable("pin", BeDataType.Int), literal("3")),
      variable("f", BeDataType.AnyType),
      BeAssignVariable(variable("s", BeDataType.String), literal("\"a\"")),
      BeAssignVariable(x, reference(x))))
  }

  test("assignments to unknown variables create untyped variables and boolean literals stay literals") {
    val Seq(led, level) = body("led = true; level = analogRead(A0);")
    assertEquals(led, BeAssignVariable(variable("led", BeDataType.AnyType), literal("true")))
    level match {
      case BeAssignVariable(target, value) =>
        assertEquals(target, variable("level", BeDataType.AnyType))
        assertEquals(call(value), named("analogRead", "A0"))
      case other => fail(s"Expected an assignment, got $other")
    }
  }

  test("calls keep dotted and namespaced names and split top-level arguments only") {
    val parsed = body("Serial.println(\"a, b;}\", max(1, 2)); std::swap(a,b); delay();")
    assertEquals(parsed.map(call), Seq(
      named("Serial.println", "\"a, b;}\"", "max(1, 2)"),
      named("std::swap", "a", "b"),
      named("delay")))
  }

  test("comments are removed but comment markers inside strings are kept") {
    val source = "void setup() {\n  /* setup, see http://example.org */\n  pinMode(13, OUTPUT); // configure\n  Serial.print(\"http://x/*y*/\");\n}"
    assertEquals(body(source).map(call), Seq(named("pinMode", "13", "OUTPUT"), named("Serial.print", "\"http://x/*y*/\"")))
    assertEquals(body("a(); /* unterminated b();"), body("a();"))
    assertEquals(body("say(\"it\\\"s // fine\");").map(call), Seq(named("say", "\"it\\\"s // fine\"")))
  }

  test("unrecognised statements become unsupported placeholders and are reported") {
    val result = CppParser.parseCppWithDiagnostics("x++; a();")
    assertEquals(result.unsupportedStatements, List("x++"))
    assertEquals(result.sequence.body.head, BeExpressionUnsupported("x++", Some(Cpp)))
  }

  test("stray closing braces between Arduino functions are reported instead of hanging the parser") {
    val result = CppParser.parseCppWithDiagnostics("void setup() { a(); } void loop() { b(); }")
    assertEquals(result.sequence.body.headOption.map(call), Some(named("a")))
    assert(result.unsupportedStatements.contains("}"), result.unsupportedStatements)
  }

  test("while loops parse comparisons, calls and identifiers as conditions and scope body variables") {
    val counter = variable("counter", BeDataType.Int)
    val parsed = body("int counter = 0; while (counter < 10) { int y = 1; counter = counter + 1; } y = 2;")
    val BeWhile(condition, loopBody) = parsed(1): @unchecked
    assertComparison(condition, "<", reference(counter), literal("10"))
    assertEquals(loopBody.body, Seq(
      BeAssignVariable(variable("y", BeDataType.Int), literal("1")),
      BeAssignVariable(counter, literal("counter + 1"))))
    assertEquals(parsed(2), BeAssignVariable(variable("y", BeDataType.AnyType), literal("2")))

    val BeWhile(callCondition, emptyBody) = body("while (digitalRead(2)) { }").head: @unchecked
    assertEquals(callCondition.sequenceInfo, BeSequence.conditionalBody(Nil).sequenceInfo)
    assertEquals(callCondition.body.map(call), Seq(named("digitalRead", "2")))
    assertEquals(emptyBody.body, Seq.empty)
    val BeWhile(flagCondition, _) = body("while (running) { }").head: @unchecked
    assertEquals(flagCondition, BeSequence.conditionalBody(List(reference(variable("running", BeDataType.Boolean)))))
    val BeWhile(literalCondition, _) = body("while (true) { }").head: @unchecked
    assertEquals(literalCondition, BeSequence.conditionalBody(List(literal("true"))))
    val BeWhile(emptyCondition, _) = body("while () { }").head: @unchecked
    assertEquals(emptyCondition, BeSequence.conditionalBody(Nil))
  }

  test("if/else-if/else chains nest the following if in the else body and bubble up diagnostics") {
    val result = CppParser.parseCppWithDiagnostics("if (a == 1) { b = 2; } else if (a != 1) { c++; } else { b = 4; }")
    val BeIfElse(condition, thenBody, elseBody) = result.sequence.body.head: @unchecked
    // An undeclared operand is typed by the operator parameter it fills.
    val a = variable("a", DefaultDefinitions.operatorDefinitionsBySymbolAndArity("==" -> 2).inputs.head.variableType)
    assertComparison(condition, "==", reference(a), literal("1"))
    assertEquals(thenBody.body, Seq(BeAssignVariable(variable("b", BeDataType.AnyType), literal("2"))))
    val Seq(BeIfElse(innerCondition, innerThen, innerElse)) = elseBody.body: @unchecked
    assertComparison(innerCondition, "!=", reference(a), literal("1"))
    assertEquals(innerThen.body, Seq(BeExpressionUnsupported("c++", Some(Cpp))))
    assertEquals(innerElse.body, Seq(BeAssignVariable(variable("b", BeDataType.AnyType), literal("4"))))
    assertEquals(result.unsupportedStatements, List("c++"))

    val BeIfElse(_, _, noElse) = body("if (x >= 2) { y(); }").head: @unchecked
    assertEquals(noElse.body, Seq.empty)
  }

  test("nested bodies report their unsupported statements and literals in headers do not confuse the scanner") {
    val result = CppParser.parseCppWithDiagnostics(
      "while (name != \"a)\") { x++; } if (analogRead(0) > 512) { } else { y--; } for (int i = 0; i < 2; i++) { z *= 2; }")
    assertEquals(result.unsupportedStatements, List("x++", "y--", "z *= 2"))
    val Seq(BeWhile(whileCondition, _), BeIfElse(ifCondition, _, _), BeRepeatNr(BeUseValue(BeDataValueLiteral("2"), None), _)) = result.sequence.body: @unchecked
    assertComparison(whileCondition, "!=", reference(variable("name",
      DefaultDefinitions.operatorDefinitionsBySymbolAndArity("!=" -> 2).inputs.head.variableType)), literal("\"a)\""))
    val operator = DefaultDefinitions.operatorDefinitionsBySymbolAndArity(">" -> 2)
    ifCondition.body match {
      case Seq(compare: BeFunctionCall) =>
        assertEquals(call(compare.parameterValueMap(operator.inputs.head)), named("analogRead", "0"))
        assertEquals(compare.parameterValueMap(operator.inputs(1)), literal("512"))
      case other => fail(s"Unexpected condition $other")
    }
    assertEquals(body("for (int i = 0; i < (3); i++) { }"), Seq(BeExpressionUnsupported("for(int i = 0; i < (3); i++){...}", Some(Cpp))))
    val BeWhile(incomplete, _) = body("while (x ==) { }").head: @unchecked
    assertEquals(incomplete, BeSequence.conditionalBody(List(literal("x =="))))
  }

  test("counted for loops become repeat blocks with the computed iteration count") {
    val result = CppParser.parseCppWithDiagnostics("for (int i = 0; i < 10; i++) { tick(); n++; }")
    val BeRepeatNr(ten, loopBody) = result.sequence.body.head: @unchecked
    assertEquals(ten, literal("10"))
    assertEquals(call(loopBody.body.head), named("tick"))
    assertEquals(result.unsupportedStatements, List("n++"))
    val counts = Seq(
      "for (i = 1; i <= 5; i += 1) { }",
      "for (int i = 2; i<7; ++i) { }",
      "for (int i = 0; i < 3; i = i + 1) { }",
      "for (int i = 0; i <= 0; i = 1 + i) { }"
    ).map(source => body(source).head match {
      case BeRepeatNr(BeUseValue(BeDataValueLiteral(amount), _), _) => amount.toInt
      case other => fail(s"$source parsed as $other")
    })
    assertEquals(counts, Seq(5, 5, 3, 1))
  }

  test("for loops that are not simple counters are kept as unsupported source") {
    val sources = Seq(
      "for (int i = 0; i < 10; i += 2) { }",
      "for (;;) { }",
      "for (int i = 0; i < 3) { }",
      "for (int i = 5; i < 2; i++) { }",
      "for (int i = 0; j < 3; i++) { }",
      "for (int i = 0; i > 3; i++) { }",
      "for (int i = start; i < 3; i++) { }",
      "for (int i = 0; i < n; i++) { }")
    sources.foreach { source =>
      val result = CppParser.parseCppWithDiagnostics(source)
      result.sequence.body match {
        case Seq(BeExpressionUnsupported(original, Some(Cpp), _)) =>
          assert(original.startsWith("for(") && original.endsWith("){...}"), original)
          assertEquals(result.unsupportedStatements, List(original))
        case other => fail(s"$source parsed as $other")
      }
    }
  }

  test("control structures without parenthesised headers or braced bodies become unparsable placeholders") {
    val cases = Seq(
      "while x < 3 { }" -> "C++ while: condition parse error: Expected '('",
      "while (x < 3 { }" -> "C++ while: condition parse error: Unclosed '('",
      "while (x) x = 1;" -> "C++ while: body parse error: Expected '{'",
      "while (x) { y();" -> "C++ while: body parse error: Unclosed '{'",
      "if x { }" -> "C++ if: condition parse error: Expected '('",
      "if (x) y();" -> "C++ if: body parse error: Expected '{'",
      "if (x) { } else y();" -> "C++ else: body parse error: Expected '{'",
      "for x { }" -> "C++ for: header parse error: Expected '('",
      "for (int i = 0; i < 2; i++) tick();" -> "C++ for: body parse error: Expected '{'")
    cases.foreach { case (source, message) =>
      val result = CppParser.parseCppWithDiagnostics(source)
      result.sequence.body match {
        case Seq(BeExpressionUnparsable(original, actualMessage, Some(Cpp))) =>
          assertEquals(actualMessage, message, source)
          assertEquals(result.unsupportedStatements, List(original))
          assert(original.nonEmpty)
        case other => fail(s"$source parsed as $other")
      }
    }
  }
}
