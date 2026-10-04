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
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleSemantics, JavaTurtleSource, JavaTurtleStructure}
import it.evadid.vm.simulation.java.{JavaInt32, JavaTurtleEvaluation as E}
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import munit.FunSuite

class BeExpressionLanguageSupportTest extends FunSuite {

  private def javaVariable(index: Int, valueType: R.ValueType): R.Variable =
    R.Variable(R.VariableId(R.MethodId(0), index), s"v$index", valueType)

  private def javaResult(expression: R.Expression, values: Map[R.VariableId, E.Value] = Map.empty): Either[E.Failure, E.Value] =
    E.evaluate(expression, variable => values.get(variable.id).toRight(E.Failure.MissingValue(variable.id)))

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

  test("Java expression arithmetic delegates to int32 rules and returns explicit failures") {
    for (operator, left, right, result) <- Seq(
      (R.BinaryOperator.Add, Int.MaxValue, 1, Int.MinValue),
      (R.BinaryOperator.Subtract, Int.MinValue, 1, Int.MaxValue),
      (R.BinaryOperator.Multiply, Int.MaxValue, Int.MaxValue, 1),
      (R.BinaryOperator.Divide, Int.MinValue, -1, Int.MinValue),
      (R.BinaryOperator.Divide, -9, 2, -4), (R.BinaryOperator.Remainder, -9, 2, -1)
    ) do assertEquals(javaResult(R.Binary(operator, R.IntLiteral(left), R.IntLiteral(right))), Right(E.Value.IntValue(result)))
    for operator <- Seq(R.BinaryOperator.Divide, R.BinaryOperator.Remainder) do
      assertEquals(javaResult(R.Binary(operator, R.IntLiteral(1), R.IntLiteral(0))), Left(E.Failure.DivisionByZero))
    assertEquals(javaResult(R.Unary(R.UnaryOperator.Plus, R.IntLiteral(9))), Right(E.Value.IntValue(9)))
    assertEquals(javaResult(R.Unary(R.UnaryOperator.Negate, R.IntLiteral(Int.MinValue))), Right(E.Value.IntValue(Int.MinValue)))
    assertEquals(javaResult(R.Unary(R.UnaryOperator.Not, R.BooleanLiteral(true))), Right(E.Value.BooleanValue(false)))
  }

  test("Java comparisons and equality use strict scalar types") {
    for (operator, expected) <- Seq(R.BinaryOperator.Less -> true, R.BinaryOperator.LessEqual -> true,
      R.BinaryOperator.Greater -> false, R.BinaryOperator.GreaterEqual -> false,
      R.BinaryOperator.Equal -> false, R.BinaryOperator.NotEqual -> true) do
      assertEquals(javaResult(R.Binary(operator, R.IntLiteral(Int.MinValue), R.IntLiteral(Int.MaxValue))),
        Right(E.Value.BooleanValue(expected)))
    for left <- Seq(false, true); right <- Seq(false, true) do {
      assertEquals(javaResult(R.Binary(R.BinaryOperator.Equal, R.BooleanLiteral(left), R.BooleanLiteral(right))),
        Right(E.Value.BooleanValue(left == right)))
      assertEquals(javaResult(R.Binary(R.BinaryOperator.NotEqual, R.BooleanLiteral(left), R.BooleanLiteral(right))),
        Right(E.Value.BooleanValue(left != right)))
    }
    for expression <- Seq(
      R.Binary(R.BinaryOperator.Equal, R.IntLiteral(1), R.BooleanLiteral(true)),
      R.Binary(R.BinaryOperator.Add, R.BooleanLiteral(true), R.IntLiteral(1)),
      R.Binary(R.BinaryOperator.Less, R.BooleanLiteral(false), R.BooleanLiteral(true)),
      R.Unary(R.UnaryOperator.Not, R.IntLiteral(1)), R.Unary(R.UnaryOperator.Negate, R.BooleanLiteral(true))
    ) do assertEquals(javaResult(expression), Left(E.Failure.TypeMismatch))
  }

  test("Java reads reject missing or wrongly typed values and the main argument wrapper") {
    val number = javaVariable(0, R.ValueType.IntValue)
    val flag = javaVariable(1, R.ValueType.BooleanValue)
    assertEquals(javaResult(R.Read(number)), Left(E.Failure.MissingValue(number.id)))
    assertEquals(javaResult(R.Read(number), Map(number.id -> E.Value.BooleanValue(true))), Left(E.Failure.TypeMismatch))
    assertEquals(javaResult(R.Read(flag), Map(flag.id -> E.Value.IntValue(1))), Left(E.Failure.TypeMismatch))
    assertEquals(javaResult(R.Read(number), Map(number.id -> E.Value.IntValue(40))), Right(E.Value.IntValue(40)))
    assertEquals(javaResult(R.Read(flag), Map(flag.id -> E.Value.BooleanValue(true))), Right(E.Value.BooleanValue(true)))
    assertEquals(E.evaluate(R.Read(javaVariable(2, R.ValueType.MainArguments)), _ => fail("Wrapper must not be read")),
      Left(E.Failure.TypeMismatch))
  }

