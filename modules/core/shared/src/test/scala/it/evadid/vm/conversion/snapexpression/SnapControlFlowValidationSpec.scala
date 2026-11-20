package it.evadid.vm.conversion.snapexpression

import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.vm.code.controlStructures.{BeFor, BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeAssignVariable, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import munit.FunSuite

class SnapControlFlowValidationSpec extends FunSuite {
  private def literal(value: String) = BeUseValue(BeDataValueLiteral(value), None)
  private val counter = BeDefineVariable(BeEntityName.fromLiteral("i"), BeDataType.AnyType)
  private val unsupported = BeExpressionUnsupported("print(x)")
  private def body(values: BeUseValue | BeExpressionUnsupported*) = BeSequence.optionalBody(values.toList)

  test("variable names are interned once and empty names fall back to x") {
    val interner = SnapControlFlow.VariableInterner.fromNames(Seq(" size ", "", "angle"))
    assertEquals(interner.names, List("size", "angle"))
    assert(interner.intern("size") eq interner.intern(" size"))
    assertEquals(SnapControlFlow.variableName(interner.intern("  ")), "x")
    assertEquals(interner.names, List("size", "angle", "x"))
  }

  test("unsupported statements are described by the construct that blocks Snap export") {
    val describe = SnapControlFlow.describeUnsupportedStatement(_)
    assertEquals(describe(BeRepeatNr.times(-1, body())), Some("negative repeat count"))
    assertEquals(describe(BeRepeatNr.times(2, body(unsupported))), Some("print(x)"))
    assertEquals(describe(BeFor(counter, BeSequence.optionalBody(Nil), literal("3"), body())), Some("for range"))
    assertEquals(describe(BeFor(counter, literal("1"), literal("3"), body())), None)
    assertEquals(describe(BeSequence.optionalBody(List(body(), BeExpressionUnparsable("x ==", "bad")))), Some("x =="))
    assertEquals(describe(BeSingleLineComment(LanguageMap.universalMap("note"))), None)
    assertEquals(describe(BeDefineVariable(BeEntityName.fromLiteral("v"), BeDataType.AnyType, Some(literal("1")))), None)
    assertEquals(describe(BeDefineVariable(BeEntityName.fromLiteral("v"), BeDataType.AnyType, Some(body()))), Some("v = ..."))
    assertEquals(describe(BeAssignVariable(counter, literal("2"))), None)
    assertEquals(SnapControlFlow.validateStatements(List(BeAssignVariable(counter, literal("2")))).isRight, true)
    assert(SnapControlFlow.validateStatements(List(BeRepeatNr.times(-1, body()))).isLeft)
  }

  test("supported conditions accept literals, variables and nested operators only") {
    assert(SnapControlFlow.isSupportedCondition(literal("True")))
    assert(SnapControlFlow.isSupportedCondition(literal("-1.5")))
    assert(!SnapControlFlow.isSupportedCondition(literal("\"text\"")))
    assert(!SnapControlFlow.isSupportedCondition(body()))
    val comparison = SnapControlFlow.operatorCall("<", List(SnapControlFlow.useVariable(counter), literal("3")))
    assert(SnapControlFlow.isSupportedCondition(SnapControlFlow.operatorCall("not", List(comparison))))
    assert(!SnapControlFlow.isSupportedCondition(SnapControlFlow.operatorCall("<<", List(literal("2"), literal("3")))))
    assert(SnapControlFlow.isSnapValueReporter("reportNot"))
    assert(!SnapControlFlow.isSnapValueReporter("forward"))
  }

  test("conditions are inverted without double negation") {
    assertEquals(SnapControlFlow.invertCondition(literal("True")), literal("False"))
    assertEquals(SnapControlFlow.invertCondition(literal("False")), literal("True"))
    val comparison = SnapControlFlow.operatorCall("<", List(literal("1"), literal("2")))
    assertEquals(SnapControlFlow.invertCondition(SnapControlFlow.invertCondition(comparison)), comparison)
  }

  test("statement checks look into programs, sequences and loop bodies") {
    val program = BeStartProgram(BeSequence.optionalBody(List(BeWhile(BeSequence.conditionalBody(List(literal("True"))), body(unsupported)))))
    assertEquals(SnapControlFlow.topLevelStatements(BeStartProgram(None)), Nil)
    assert(!SnapControlFlow.hasSupportedStatements(program))
    assert(!SnapControlFlow.containsSupportedStatement(BeSequence.optionalBody(List(unsupported))))
    assert(SnapControlFlow.containsSupportedStatement(BeSequence.optionalBody(List(BeAssignVariable(counter, literal("1"))))))
    assertEquals(SnapControlFlow.describeUnsupportedStatement(program.startSequence.get), Some("print(x)"))
    val branch = BeIfElse(BeSequence.conditionalBody(List(literal("True"))), body(), body(unsupported))
    assertEquals(SnapControlFlow.describeUnsupportedStatement(branch), Some("print(x)"))
    assertEquals(SnapControlFlow.collectVariableNames(BeSequence.optionalBody(List(BeAssignVariable(counter, literal("1"))))), List("i"))
  }
}
