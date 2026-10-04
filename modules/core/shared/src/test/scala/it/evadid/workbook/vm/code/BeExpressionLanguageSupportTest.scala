package it.evadid.workbook.vm.code

import it.evadid.vm.code.defining.BeDefineFunction.functionInfo
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.simulation.java.JavaInt32
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import munit.FunSuite

class BeExpressionLanguageSupportTest extends FunSuite {

  private val targetLanguages = List(Python, Java, Lisp, Cpp)
  private val humanLanguage = English

  private val xVar = BeDefineVariable(BeEntityName.fromUniversalNameInParts("x"), BeDataType.Int)
  private val yVar = BeDefineVariable(BeEntityName.fromUniversalNameInParts("y"), BeDataType.Int)
  private val boolVar = BeDefineVariable(BeEntityName.fromUniversalNameInParts("ok"), BeDataType.Boolean)

  private val literalOne = BeUseValue(BeDataValueLiteral("1"), Some(xVar))
  private val literalTwo = BeUseValue(BeDataValueLiteral("2"), Some(yVar))
  private val literalTrue = BeUseValue(BeDataValueLiteral("true"), Some(boolVar))

  private val assignX = BeAssignVariable(xVar, literalOne)
  private val returnX = BeReturn(Some(BeUseValue(BeDataValueLiteral("x"), Some(xVar))))
  private val sequence = BeSequence.optionalBody(List(assignX, returnX))

  private val function = BeDefineFunction(
    inputs = List(xVar, yVar),
    outputs = Some(BeDefineVariable(BeEntityName.fromUniversalNameInParts("result"), BeDataType.Int)),
    body = BeSequence.optionalBody(List(returnX)),
    functionTypeInfo = functionInfo(BeEntityName.fromUniversalNameInParts("add"))
  )

  private val functionCall = BeFunctionCall(function, Map(xVar -> literalOne, yVar -> literalTwo))

  private val allExpressions: List[BeExpression] = List(
    xVar,
    function,
    BeDefineClass(BeEntityName.fromUniversalNameInParts("Counter"), attributes = List(xVar), methods = List(function)),
    assignX,
    BeUseValue(BeDataValueLiteral("7"), Some(xVar)),
    functionCall,
    BeReturn(Some(literalOne)),
    BeStartProgram(Some(sequence)),
    sequence,
    BeIfElse(BeSequence.conditionalBody(List(literalTrue)), BeSequence.optionalBody(List(assignX)), BeSequence.optionalBody(List(returnX))),
    BeWhile(BeSequence.conditionalBody(List(literalTrue)), BeSequence.optionalBody(List(assignX))),
    BeRepeatNr(3, BeSequence.optionalBody(List(assignX))),
    BeExpressionUnsupported("unsupported"),
    BeExpressionUnparsable("raw", "bad syntax"),
    BeSingleLineComment(LanguageMap.universalMap("note"))
  )

  test("all BeExpression subclasses render for Python, Java, Lisp, and C++") {
    allExpressions.foreach { expr =>
      targetLanguages.foreach { language =>
        val rendered = expr.structureInfo.toStringInLanguage(language, humanLanguage)
        assert(rendered.trim.nonEmpty, s"${expr.getClass.getSimpleName} should render non-empty for ${language.name}")
      }
    }
  }

  test("Python rendering includes type hints for variable and function signatures") {
    val renderedVariable = xVar.structureInfo.toStringInLanguage(Python, humanLanguage)
    val renderedFunction = function.structureInfo.toStringInLanguage(Python, humanLanguage)

    assert(renderedVariable.contains(":"), s"Expected Python variable hint in: $renderedVariable")
    assert(renderedFunction.contains("def add("), clues(renderedFunction))
    assert(renderedFunction.contains("x: int"), clues(renderedFunction))
    assert(renderedFunction.contains("y: int"), clues(renderedFunction))
    assert(renderedFunction.contains("-> int"), clues(renderedFunction))
  }

  test("Python sequence rendering matches expected string exactly") {
    val scripted = BeSequence.optionalBody(List(
      BeAssignVariable(xVar, literalOne),
      BeAssignVariable(yVar, literalTwo),
      BeIfElse(
        BeSequence.conditionalBody(List(literalTrue)),
        BeSequence.optionalBody(List(BeAssignVariable(xVar, literalTwo))),
        BeSequence.optionalBody(List(BeAssignVariable(yVar, literalOne)))
      )
    ))

    val rendered = scripted.structureInfo.toStringInLanguage(Python, humanLanguage)

    val expected =
      """x: int = 1
        |y: int = 2
        |if true:
        |    x: int = 2
        |else:
        |    y: int = 1
        |""".stripMargin

    assertEquals(rendered, expected)
  }

  test("Java sequence rendering matches expected string ignoring surrounding whitespace") {
    val scripted = BeSequence.optionalBody(List(
      BeAssignVariable(xVar, literalOne),
      BeAssignVariable(yVar, literalTwo),
      BeIfElse(
        BeSequence.conditionalBody(List(literalTrue)),
        BeSequence.optionalBody(List(BeAssignVariable(xVar, literalTwo))),
        BeSequence.optionalBody(List(BeAssignVariable(yVar, literalOne)))
      )
    ))

    val expected =
      """int x = 1;
        |int y = 2;
        |if(true){
        |    int x = 2;
        |} else {
        |    int y = 1;
        |}
        |""".stripMargin

    assertEquals(scripted.structureInfo.toStringInLanguage(Java, humanLanguage).trim, expected.trim)
  }