  test("Java short-circuit truth tables read each required operand exactly once") {
    val a = javaVariable(0, R.ValueType.BooleanValue)
    val b = javaVariable(1, R.ValueType.BooleanValue)
    for operator <- Seq(R.ShortCircuitOperator.And, R.ShortCircuitOperator.Or);
        left <- Seq(false, true); right <- Seq(false, true) do {
      var trace = Vector.empty[R.VariableId]
      val result = E.evaluate(R.ShortCircuit(operator, R.Read(a), R.Read(b)), variable => {
        trace :+= variable.id
        Right(E.Value.BooleanValue(if variable.id == a.id then left else right))
      })
      val useRight = if operator == R.ShortCircuitOperator.And then left else !left
      val expected = if operator == R.ShortCircuitOperator.And then left && right else left || right
      assertEquals(result, Right(E.Value.BooleanValue(expected)))
      assertEquals(trace, if useRight then Vector(a.id, b.id) else Vector(a.id))
    }
  }

  test("Java short circuits skip throwing readers and arithmetic failures") {
    val flag = javaVariable(0, R.ValueType.BooleanValue)
    val division = R.Binary(R.BinaryOperator.Greater,
      R.Binary(R.BinaryOperator.Divide, R.IntLiteral(1), R.IntLiteral(0)), R.IntLiteral(0))
    for (operator, left) <- Seq(R.ShortCircuitOperator.And -> false, R.ShortCircuitOperator.Or -> true) do {
      assertEquals(E.evaluate(R.ShortCircuit(operator, R.BooleanLiteral(left), R.Read(flag)), _ => fail("Skipped reader")),
        Right(E.Value.BooleanValue(left)))
      assertEquals(javaResult(R.ShortCircuit(operator, R.BooleanLiteral(left), division)), Right(E.Value.BooleanValue(left)))
      assertEquals(javaResult(R.ShortCircuit(operator, R.BooleanLiteral(!left), division)), Left(E.Failure.DivisionByZero))
      assertEquals(javaResult(R.ShortCircuit(operator, R.BooleanLiteral(!left), R.Read(flag))), Left(E.Failure.MissingValue(flag.id)))
    }
  }

  test("Java nested short circuits and boolean equality retain their distinct reading behavior") {
    val a = javaVariable(0, R.ValueType.BooleanValue)
    val b = javaVariable(1, R.ValueType.BooleanValue)
    val c = javaVariable(2, R.ValueType.BooleanValue)
    var trace = Vector.empty[R.VariableId]
    val nested = R.ShortCircuit(R.ShortCircuitOperator.Or,
      R.ShortCircuit(R.ShortCircuitOperator.And, R.Read(a), R.Read(b)), R.Read(c))
    assertEquals(E.evaluate(nested, variable => {
      trace :+= variable.id
      if variable.id == b.id then fail("Skipped nested read")
      Right(E.Value.BooleanValue(variable.id == c.id))
    }), Right(E.Value.BooleanValue(true)))
    assertEquals(trace, Vector(a.id, c.id))
    trace = Vector.empty
    assertEquals(E.evaluate(R.Binary(R.BinaryOperator.Equal, R.Read(a), R.Read(b)), variable => {
      trace :+= variable.id
      Right(E.Value.BooleanValue(false))
    }), Right(E.Value.BooleanValue(true)))
    assertEquals(trace, Vector(a.id, b.id))
  }

  test("Java expression failures stop later reads without returning fallback values") {
    val a = javaVariable(0, R.ValueType.IntValue)
    val b = javaVariable(1, R.ValueType.IntValue)
    val c = javaVariable(2, R.ValueType.IntValue)
    var trace = Vector.empty[R.VariableId]
    val read: E.Reader = variable => {
      trace :+= variable.id
      Right(E.Value.IntValue(if variable.id == b.id then 0 else 1))
    }
    assertEquals(E.evaluate(R.Binary(R.BinaryOperator.Add, R.Read(a), R.Read(c)), read), Right(E.Value.IntValue(2)))
    assertEquals(trace, Vector(a.id, c.id))
    trace = Vector.empty
    val failing = R.Binary(R.BinaryOperator.Add, R.Binary(R.BinaryOperator.Divide, R.Read(a), R.Read(b)), R.Read(c))
    assertEquals(E.evaluate(failing, read), Left(E.Failure.DivisionByZero))
    assertEquals(trace, Vector(a.id, b.id))
    assertEquals(E.evaluate(R.Binary(R.BinaryOperator.Add, R.BooleanLiteral(false), R.Read(c)), _ => fail("Invalid LHS stops RHS")),
      Left(E.Failure.TypeMismatch))
    assertEquals(E.evaluate(R.ShortCircuit(R.ShortCircuitOperator.And, R.IntLiteral(1), R.Read(c)), _ => fail("Invalid boolean LHS")),
      Left(E.Failure.TypeMismatch))
  }