  test("Lisp sequence rendering matches expected string exactly") {
    val scripted = BeSequence.optionalBody(List(
      BeAssignVariable(xVar, literalOne),
      BeAssignVariable(yVar, literalTwo),
      BeIfElse(
        BeSequence.conditionalBody(List(literalTrue)),
        BeSequence.optionalBody(List(BeAssignVariable(xVar, literalTwo))),
        BeSequence.optionalBody(List(BeAssignVariable(yVar, literalOne)))
      )
    ))

    val expected =
      """(progn
        |    (setf x 1)
        |    (setf y 2)
        |    (if (progn    true)
        |        (progn
        |            (progn
        |                (setf x 2)
        |            )
        |        )
        |        (progn
        |            (progn
        |                (setf y 1)
        |            )
        |        )
        |    )
        |)""".stripMargin

    assertEquals(scripted.structureInfo.toStringInLanguage(Lisp, humanLanguage), expected)
  }

  test("C++ sequence rendering matches expected string ignoring surrounding whitespace") {
    val scripted = BeSequence.optionalBody(List(
      BeAssignVariable(xVar, literalOne),
      BeAssignVariable(yVar, literalTwo),
      BeIfElse(
        BeSequence.conditionalBody(List(literalTrue)),
        BeSequence.optionalBody(List(BeAssignVariable(xVar, literalTwo))),
        BeSequence.optionalBody(List(BeAssignVariable(yVar, literalOne)))
      )
    ))

    val expected =
      """int x = 1;
        |int y = 2;
        |if(true){
        |    int x = 2;
        |} else {
        |    int y = 1;
        |}
        |""".stripMargin

    assertEquals(scripted.structureInfo.toStringInLanguage(Cpp, humanLanguage).trim, expected.trim)
  }

  test("Java int arithmetic wraps without changing the legacy numeric types") {
    assertEquals(JavaInt32.add(Int.MaxValue, 1), Int.MinValue)
    assertEquals(JavaInt32.subtract(Int.MinValue, 1), Int.MaxValue)
    assertEquals(JavaInt32.multiply(Int.MaxValue, Int.MaxValue), 1)
    assertEquals(JavaInt32.multiply(65536, 65536), 0)
    assertEquals(JavaInt32.negate(Int.MinValue), Int.MinValue)
    assert(BeDataType.Int.isValidLiteral("2147483648"))
    assertEquals(BeDataValueLiteral("1").currentType, BeDataType.Numeric)
  }

  test("Java int division truncates toward zero and remainder retains the dividend sign") {
    for (left, right, quotient, remainder) <- Seq(
      (Int.MinValue, -1, Int.MinValue, 0), (-9, 2, -4, -1), (9, -2, -4, 1), (-9, -2, 4, -1)
    ) do {
      assertEquals(JavaInt32.divide(left, right), Right(quotient))
      assertEquals(JavaInt32.remainder(left, right), Right(remainder))
    }
  }

  test("Java int zero divisors return an explicit arithmetic error") {
    for value <- Seq(Int.MinValue, -1, 0, 1, Int.MaxValue) do {
      assertEquals(JavaInt32.divide(value, 0), Left(JavaInt32.Error.DivisionByZero))
      assertEquals(JavaInt32.remainder(value, 0), Left(JavaInt32.Error.DivisionByZero))
    }
  }

  test("Java int operations match an independent unbounded integer model") {
    val modulus = BigInt(1) << 32
    def wrapped(value: BigInt): Int = {
      val unsigned = ((value % modulus) + modulus) % modulus
      (if unsigned > Int.MaxValue then unsigned - modulus else unsigned).toInt
    }
    val edges = Vector(Int.MinValue, Int.MinValue + 1, -1000000000, -65536, -9, -2, -1,
      0, 1, 2, 9, 65536, 1000000000, Int.MaxValue)
    var state = BigInt("13579bdf", 16)
    def sample(): Int = {
      state = (state * 1664525 + 1013904223) % modulus
      wrapped(state)
    }
    val pairs = (for left <- edges; right <- edges yield left -> right) ++ Vector.fill(512)(sample() -> sample())
    for (left, right) <- pairs do {
      val a = BigInt(left)
      val b = BigInt(right)
      assertEquals(JavaInt32.add(left, right), wrapped(a + b), clue = (left, right))
      assertEquals(JavaInt32.subtract(left, right), wrapped(a - b), clue = (left, right))
      assertEquals(JavaInt32.multiply(left, right), wrapped(a * b), clue = (left, right))
      assertEquals(JavaInt32.negate(left), wrapped(-a), clue = left)
      if right == 0 then {
        assertEquals(JavaInt32.divide(left, right), Left(JavaInt32.Error.DivisionByZero))
        assertEquals(JavaInt32.remainder(left, right), Left(JavaInt32.Error.DivisionByZero))
      } else {
        assertEquals(JavaInt32.divide(left, right), Right(wrapped(a / b)), clue = (left, right))
        assertEquals(JavaInt32.remainder(left, right), Right(wrapped(a % b)), clue = (left, right))
      }
    }
    assertEquals(pairs.size, 708)
  }
}