  test("Java expression budgets reject excessive work before extra reads and reset between calls") {
    val a = javaVariable(0, R.ValueType.IntValue)
    val b = javaVariable(1, R.ValueType.IntValue)
    val sum = R.Binary(R.BinaryOperator.Add, R.Read(a), R.Read(b))
    var trace = Vector.empty[R.VariableId]
    val read: E.Reader = variable => { trace :+= variable.id; Right(E.Value.IntValue(1)) }
    assertEquals(E.evaluate(sum, read, E.Limits(maxNodes = 2)), Left(E.Failure.LimitExceeded))
    assertEquals(trace, Vector(a.id))
    trace = Vector.empty
    assertEquals(E.evaluate(sum, read, E.Limits(maxNodes = 3)), Right(E.Value.IntValue(2)))
    assertEquals(trace, Vector(a.id, b.id))
    assertEquals(E.evaluate(R.Group(R.Read(a)), _ => fail("Depth limit"), E.Limits(maxDepth = 1)), Left(E.Failure.LimitExceeded))
    assertEquals(E.evaluate(R.Group(R.Read(a)), read, E.Limits(maxDepth = 2)), Right(E.Value.IntValue(1)))
    val deep = (1 to 512).foldLeft[R.Expression](R.IntLiteral(1))((value, _) => R.Group(value))
    assertEquals(javaResult(deep), Left(E.Failure.LimitExceeded))
    assertEquals(E.evaluate(R.IntLiteral(40), read, E.Limits(maxNodes = 1)), Right(E.Value.IntValue(40)))
    for limits <- Seq(E.Limits(maxDepth = 0), E.Limits(maxDepth = -1), E.Limits(maxDepth = E.Limits.MaxDepth + 1),
      E.Limits(maxNodes = 0), E.Limits(maxNodes = -1), E.Limits(maxNodes = E.Limits.MaxNodes + 1)) do
      assertEquals(E.evaluate(R.Read(a), _ => fail("Invalid limits"), limits), Left(E.Failure.LimitExceeded))
    assertEquals(E.evaluate(R.ShortCircuit(R.ShortCircuitOperator.And, R.BooleanLiteral(false), R.Read(a)),
      _ => fail("Skipped budgeted read"), E.Limits(maxNodes = 2)), Right(E.Value.BooleanValue(false)))
    def tree(leaves: Int): R.Expression =
      if leaves == 1 then R.Read(a)
      else R.Binary(R.BinaryOperator.Add, tree(leaves / 2), tree(leaves - leaves / 2))
    val atLimit = R.Group(tree(5000))
    var reads = 0
    val counted: E.Reader = _ => { reads += 1; Right(E.Value.IntValue(1)) }
    assertEquals(E.evaluate(atLimit, counted), Right(E.Value.IntValue(5000)))
    assertEquals(reads, 5000)
    reads = 0
    assertEquals(E.evaluate(R.Group(atLimit), counted), Left(E.Failure.LimitExceeded))
    assertEquals(reads, 4999)
  }

  test("Java source expressions retain their meaning through parsing checking and resolution") {
    val source = "class Drawing { static void calculate(int n, boolean flag) { " +
      "int product = n * n; int quotient = -9 / 2; int rest = -9 % 2; " +
      "boolean skip = flag && n / 0 > 0; boolean ready = !flag || n / 0 > 0; int wrapped = - -2147483648; " +
      "} public static void main(String[] args) {} }"
    val resolved = (for {
      parsed <- JavaTurtleSource.parse(source)
      structure <- JavaTurtleStructure.check(parsed)
      checked <- JavaTurtleSemantics.check(structure)
      result <- R.resolve(checked)
    } yield result).fold(problem => fail(problem.message), identity)
    val method = resolved.methods.find(_.name == "calculate").getOrElse(fail("Missing calculate"))
    val values: Map[R.VariableId, E.Value] = Map(method.parameters(0).id -> E.Value.IntValue(Int.MaxValue),
      method.parameters(1).id -> E.Value.BooleanValue(false))
    val expressions = method.body.statements.collect { case R.Declare(_, Some(value)) => value }
    assertEquals(expressions.map(expression => javaResult(expression, values)), Vector(
      Right(E.Value.IntValue(1)), Right(E.Value.IntValue(-4)), Right(E.Value.IntValue(-1)),
      Right(E.Value.BooleanValue(false)), Right(E.Value.BooleanValue(true)), Right(E.Value.IntValue(Int.MinValue))
    ))
    assertEquals(resolved.source, source)
  }
}
