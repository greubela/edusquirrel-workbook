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
import it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonExport as Y
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleSemantics, JavaTurtleSource, JavaTurtleStructure, JavaTurtleVmBindings as V, JavaTurtleVmExpressions as X, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.{BeSimulatorConfig, BeSimulatorState, BeVirtualMachineState}
import it.evadid.vm.simulation.java.{JavaInt32, JavaTurtleEvaluation as E, JavaTurtleInvocationTrace, JavaTurtleRuntime as T}
import it.evadid.vm.types.{BeChildRole, BeDataType, BeDataValueLiteral, BeScope, BeUseValueReference}
import it.evadid.workbook.elements.interactionElements.programming.JavaKochAssessment as K
import munit.FunSuite

class BeExpressionLanguageSupportTest extends FunSuite {

  private def javaVariable(index: Int, valueType: R.ValueType): R.Variable =
    R.Variable(R.VariableId(R.MethodId(0), index), s"v$index", valueType)

  private def javaResult(expression: R.Expression, values: Map[R.VariableId, E.Value] = Map.empty): Either[E.Failure, E.Value] =
    E.evaluate(expression, variable => values.get(variable.id).toRight(E.Failure.MissingValue(variable.id)))

  private def javaProgram(main: String, helpers: String = ""): R.ResolvedSource = {
    val source = s"class Drawing { $helpers public static void main(String[] args) { $main } }"
    (for {
      parsed <- JavaTurtleSource.parse(source)
      structure <- JavaTurtleStructure.check(parsed)
      checked <- JavaTurtleSemantics.check(structure)
      result <- R.resolve(checked)
    } yield result).fold(problem => fail(problem.message), identity)
  }

  private def forward(value: Int): T.Command = T.Command(R.TurtleCommand.Forward, value)
  private def right(value: Int): T.Command = T.Command(R.TurtleCommand.TurnRight, value)

  private def singleMethodExecution(status: T.Status, commands: Vector[T.Command], steps: Int): T.Execution = {
    val methods = if steps == 0 then Vector.empty else Vector(T.MethodCalls(R.MethodId(0), 1, 0))
    val forwards = commands.count(command => command.command == R.TurtleCommand.Forward && command.value != 0.0)
    val drawings = if forwards == 0 then Vector.empty else Vector(T.MethodDrawing(R.MethodId(0), forwards, 0))
    T.Execution(status, commands, steps, Some(T.CallEvidence(methods, if steps == 0 then 0 else 1)),
      Some(T.DrawingEvidence(drawings)))
  }

  private def restoredJavaExpression(expression: X.Expression): R.Expression = expression.node match {
    case X.Node.IntLiteral(value) => R.IntLiteral(value)
    case X.Node.DoubleLiteral(value) => R.DoubleLiteral(value)
    case X.Node.Widen(inner) => R.Widen(restoredJavaExpression(inner))
    case X.Node.BooleanLiteral(value) => R.BooleanLiteral(value)
    case X.Node.Read(variable, _) => R.Read(variable)
    case X.Node.Group(inner) => R.Group(restoredJavaExpression(inner))
    case X.Node.Unary(operator, operand) => R.Unary(operator, restoredJavaExpression(operand))
    case X.Node.Binary(operator, left, right) => R.Binary(operator, restoredJavaExpression(left), restoredJavaExpression(right))
    case X.Node.ShortCircuit(operator, left, right) => R.ShortCircuit(operator, restoredJavaExpression(left), restoredJavaExpression(right))
  }

  private def restoredJavaBlock(block: P.Block): R.Block = R.Block(block.statements.map { statement => statement.node match {
    case P.Node.Empty => R.Empty
    case P.Node.Return => R.Return
    case P.Node.Declare(variable, _, initial) => R.Declare(variable, initial.map(value => restoredJavaExpression(value.expression)))
    case P.Node.Assign(variable, _, operator, value) => R.Assign(variable, operator, restoredJavaExpression(value.expression))
    case P.Node.Call(target, arguments) =>
      val call = target match {
        case P.CallTarget.Helper(method) => R.CallTarget.Helper(method.id)
        case P.CallTarget.Turtle(command) => R.CallTarget.Turtle(command)
      }
      R.Call(call, arguments.map(value => restoredJavaExpression(value.expression)))
    case P.Node.If(condition, positive, negative) => R.If(restoredJavaExpression(condition.expression), restoredJavaBlock(positive), negative.map(restoredJavaBlock))
    case P.Node.While(condition, body) => R.While(restoredJavaExpression(condition.expression), restoredJavaBlock(body))
    case P.Node.For(init, condition, update, body) => R.For(restoredJavaBlock(init), condition.map(value => restoredJavaExpression(value.expression)), restoredJavaBlock(update), restoredJavaBlock(body))
  } })

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

  test("Java double expressions promote operands but preserve integer division before widening") {
    assertEquals(javaResult(R.Binary(R.BinaryOperator.Divide, R.DoubleLiteral(10.0), R.IntLiteral(3))), Right(E.Value.DoubleValue(10.0 / 3.0)))
    assertEquals(javaResult(R.Widen(R.Binary(R.BinaryOperator.Divide, R.IntLiteral(10), R.IntLiteral(3)))), Right(E.Value.DoubleValue(3.0)))
    assertEquals(javaResult(R.Binary(R.BinaryOperator.Add, R.DoubleLiteral(0.5), R.IntLiteral(3))), Right(E.Value.DoubleValue(3.5)))
    assertEquals(javaResult(R.Unary(R.UnaryOperator.Negate, R.DoubleLiteral(0.0))), Right(E.Value.DoubleValue(-0.0)))
    assertEquals(javaResult(R.Widen(R.BooleanLiteral(true))), Left(E.Failure.TypeMismatch))
    assertEquals(E.widen(E.Value.DoubleValue(3.0), R.ValueType.IntValue), Left(E.Failure.TypeMismatch))
  }

  test("Java floating comparisons use primitive IEEE equality and preserve remainder sign") {
    def result(operator: R.BinaryOperator, a: Double, b: Double) = javaResult(R.Binary(operator, R.DoubleLiteral(a), R.DoubleLiteral(b)))
    assertEquals(result(R.BinaryOperator.Equal, 0.0, -0.0), Right(E.Value.BooleanValue(true)))
    assertEquals(result(R.BinaryOperator.Equal, Double.NaN, Double.NaN), Right(E.Value.BooleanValue(false)))
    assertEquals(result(R.BinaryOperator.NotEqual, Double.NaN, Double.NaN), Right(E.Value.BooleanValue(true)))
    assertEquals(result(R.BinaryOperator.LessEqual, Double.NaN, 0.0), Right(E.Value.BooleanValue(false)))
    assertEquals(result(R.BinaryOperator.Divide, 1.0, -0.0), Right(E.Value.DoubleValue(Double.NegativeInfinity)))
    assertEquals(result(R.BinaryOperator.Remainder, -9.0, 2.0), Right(E.Value.DoubleValue(-1.0)))
    val zero = result(R.BinaryOperator.Remainder, -4.0, 2.0).toOption.get.asInstanceOf[E.Value.DoubleValue].value
    assertEquals(java.lang.Double.doubleToRawLongBits(zero), java.lang.Double.doubleToRawLongBits(-0.0))
    assert(result(R.BinaryOperator.Divide, 0.0, 0.0).toOption.get.asInstanceOf[E.Value.DoubleValue].value.isNaN)
    assert(result(R.BinaryOperator.Remainder, Double.PositiveInfinity, 2.0).toOption.get.asInstanceOf[E.Value.DoubleValue].value.isNaN)
  }

  test("manual Java VM expression binding rejects nonfinite literals but keeps numeric binding types") {
    val bindings = V.bind(javaProgram("double length = 10.5;"))
    for value <- List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity) do
      assertEquals(X.adapt(bindings, R.DoubleLiteral(value)).left.toOption.get.problem, JavaTurtleSource.Problem.UnsupportedNumber)
    val expression = X.adapt(bindings, R.Widen(R.IntLiteral(10))).toOption.get
    assertEquals(expression.expression.staticInformationExpression.staticType, BeDataType.Numeric)
    assertEquals(X.evaluate(expression, _ => fail("Literal does not read")), Right(E.Value.DoubleValue(10.0)))
  }

  test("typed Java double bindings and VM statements retain promotions and local parameters") {
    val raw = "\r\nclass Drawing { static void draw(double length, int parts) { length /= parts; Turtle.forward(length); } " +
      "public static void main(String[] args) { double length = 10; double truncated = 10 / 3; " +
      "draw(length, 3); Turtle.forward(truncated); Turtle.forward(length); } }\t "
    val program = P.compile(raw).fold(error => fail(error.message), identity)
    val execution = T.runVm(program)
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands.map(_.value), Vector(10.0 / 3.0, 3.0, 10.0))
    assertEquals(T.run(program.bindings.source), execution)
    val helper = program.root.methods.find(_.binding.originalName == "draw").get
    assertEquals(helper.binding.parameters.head.definition.get.variableType, BeDataType.Numeric)
    assertEquals(helper.binding.parameters(1).definition.get.variableType, BeDataType.Int)
    assertEquals(T.invokeVm(program, helper.binding.id, Vector(E.Value.IntValue(10), E.Value.IntValue(3))).commands.map(_.value), Vector(10.0 / 3.0))
    assertEquals(program.bindings.source.source, raw)
  }

  test("nonfinite double calculations remain usable until they reach a drawing command") {
    val usable = T.run(javaProgram("double infinity = 1.0 / 0.0; if (infinity > 0) { Turtle.forward(2); } double nan = 0.0 / 0.0; if (nan != nan) { Turtle.forward(3); }"))
    assertEquals(usable.status, T.Status.Completed)
    assertEquals(usable.commands, Vector(forward(2), forward(3)))
    val stopped = T.run(javaProgram("Turtle.forward(1); Turtle.forward(1.0 / 0.0); Turtle.forward(99);"))
    assertEquals(stopped.status, T.Status.Failed(T.Failure.NonFiniteCommand))
    assertEquals(stopped.commands, Vector(forward(1)))
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

  test("Java for and while methods produce the parameterized square trace") {
    for body <- Seq(
      "for (int i = 0; i < 4; i += 1) { Turtle.forward(n); Turtle.turnRight(90); }",
      "int i = 0; while (i < 4) { Turtle.forward(n); Turtle.turnRight(90); i += 1; }"
    ); size <- Seq(10, 40, 100) do {
      val source = javaProgram(s"square($size);", s"static void square(int n) { $body }")
      val expected = Vector.fill(4)(Vector(forward(size), right(90))).flatten
      val execution = T.run(source)
      assertEquals(execution.status, T.Status.Completed)
      assertEquals(execution.commands, expected)
      val helper = source.methods.find(_.name == "square").getOrElse(fail("Missing square"))
      val invoked = T.invoke(source, helper.id, Vector(E.Value.IntValue(size)))
      assertEquals(invoked.status, T.Status.Completed)
      assertEquals(invoked.commands, expected)
    }
  }

  test("Java helper arguments use the caller frame and mutable parameters stay local") {
    val source = javaProgram("int n = 40; outer(n); Turtle.forward(n); draw(10); draw(10);", """
      static void inner(int n, int other) { n += 1; Turtle.forward(n); Turtle.forward(other); }
      static void outer(int n) { inner(n + 1, n + 2); Turtle.forward(n); }
      static void draw(int n) { n += 1; Turtle.forward(n); }
    """)
    val execution = T.run(source)
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(42, 42, 40, 40, 11, 11).map(forward))
    val boolean = T.run(javaProgram("boolean flag = true; choose(flag); if (flag) { Turtle.forward(3); }", """
      static void choose(boolean flag) { flag = !flag; if (flag) { Turtle.forward(1); } else { Turtle.forward(2); } }
    """))
    assertEquals(boolean.status, T.Status.Completed)
    assertEquals(boolean.commands, Vector(forward(2), forward(3)))
    assertEquals(T.run(source), execution)
  }

  test("Java loop declarations reset on each entry and sibling scopes stay separate") {
    val execution = T.run(javaProgram("""
      for (int i = 0; i < 3; i += 1) { int value = i; Turtle.forward(value); }
      if (true) { int value = 7; Turtle.forward(value); }
      if (true) { int value = 8; Turtle.forward(value); }
      for (int i = 0; i < 1; i += 1) { int value; value = 9; Turtle.forward(value); }
    """))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(0, 1, 2, 7, 8, 9).map(forward))
  }

  test("Java loops skip zero iterations and execute updates after the body in order") {
    val execution = T.run(javaProgram("""
      int i = 0;
      while (i < 0) { Turtle.forward(99); }
      for (int k = 0; k < 0; k += 1) { Turtle.forward(99); }
      for (i = 0; i < 3; i += 1, Turtle.turnRight(i)) { Turtle.forward(i); }
      Turtle.forward(i);
    """))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(forward(0), right(1), forward(1), right(2), forward(2), right(3), forward(3)))
    val overflow = T.run(javaProgram("int i = 2147483647; while (i > 0) { Turtle.forward(i); i += 1; } Turtle.forward(i);"))
    assertEquals(overflow.status, T.Status.Completed)
    assertEquals(overflow.commands, Vector(forward(Int.MaxValue), forward(Int.MinValue)))
  }

  test("Java return ends only its method and skips the pending loop update") {
    val execution = T.run(javaProgram("first(); Turtle.forward(11); second(1); Turtle.forward(14);", """
      static void first() {
        for (int i = 0; i < 4; Turtle.forward(999)) { Turtle.forward(10); return; }
        Turtle.forward(999);
      }
      static void second(int n) {
        while (n > 0) { if (n > 0) { Turtle.forward(12); return; } n -= 1; }
        Turtle.forward(999);
      }
    """))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(10, 11, 12, 14).map(forward))
    val early = T.run(javaProgram("if (true) { Turtle.forward(1); return; } Turtle.forward(999);"))
    assertEquals(early.status, T.Status.Completed)
    assertEquals(early.commands, Vector(forward(1)))
  }

  test("Java compound assignments use int32 arithmetic") {
    val execution = T.run(javaProgram("""
      int n = 2147483647; n += 1; Turtle.forward(n);
      n -= 1; Turtle.forward(n); n *= 2147483647; Turtle.forward(n);
      n = -2147483648; n /= -1; Turtle.forward(n); n %= -1; Turtle.forward(n);
      n = -9; n /= 2; Turtle.forward(n); n = -9; n %= 2; Turtle.forward(n);
    """))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(Int.MinValue, Int.MaxValue, 1, Int.MinValue, 0, -4, -1).map(forward))
  }

  test("Java runtime failures preserve only the executed prefix and stop caller continuation") {
    val helpers = "static void pair(int a, int b) { Turtle.forward(999); }"
    for body <- Seq("int n = 1; n /= 0;", "int n = 1; n %= 0;", "pair(1 / 0, 2 / 0);") do {
      val execution = T.run(javaProgram(s"Turtle.forward(1); $body Turtle.forward(999);", helpers))
      assertEquals(execution.status, T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero)))
      assertEquals(execution.commands, Vector(forward(1)))
    }
    val skipped = T.run(javaProgram("""
      boolean flag = false && 1 / 0 > 0;
      if (!flag || 1 / 0 > 0) { Turtle.forward(2); }
    """))
    assertEquals(skipped.status, T.Status.Completed)
    assertEquals(skipped.commands, Vector(forward(2)))
  }

  test("Java runtime rejects invalid invocations and limits before effects") {
    val source = javaProgram("draw(1);", "static void draw(int n) { Turtle.forward(n); }")
    val method = source.methods.find(_.name == "draw").getOrElse(fail("Missing draw"))
    for (id, arguments) <- Seq(method.id -> Vector.empty[E.Value],
      method.id -> Vector(E.Value.BooleanValue(true)), method.id -> Vector(E.Value.IntValue(1), E.Value.IntValue(2)),
      R.MethodId(999) -> Vector.empty[E.Value], source.entryPoint -> Vector.empty[E.Value]) do {
      val execution = T.invoke(source, id, arguments)
      assertEquals(execution, singleMethodExecution(T.Status.Failed(T.Failure.InvalidInvocation), Vector.empty, 0))
    }
    for limits <- Seq(T.Limits(maxSteps = 0), T.Limits(maxSteps = -1), T.Limits(maxSteps = T.Limits.MaxSteps + 1),
      T.Limits(maxCommands = -1), T.Limits(maxCommands = T.Limits.MaxCommands + 1),
      T.Limits(maxCallDepth = 0), T.Limits(maxCallDepth = T.Limits.MaxCallDepth + 1),
      T.Limits(maxBlockDepth = 0), T.Limits(maxBlockDepth = T.Limits.MaxBlockDepth + 1)) do
      assertEquals(T.run(source, limits, () => fail("Invalid limits must not poll")),
        singleMethodExecution(T.Status.Failed(T.Failure.InvalidLimits), Vector.empty, 0))
    assertEquals(T.run(javaProgram(""), T.Limits(maxCommands = 0)).status, T.Status.Completed)
    val noCommands = T.run(source, T.Limits(maxCommands = 0))
    assertEquals(noCommands.status, T.Status.LimitExceeded)
    assertEquals(noCommands.commands, Vector.empty)
  }

  test("Java runtime shares exact work limits across statements and expressions") {
    val empty = T.run(javaProgram(""), T.Limits(maxSteps = 2))
    assertEquals(empty, singleMethodExecution(T.Status.Completed, Vector.empty, 2))
    val one = javaProgram("Turtle.forward(1);")
    assertEquals(T.run(one, T.Limits(maxSteps = 5)), singleMethodExecution(T.Status.Completed, Vector(forward(1)), 5))
    assertEquals(T.run(one, T.Limits(maxSteps = 4)), singleMethodExecution(T.Status.LimitExceeded, Vector.empty, 4))
    val expressions = javaProgram("int a = 1 + 2; int b = 3 + 4; Turtle.forward(a + b);")
    assertEquals(T.run(expressions, T.Limits(maxSteps = 15)), singleMethodExecution(T.Status.Completed, Vector(forward(10)), 15))
    assertEquals(T.run(expressions, T.Limits(maxSteps = 14)), singleMethodExecution(T.Status.LimitExceeded, Vector.empty, 14))
    val square = javaProgram("for (int i = 0; i < 4; i += 1) { Turtle.forward(10); Turtle.turnRight(90); }")
    val full = T.run(square, T.Limits(maxCommands = 8))
    val limited = T.run(square, T.Limits(maxCommands = 7))
    assertEquals(full.status, T.Status.Completed)
    assertEquals(full.commands.size, 8)
    assertEquals(limited.status, T.Status.LimitExceeded)
    assertEquals(limited.commands, full.commands.take(7))
  }

  test("Java empty endless loops stop on limits or cancellation and new runs are fresh") {
    for body <- Seq("for (;;) {}", "while (true) {}", "for (;;) { ; }") do {
      val source = javaProgram(body)
      assertEquals(T.run(source, T.Limits(maxSteps = 20)), singleMethodExecution(T.Status.LimitExceeded, Vector.empty, 20))
      var polls = 0
      val cancelled = T.run(source, T.Limits(maxSteps = 30), () => { polls += 1; polls >= 10 })
      assertEquals(cancelled, singleMethodExecution(T.Status.Cancelled, Vector.empty, 9))
      assertEquals(polls, 10)
      assertEquals(T.run(source, T.Limits(maxSteps = 20)), singleMethodExecution(T.Status.LimitExceeded, Vector.empty, 20))
    }
    val one = javaProgram("Turtle.forward(1);")
    assertEquals(T.run(one, isCancelled = () => true), singleMethodExecution(T.Status.Cancelled, Vector.empty, 0))
    var polls = 0
    assertEquals(T.run(one, isCancelled = () => { polls += 1; polls >= 5 }), singleMethodExecution(T.Status.Cancelled, Vector.empty, 4))
    assertEquals(T.run(one), singleMethodExecution(T.Status.Completed, Vector(forward(1)), 5))
    val two = javaProgram("Turtle.forward(1); Turtle.forward(2);")
    polls = 0
    assertEquals(T.run(two, isCancelled = () => { polls += 1; polls >= 8 }),
      singleMethodExecution(T.Status.Cancelled, Vector(forward(1)), 7))
    assertEquals(T.run(two), singleMethodExecution(T.Status.Completed, Vector(forward(1), forward(2)), 8))
    val emitting = T.run(javaProgram("while (true) { Turtle.forward(1); }"), T.Limits(maxCommands = 3))
    assertEquals(emitting.status, T.Status.LimitExceeded)
    assertEquals(emitting.commands, Vector.fill(3)(forward(1)))
  }

  test("Java call and block depth limits stop explicit stacks without affecting later runs") {
    val source = javaProgram("draw();", "static void draw() { Turtle.forward(1); }")
    val tooDeep = T.run(source, T.Limits(maxCallDepth = 1))
    assertEquals(tooDeep.status, T.Status.LimitExceeded)
    assertEquals(tooDeep.commands, Vector.empty)
    val shallow = T.run(source, T.Limits(maxCallDepth = 2))
    assertEquals(shallow.status, T.Status.Completed)
    assertEquals(shallow.commands, Vector(forward(1)))
    def chain(count: Int): R.ResolvedSource = javaProgram("m0();", (0 until count).map { index =>
      val body = if index == count - 1 then "Turtle.forward(1);" else s"m${index + 1}();"
      s"static void m$index() { $body }"
    }.mkString(" "))
    val deep = T.run(chain(80))
    assertEquals(deep.status, T.Status.LimitExceeded)
    assertEquals(deep.commands, Vector.empty)
    val long = T.run(chain(40))
    assertEquals(long.status, T.Status.Completed)
    assertEquals(long.commands, Vector(forward(1)))
    val nested = javaProgram("if (true) { " * 8 + "Turtle.forward(1);" + " }" * 8)
    val restricted = T.run(nested, T.Limits(maxBlockDepth = 8))
    assertEquals(restricted.status, T.Status.LimitExceeded)
    assertEquals(restricted.commands, Vector.empty)
    val allowed = T.run(nested, T.Limits(maxBlockDepth = 9))
    assertEquals(allowed.status, T.Status.Completed)
    assertEquals(allowed.commands, Vector(forward(1)))
  }

  test("Java VM bindings retain source method parameter and declaration identities") {
    val source = javaProgram("draw(40, true);", """
      static void draw(int distance, boolean enabled) {
        int size = distance;
        if (enabled) { int sizeInBranch = size; } else { int sizeInBranch = size; }
        while (enabled) { int repeated = size; enabled = false; }
        for (int i = 0; i < 1; i += 1) { int value = size; }
        for (int i = 0; i < 1; i += 1) { int value = size; }
        int uninitialized;
      }
    """)
    val bindings = V.bind(source)
    assert(bindings.source eq source)
    assertEquals(bindings.source.source, source.source)
    assertEquals(bindings.methods.map(_.id), source.methods.map(_.id))
    assertEquals(bindings.methods.map(_.originalName), source.methods.map(_.name))
    assertEquals(bindings.entryPoint.id, source.entryPoint)
    source.methods.zip(bindings.methods).foreach { (method, binding) =>
      assertEquals(binding.parameters.map(_.variable), method.parameters)
      assertEquals(bindings.method(method.id), Some(binding))
      binding.parameters.foreach { parameter =>
        assert(bindings.variable(parameter.variable.id).get eq parameter)
      }
    }
    val locals = bindings.variables.filter(_.variable.id.method == R.MethodId(0))
    assertEquals(locals.map(_.variable.name), Vector("distance", "enabled", "size", "sizeInBranch", "sizeInBranch",
      "repeated", "i", "value", "i", "value", "uninitialized"))
    assertEquals(locals.map(_.variable.id.index), (0 until locals.size).toVector)
    assertEquals(locals.flatMap(_.definition).distinct.size, locals.size)
    assert(locals.flatMap(_.definition).forall(_.initValue.isEmpty))
    assertEquals(locals.head.definition.get.variableType, BeDataType.Int)
    assertEquals(locals(1).definition.get.variableType, BeDataType.Boolean)
    assertEquals(T.run(source).status, T.Status.Completed)
  }

  test("Java VM names do not normalize or collide with learner identifiers") {
    val source = javaProgram("""
      int $x = 1; int def = 2; int lambda = 3; int java_variable_0_0 = 4;
      drawSquare($x); draw_square(def); java_method_0(lambda);
    """, """
      static void drawSquare(int sideLength) { Turtle.forward(sideLength); }
      static void draw_square(int side_length) { Turtle.forward(side_length); }
      static void java_method_0(int value) { Turtle.forward(value); }
    """)
    assertEquals(BeEntityName.fromCodeString("drawSquare").universalInterpretation(),
      BeEntityName.fromCodeString("draw_square").universalInterpretation())
    val bindings = V.bind(source)
    val names = bindings.methods.map(_.name) ++ bindings.variables.flatMap(_.definition.map(_.name))
    assertEquals(names.map(_.universalInterpretation()).distinct.size, names.size)
    names.foreach { name =>
      val expected = name.universalInterpretation()
      assert(expected.matches("java_(method_[0-9]+|variable_[0-9]+_[0-9]+)"), clue = expected)
      for language <- List(English, German); style <- List(NamingStyle.CamelCase, NamingStyle.SnakeCase, NamingStyle.AllcapsSchool) do
        assertEquals(name.getNameIn(language, style), expected)
    }
    bindings.variables.flatMap(_.definition).foreach { variable =>
      val read = BeUseValue(BeUseValueReference(variable), None)
      for language <- List(Python, Java) do
        assertEquals(read.structureInfo.toStringInLanguage(language, English), variable.name.universalInterpretation())
    }
    assertEquals(T.run(source).commands, Vector(forward(1), forward(2), forward(3)))
  }

  test("Java VM references use interned definitions and reject mismatched symbols") {
    val source = javaProgram("int value = 1; boolean flag = true;", "static void draw(int value) {}")
    val bindings = V.bind(source)
    val values = bindings.variables.filter(_.definition.nonEmpty)
    values.foreach { binding =>
      val variable = binding.variable
      val first = bindings.reference(variable).toOption.get
      val second = bindings.reference(variable).toOption.get
      val definition = bindings.definition(variable).toOption.get
      assert(binding.definition.get eq definition)
      first.value match {
        case BeUseValueReference(value) => assert(value eq definition)
        case _ => fail("Expected a variable reference")
      }
      second.value match {
        case BeUseValueReference(value) => assert(value eq definition)
        case _ => fail("Expected a variable reference")
      }
      assertEquals(first.staticInformationExpression.staticType, definition.variableType)
      for changed <- Seq(variable.copy(name = "other"), variable.copy(id = R.VariableId(variable.id.method, 999)),
        variable.copy(valueType = if variable.valueType == R.ValueType.IntValue then R.ValueType.BooleanValue else R.ValueType.IntValue)) do
        assertEquals(bindings.reference(changed).swap.toOption.get.problem, JavaTurtleSource.Problem.UnknownVariable)
    }
    assertEquals(bindings.variable(R.VariableId(R.MethodId(999), 0)), None)
    assertEquals(bindings.method(R.MethodId(999)), None)
    val repeated = V.bind(source)
    assertEquals(repeated.methods, bindings.methods)
    assertEquals(repeated.variables, bindings.variables)
    values.foreach { binding =>
      assert(!(binding.definition.get eq repeated.definition(binding.variable).toOption.get))
    }
  }

  test("Java VM main arguments remain metadata without introducing array values") {
    val source = javaProgram("int size = 40; Turtle.forward(size);")
    val bindings = V.bind(source)
    val argument = bindings.entryPoint.parameters.head
    assertEquals(argument.variable.valueType, R.ValueType.MainArguments)
    assertEquals(argument.definition, None)
    assertEquals(bindings.variable(argument.variable.id), Some(argument))
    assertEquals(bindings.definition(argument.variable).swap.toOption.get.problem, JavaTurtleSource.Problem.UnsupportedType)
    assertEquals(bindings.reference(argument.variable).swap.toOption.get.problem, JavaTurtleSource.Problem.UnsupportedType)
    val size = bindings.variables.find(_.variable.name == "size").getOrElse(fail("Missing size"))
    assertEquals(size.variable.id.index, 1)
    assertEquals(size.definition.get.name.universalInterpretation(), "java_variable_0_1")
    assertEquals(T.run(source).commands, Vector(forward(40)))
  }

  test("Java VM bindings keep a main declared before its helper as the entry point") {
    val source = "class Drawing { public static void main(String[] args) { draw(40); } " +
      "static void draw(int size) { Turtle.forward(size); } }"
    val resolved = (for {
      parsed <- JavaTurtleSource.parse(source)
      structure <- JavaTurtleStructure.check(parsed)
      typed <- JavaTurtleSemantics.check(structure)
      result <- R.resolve(typed)
    } yield result).fold(problem => fail(problem.message), identity)
    val bindings = V.bind(resolved)
    assertEquals(bindings.entryPoint.id, R.MethodId(0))
    assertEquals(bindings.methods.map(_.originalName), Vector("main", "draw"))
    assertEquals(bindings.methods(1).parameters.head.variable.id, R.VariableId(R.MethodId(1), 0))
    assertEquals(bindings.methods(1).name.universalInterpretation(), "java_method_1")
    assertEquals(T.run(resolved).commands, Vector(forward(40)))
  }

  test("Java VM binding collection handles the full variable budget and deep branches") {
    val helpers = (0 until 8).map { method =>
      val count = if method == 7 then 127 else 128
      s"static void m$method() {" + (0 until count).map(index => s"int x$index;").mkString + "}"
    }.mkString
    val bindings = V.bind(javaProgram("", helpers))
    assertEquals(bindings.variables.size, 1024)
    assertEquals(bindings.variables.flatMap(_.definition).size, 1023)
    assertEquals(bindings.variables.map(_.variable.id).distinct.size, 1024)
    assertEquals(bindings.variables.flatMap(_.definition).distinct.size, 1023)
    bindings.variables.foreach { binding =>
      assertEquals(bindings.variable(binding.variable.id), Some(binding))
    }
    val deep = V.bind(javaProgram("if (true) { int size = 40; } else " * 27 + "if (true) { int size = 40; }"))
    assertEquals(deep.variables.map(_.variable.name), Vector("args") ++ Vector.fill(28)("size"))
    assertEquals(deep.variables.map(_.variable.id.index), (0 to 28).toVector)
    assertEquals(deep.variables.flatMap(_.definition).distinct.size, 28)
    assertEquals(deep.variables.last.definition.get.name.universalInterpretation(), "java_variable_0_28")
    val wide = V.bind(javaProgram(";" * 4089))
    assertEquals(wide.variables.size, 1)
    val methods = V.bind(javaProgram("", (0 until 127).map(index => s"static void m$index() {}").mkString))
    assertEquals(methods.methods.size, 128)
    assertEquals(methods.methods.map(_.name.universalInterpretation()).distinct.size, 128)
  }

  test("Java VM expressions retain typed literals grouping and bound read children") {
    val source = javaProgram("int size = (1 + 2) * 3;")
    val bindings = V.bind(source)
    val R.Declare(size, Some(initializer)) = source.methods.head.body.statements.head: @unchecked
    val compiled = X.adapt(bindings, initializer).toOption.get
    assert(compiled.bindings eq bindings)
    assertEquals(compiled.expression.staticInformationExpression.staticType, BeDataType.Int)
    assertEquals(compiled.expression.staticInformationExpression.staticValue, None)
    compiled.expression.node match {
      case X.Node.Binary(R.BinaryOperator.Multiply, group, three) =>
        assertEquals(three.node, X.Node.IntLiteral(3))
        group.node match {
          case X.Node.Group(sum) => sum.node match {
            case X.Node.Binary(R.BinaryOperator.Add, one, two) =>
              assertEquals(one.node, X.Node.IntLiteral(1))
              assertEquals(two.node, X.Node.IntLiteral(2))
            case _ => fail("Missing grouped addition")
          }
          case _ => fail("Missing parentheses")
        }
        val children = compiled.expression.structureInfo.getChildrenAsReference(BeScope.GlobalScope())
        assertEquals(children.map(_.childInfo.myRoleInParent), Seq(BeChildRole.FunctionParameter(0), BeChildRole.FunctionParameter(1)))
        assert(children.head.expr eq group)
        assert(children(1).expr eq three)
      case _ => fail("Missing multiplication")
    }
    assertEquals(X.evaluate(compiled, _ => fail("Unexpected variable read")), Right(E.Value.IntValue(9)))
    val read = X.adapt(bindings, R.Read(size)).toOption.get
    read.expression.node match {
      case X.Node.Read(variable, reference) =>
        assertEquals(variable, size)
        val children = read.expression.structureInfo.getChildrenAsReference(BeScope.GlobalScope())
        assert(children.head.expr eq reference)
        reference.value match {
          case BeUseValueReference(definition) =>
            assert(definition eq bindings.definition(size).toOption.get)
            assertEquals(X.evaluate(read, received => {
              assert(received eq definition)
              Right(E.Value.IntValue(40))
            }), Right(E.Value.IntValue(40)))
          case _ => fail("Missing bound reference")
        }
      case _ => fail("Missing variable read")
    }
    val flag = X.adapt(bindings, R.BooleanLiteral(true)).toOption.get
    assertEquals(flag.expression.staticInformationExpression.staticType, BeDataType.Boolean)
    assertEquals(X.evaluate(flag, _ => fail("Unexpected variable read")), Right(E.Value.BooleanValue(true)))
    assertEquals(BeDataValueLiteral("1").currentType, BeDataType.Numeric)
  }

  test("Java VM arithmetic matches an independent integer model") {
    val bindings = V.bind(javaProgram(""))
    val modulus = BigInt(1) << 32
    def wrapped(value: BigInt): Int = {
      val unsigned = ((value % modulus) + modulus) % modulus
      (if unsigned > Int.MaxValue then unsigned - modulus else unsigned).toInt
    }
    var state = BigInt("13579bdf", 16)
    def sample(): Int = {
      state = (state * 1664525 + 1013904223) % modulus
      wrapped(state)
    }
    val edges = Vector(Int.MinValue, Int.MinValue + 1, -1000000000, -65536, -9, -2, -1,
      0, 1, 2, 9, 65536, 1000000000, Int.MaxValue)
    val pairs = (for left <- edges; right <- edges yield left -> right) ++ Vector.fill(512)(sample() -> sample())
    for (left, right) <- pairs; operator <- Seq(R.BinaryOperator.Add, R.BinaryOperator.Subtract,
      R.BinaryOperator.Multiply, R.BinaryOperator.Divide, R.BinaryOperator.Remainder) do {
      val compiled = X.adapt(bindings, R.Binary(operator, R.IntLiteral(left), R.IntLiteral(right))).toOption.get
      val expected = operator match {
        case R.BinaryOperator.Add => Right(E.Value.IntValue(wrapped(BigInt(left) + BigInt(right))))
        case R.BinaryOperator.Subtract => Right(E.Value.IntValue(wrapped(BigInt(left) - BigInt(right))))
        case R.BinaryOperator.Multiply => Right(E.Value.IntValue(wrapped(BigInt(left) * BigInt(right))))
        case R.BinaryOperator.Divide | R.BinaryOperator.Remainder if right == 0 => Left(E.Failure.DivisionByZero)
        case R.BinaryOperator.Divide => Right(E.Value.IntValue(wrapped(BigInt(left) / BigInt(right))))
        case R.BinaryOperator.Remainder => Right(E.Value.IntValue(wrapped(BigInt(left) % BigInt(right))))
        case _ => fail("Unexpected arithmetic operator")
      }
      assertEquals(X.evaluate(compiled, _ => fail("Unexpected variable read")), expected, clue = (operator, left, right))
    }
    assertEquals(pairs.size, 708)
    for (operator, input, expected) <- Seq((R.UnaryOperator.Plus, 9, 9),
      (R.UnaryOperator.Negate, Int.MinValue, Int.MinValue), (R.UnaryOperator.Negate, 9, -9)) do {
      val compiled = X.adapt(bindings, R.Unary(operator, R.IntLiteral(input))).toOption.get
      assertEquals(X.evaluate(compiled, _ => fail("Unexpected variable read")), Right(E.Value.IntValue(expected)))
    }
  }

  test("Java VM comparisons preserve strict int and boolean semantics") {
    val bindings = V.bind(javaProgram(""))
    for (operator, expected) <- Seq(R.BinaryOperator.Less -> true, R.BinaryOperator.LessEqual -> true,
      R.BinaryOperator.Greater -> false, R.BinaryOperator.GreaterEqual -> false,
      R.BinaryOperator.Equal -> false, R.BinaryOperator.NotEqual -> true) do {
      val compiled = X.adapt(bindings, R.Binary(operator, R.IntLiteral(Int.MinValue), R.IntLiteral(Int.MaxValue))).toOption.get
      assertEquals(compiled.expression.staticInformationExpression.staticType, BeDataType.Boolean)
      assertEquals(X.evaluate(compiled, _ => fail("Unexpected variable read")), Right(E.Value.BooleanValue(expected)))
    }
    for a <- Seq(false, true); b <- Seq(false, true); operator <- Seq(R.BinaryOperator.Equal, R.BinaryOperator.NotEqual) do {
      val compiled = X.adapt(bindings, R.Binary(operator, R.BooleanLiteral(a), R.BooleanLiteral(b))).toOption.get
      val expected = if operator == R.BinaryOperator.Equal then a == b else a != b
      assertEquals(X.evaluate(compiled, _ => fail("Unexpected variable read")), Right(E.Value.BooleanValue(expected)))
    }
    val not = X.adapt(bindings, R.Unary(R.UnaryOperator.Not, R.BooleanLiteral(true))).toOption.get
    assertEquals(X.evaluate(not, _ => fail("Unexpected variable read")), Right(E.Value.BooleanValue(false)))
  }

  test("Java VM short circuit and arithmetic failures preserve variable access order") {
    val bindings = V.bind(javaProgram("", "static void draw(boolean flag, int a, int b, int c) {}"))
    val variables = bindings.source.methods.head.parameters
    val byDefinition = variables.map(variable => bindings.definition(variable).toOption.get -> variable).toMap
    val trace = scala.collection.mutable.ArrayBuffer.empty[String]
    def read(values: Vector[E.Value]): X.Reader = definition => {
      val variable = byDefinition(definition)
      assert(definition eq bindings.definition(variable).toOption.get)
      trace += variable.name
      Right(values(variable.id.index))
    }
    val flag = R.Read(variables(0))
    val division = R.Binary(R.BinaryOperator.Divide, R.Read(variables(1)), R.Read(variables(2)))
    val positive = R.Binary(R.BinaryOperator.Greater, division, R.IntLiteral(0))
    for operator <- Seq(R.ShortCircuitOperator.And, R.ShortCircuitOperator.Or); left <- Seq(false, true) do {
      trace.clear()
      val compiled = X.adapt(bindings, R.ShortCircuit(operator, flag, R.Group(positive))).toOption.get
      val skipped = if operator == R.ShortCircuitOperator.And then !left else left
      val expected = if skipped then Right(E.Value.BooleanValue(left)) else Left(E.Failure.DivisionByZero)
      assertEquals(X.evaluate(compiled, read(Vector(E.Value.BooleanValue(left), E.Value.IntValue(1),
        E.Value.IntValue(0), E.Value.IntValue(10)))), expected)
      assertEquals(trace.toVector, if skipped then Vector("flag") else Vector("flag", "a", "b"))
    }
    trace.clear()
    val addition = X.adapt(bindings, R.Binary(R.BinaryOperator.Add, division, R.Read(variables(3)))).toOption.get
    assertEquals(X.evaluate(addition, read(Vector(E.Value.BooleanValue(true), E.Value.IntValue(1),
      E.Value.IntValue(0), E.Value.IntValue(10)))), Left(E.Failure.DivisionByZero))
    assertEquals(trace.toVector, Vector("a", "b"))
    trace.clear()
    assertEquals(X.evaluate(addition, read(Vector(E.Value.BooleanValue(true), E.Value.IntValue(9),
      E.Value.IntValue(2), E.Value.IntValue(10)))), Right(E.Value.IntValue(14)))
    assertEquals(trace.toVector, Vector("a", "b", "c"))
  }

  test("Java VM adaptation checks invalid operands and bindings even in skipped branches") {
    val bindings = V.bind(javaProgram("int value = 1;"))
    val variable = bindings.variables.find(_.variable.name == "value").get.variable
    for bad <- Seq(R.Binary(R.BinaryOperator.Add, R.IntLiteral(1), R.BooleanLiteral(true)),
      R.Binary(R.BinaryOperator.Equal, R.BooleanLiteral(false), R.IntLiteral(0)),
      R.Binary(R.BinaryOperator.Less, R.BooleanLiteral(false), R.BooleanLiteral(true)),
      R.Unary(R.UnaryOperator.Not, R.IntLiteral(1)), R.Unary(R.UnaryOperator.Plus, R.BooleanLiteral(true)),
      R.ShortCircuit(R.ShortCircuitOperator.And, R.BooleanLiteral(false), R.IntLiteral(1))) do
      assertEquals(X.adapt(bindings, bad).swap.toOption.get.problem, JavaTurtleSource.Problem.TypeMismatch)
    for changed <- Seq(variable.copy(name = "other"), variable.copy(valueType = R.ValueType.BooleanValue),
      variable.copy(id = R.VariableId(R.MethodId(999), variable.id.index))) do {
      assertEquals(X.adapt(bindings, R.Read(changed)).swap.toOption.get.problem, JavaTurtleSource.Problem.UnknownVariable)
      val skipped = R.ShortCircuit(R.ShortCircuitOperator.Or, R.BooleanLiteral(true), R.Read(changed))
      assertEquals(X.adapt(bindings, skipped).swap.toOption.get.problem, JavaTurtleSource.Problem.UnknownVariable)
    }
    val args = bindings.entryPoint.parameters.head.variable
    assertEquals(X.adapt(bindings, R.Read(args)).swap.toOption.get.problem, JavaTurtleSource.Problem.UnsupportedType)
  }

  test("Java VM adaptation enforces exact expression size and depth limits before recursion") {
    val bindings = V.bind(javaProgram(""))
    def groups(count: Int): R.Expression = (0 until count).foldLeft[R.Expression](R.IntLiteral(1))((inner, _) => R.Group(inner))
    assert(X.adapt(bindings, groups(63)).isRight)
    assertEquals(X.adapt(bindings, groups(64)).swap.toOption.get.problem, JavaTurtleSource.Problem.InputLimit)
    assertEquals(X.adapt(bindings, groups(5000)).swap.toOption.get.problem, JavaTurtleSource.Problem.InputLimit)
    def balanced(levels: Int): R.Expression =
      if levels == 0 then R.IntLiteral(1) else {
        val child = balanced(levels - 1)
        R.Binary(R.BinaryOperator.Add, child, child)
      }
    val exact = R.Group(balanced(11))
    val allowed = X.adapt(bindings, exact).toOption.get
    assertEquals(X.evaluate(allowed, _ => fail("Unexpected variable read"), E.Limits(maxNodes = 4096)), Right(E.Value.IntValue(2048)))
    assertEquals(X.adapt(bindings, R.Group(exact)).swap.toOption.get.problem, JavaTurtleSource.Problem.InputLimit)
    val skipped = R.ShortCircuit(R.ShortCircuitOperator.Or, R.BooleanLiteral(true),
      R.Binary(R.BinaryOperator.Greater, groups(5000), R.IntLiteral(0)))
    assertEquals(X.adapt(bindings, skipped).swap.toOption.get.problem, JavaTurtleSource.Problem.InputLimit)
  }

  test("Java VM evaluation checks reader types budgets and fresh execution after failure") {
    val bindings = V.bind(javaProgram("int value = 1;"))
    val variable = bindings.variables.find(_.variable.name == "value").get.variable
    val expression = X.adapt(bindings, R.Binary(R.BinaryOperator.Add, R.Read(variable), R.Read(variable))).toOption.get
    var reads = 0
    val reader: X.Reader = definition => {
      assert(definition eq bindings.definition(variable).toOption.get)
      reads += 1
      Right(E.Value.IntValue(reads))
    }
    assertEquals(X.evaluate(expression, reader, E.Limits(maxNodes = 2)), Left(E.Failure.LimitExceeded))
    assertEquals(reads, 1)
    reads = 0
    assertEquals(X.evaluate(expression, reader, E.Limits(maxNodes = 3)), Right(E.Value.IntValue(3)))
    assertEquals(reads, 2)
    reads = 0
    for limits <- Seq(E.Limits(maxDepth = 1), E.Limits(maxNodes = 0), E.Limits(maxDepth = 257)) do
      assertEquals(X.evaluate(expression, reader, limits), Left(E.Failure.LimitExceeded))
    assertEquals(reads, 0)
    assertEquals(X.evaluate(expression, _ => Right(E.Value.BooleanValue(true))), Left(E.Failure.TypeMismatch))
    assertEquals(X.evaluate(expression, _ => Left(E.Failure.MissingValue(variable.id))), Left(E.Failure.MissingValue(variable.id)))
    assertEquals(X.evaluate(expression, _ => Left(E.Failure.Cancelled)), Left(E.Failure.Cancelled))
    assertEquals(X.evaluate(expression, _ => Right(E.Value.IntValue(40))), Right(E.Value.IntValue(80)))
  }

  test("Java VM nodes reject generic execution and edits without changing existing printers") {
    val compiled = X.adapt(V.bind(javaProgram("")), R.IntLiteral(1)).toOption.get
    val expression = compiled.expression
    assert(expression.structureInfo.withReplacedChildren(Map.empty) eq expression)
    intercept[UnsupportedOperationException] {
      expression.structureInfo.withReplacedChildren(Map(BeChildRole.FunctionParameter(0) -> literalTwo))
    }
    val state = BeSimulatorState(false, expression, false, Nil, Nil, BeVirtualMachineState.emptyMachineState)
    intercept[UnsupportedOperationException] { expression.expressionExecutor(BeSimulatorConfig(), state) }
    for language <- List(Python, Java, Cpp, JavaScript) do
      intercept[UnsupportedOperationException] { expression.structureInfo.toStringInLanguage(language, English) }
    assertEquals(literalOne.structureInfo.toStringInLanguage(Python, English), "1")
  }

  test("Java VM programs retain complete methods entry point and main argument metadata") {
    val text = """class Drawing {
      static void square(int size) { for (int i = 0; i < 4; i += 1) { Turtle.forward(size); Turtle.turnRight(90); } }
      public static void main(String[] argv) { square(10); square(40); }
      static void unused(boolean enabled) { if (enabled) { return; } }
    }"""
    val source = (for {
      parsed <- JavaTurtleSource.parse(text)
      structure <- JavaTurtleStructure.check(parsed)
      typed <- JavaTurtleSemantics.check(structure)
      resolved <- R.resolve(typed)
    } yield resolved).toOption.get
    val compiled = P.adapt(source).toOption.get
    assert(compiled.bindings.source eq source)
    assert(compiled.vm.fullProgram eq compiled.root)
    assertEquals(compiled.bindings.source.source, text)
    assertEquals(compiled.bindings.source.className, "Drawing")
    assertEquals(compiled.root.methods.map(_.binding.originalName), Vector("square", "main", "unused"))
    assert(compiled.root.entryPoint eq compiled.root.methods(1))
    assert(compiled.root.entryPoint.binding eq compiled.bindings.entryPoint)
    assertEquals(compiled.root.entryPoint.binding.id, source.entryPoint)
    val main = compiled.root.entryPoint
    assertEquals(main.binding.parameters.map(_.variable.name), Vector("argv"))
    assertEquals(main.binding.parameters.head.variable.valueType, R.ValueType.MainArguments)
    assertEquals(main.binding.parameters.head.definition, None)
    assertEquals(main.structureInfo.getChildrenAsReference(BeScope.GlobalScope()).map(_.childInfo.myRoleInParent), Seq(BeChildRole.BodySequence(0)))
    val children = compiled.root.structureInfo.getChildrenAsReference(BeScope.GlobalScope())
    assertEquals(children.map(_.childInfo.myRoleInParent), Seq(BeChildRole.MethodInClass(0), BeChildRole.MethodInClass(1), BeChildRole.MethodInClass(2)))
    compiled.root.methods.zip(source.methods).foreach { (method, original) =>
      assert(method.binding eq compiled.bindings.method(original.id).get)
      assertEquals(method.binding.parameters.map(_.variable), original.parameters)
      assertEquals(restoredJavaBlock(method.body), original.body)
    }
    assertEquals(compiled.root.staticInformationExpression.staticType, BeDataType.Unit)
    assert(compiled.root.staticInformationExpression.hasSideEffects)
  }

  test("Java VM declarations and assignment operators share their interned target definition") {
    val source = javaProgram("int value; value = 10; value += 2; value -= 1; value *= 3; value /= 2; value %= 4; Turtle.forward(value);")
    val compiled = P.adapt(source).toOption.get
    val statements = compiled.root.entryPoint.body.statements
    val P.Node.Declare(variable, definition, initial) = statements.head.node: @unchecked
    assertEquals(initial, None)
    assert(definition eq compiled.bindings.definition(variable).toOption.get)
    val operators = statements.slice(1, 7).map { statement => statement.node match {
      case P.Node.Assign(target, received, operator, value) =>
        assertEquals(target, variable)
        assert(received eq definition)
        assert(value.bindings eq compiled.bindings)
        val children = statement.structureInfo.getChildrenAsReference(BeScope.GlobalScope())
        assert(children.head.expr eq definition)
        assert(children(1).expr eq value.expression)
        assertEquals(children(1).childInfo.myRoleInParent, BeChildRole.ValueInAssignment)
        operator
      case _ => fail("Missing assignment")
    } }
    assertEquals(operators, Vector(R.AssignmentOperator.Set, R.AssignmentOperator.Add, R.AssignmentOperator.Subtract,
      R.AssignmentOperator.Multiply, R.AssignmentOperator.Divide, R.AssignmentOperator.Remainder))
    val P.Node.Call(P.CallTarget.Turtle(R.TurtleCommand.Forward), Vector(argument)) = statements.last.node: @unchecked
    val X.Node.Read(readVariable, reference) = argument.expression.node: @unchecked
    assertEquals(readVariable, variable)
    val BeUseValueReference(readDefinition) = reference.value: @unchecked
    assert(readDefinition eq definition)
    assertEquals(restoredJavaBlock(compiled.root.entryPoint.body), source.methods.head.body)
    val initialized = P.adapt(javaProgram("int value = 0;")).toOption.get
    val P.Node.Declare(_, _, zero) = initialized.root.entryPoint.body.statements.head.node: @unchecked
    assertEquals(zero.get.expression.node, X.Node.IntLiteral(0))
  }

  test("Java VM helper calls retain argument order caller reads and callee parameters") {
    val source = javaProgram("caller(5);", """
      static void pair(int first, int second, boolean enabled) { if (enabled) { Turtle.forward(first); Turtle.forward(second); } }
      static void caller(int size) { pair(size, size + 1, true); pair(size, size, false); }
    """)
    val compiled = P.adapt(source).toOption.get
    val callee = compiled.root.methods(0)
    val caller = compiled.root.methods(1)
    val callerDefinition = caller.binding.parameters.head.definition.get
    val parameters = callee.structureInfo.getChildrenAsReference(BeScope.GlobalScope()).take(3)
    assertEquals(parameters.map(_.childInfo.myRoleInParent), Seq(BeChildRole.FunctionParameter(0), BeChildRole.FunctionParameter(1), BeChildRole.FunctionParameter(2)))
    parameters.zip(callee.binding.parameters).foreach { (child, parameter) => assert(child.expr eq parameter.definition.get) }
    caller.body.statements.foreach { statement =>
      val P.Node.Call(P.CallTarget.Helper(target), arguments) = statement.node: @unchecked
      assert(target eq callee.binding)
      assertEquals(arguments.size, 3)
      assert(arguments.forall(_.bindings eq compiled.bindings))
      val children = statement.structureInfo.getChildrenAsReference(BeScope.GlobalScope())
      assertEquals(children.map(_.childInfo.myRoleInParent), Seq(BeChildRole.FunctionParameter(0), BeChildRole.FunctionParameter(1), BeChildRole.FunctionParameter(2)))
      assert(children.zip(arguments).forall((child, argument) => child.expr eq argument.expression))
      val X.Node.Read(variable, reference) = arguments.head.expression.node: @unchecked
      assertEquals(variable.id.method, caller.binding.id)
      val BeUseValueReference(definition) = reference.value: @unchecked
      assert(definition eq callerDefinition)
      assert(callee.binding.parameters.forall(parameter => !(definition eq parameter.definition.get)))
    }
    val P.Node.Call(_, repeated) = caller.body.statements(1).node: @unchecked
    assertEquals(restoredJavaExpression(repeated(0).expression), restoredJavaExpression(repeated(1).expression))
    assertEquals(restoredJavaBlock(caller.body), source.methods(1).body)
  }

  test("Java VM equal local names remain separate across methods branches loops and sources") {
    val source = javaProgram("first(10); second(20);", """
      static void first(int size) {
        if (true) { int value = size; Turtle.forward(value); }
        if (true) { int value = size + 1; Turtle.forward(value); }
        for (int i = 0; i < 1; i += 1) { Turtle.forward(i); }
        for (int i = 0; i < 1; i += 1) { Turtle.forward(i); }
      }
      static void second(int size) { Turtle.forward(size); }
    """)
    val compiled = P.adapt(source).toOption.get
    for name <- Seq("size", "value", "i") do {
      val bindings = compiled.bindings.variables.filter(_.variable.name == name)
      assertEquals(bindings.size, 2)
      assertEquals(bindings.map(_.variable.id).distinct.size, 2)
      assert(!(bindings(0).definition.get eq bindings(1).definition.get))
    }
    compiled.root.methods.zip(source.methods).foreach { (method, original) => assertEquals(restoredJavaBlock(method.body), original.body) }
    val another = P.adapt(source).toOption.get
    assert(!(another.bindings eq compiled.bindings))
    compiled.bindings.variables.zip(another.bindings.variables).foreach { (left, right) =>
      assertEquals(left.variable, right.variable)
      left.definition.foreach(definition => assert(!(definition eq right.definition.get)))
    }
  }

  test("Java VM control flow retains optional branches loop parts and statement roles") {
    val source = javaProgram("run(2, true);", """
      static void run(int size, boolean enabled) {
        ;
        if (enabled) {} if (enabled) {} else {} if (enabled) {} else {;}
        while (enabled) {
          for (int i = 0; i < size; i += 1, Turtle.turnRight(90)) { Turtle.forward(i); }
          enabled = false;
        }
        return;
      }
      static void unbounded() { for (;;) { return; } }
      static void constant() { for (; true;) { return; } }
    """)
    val compiled = P.adapt(source).toOption.get
    val statements = compiled.root.methods.head.body.statements
    assertEquals(statements.head.node, P.Node.Empty)
    assertEquals(statements.last.node, P.Node.Return)
    val P.Node.If(_, _, absent) = statements(1).node: @unchecked
    val P.Node.If(_, _, empty) = statements(2).node: @unchecked
    val P.Node.If(_, _, semicolon) = statements(3).node: @unchecked
    assertEquals(absent, None)
    assertEquals(empty.get.statements.size, 0)
    assertEquals(semicolon.get.statements.map(_.node), Vector(P.Node.Empty))
    val P.Node.While(_, body) = statements(4).node: @unchecked
    val loop = body.statements.head
    val P.Node.For(init, condition, update, repeated) = loop.node: @unchecked
    assertEquals(init.statements.size, 1)
    assert(condition.isDefined)
    assertEquals(update.statements.size, 2)
    assertEquals(repeated.statements.size, 1)
    val children = loop.structureInfo.getChildrenAsReference(BeScope.GlobalScope())
    assertEquals(children.map(_.childInfo.myRoleInParent), Seq(BeChildRole.BodySequence(0), BeChildRole.ConditionInControlStructure,
      BeChildRole.BodySequence(1), BeChildRole.BodySequence(2)))
    assert(children.head.expr eq init)
    assert(children(2).expr eq repeated)
    assert(children(3).expr eq update)
    val P.Node.For(_, omitted, _, _) = compiled.root.methods(1).body.statements.head.node: @unchecked
    val P.Node.For(_, explicit, _, _) = compiled.root.methods(2).body.statements.head.node: @unchecked
    assertEquals(omitted, None)
    assertEquals(explicit.get.expression.node, X.Node.BooleanLiteral(true))
    compiled.root.methods.zip(source.methods).foreach { (method, original) => assertEquals(restoredJavaBlock(method.body), original.body) }
  }

  test("Java VM program construction remains behind complete source validation") {
    for (main, helpers, expected) <- Seq(
      ("int value; Turtle.forward(value);", "", JavaTurtleSource.Problem.UninitializedVariable),
      ("for (int i = 0; i < 1; i += 1) {} Turtle.forward(i);", "", JavaTurtleSource.Problem.UnknownVariable),
      ("for (int i = 0; i < 1; value += 1) { int value = 0; }", "", JavaTurtleSource.Problem.UnknownVariable),
      ("draw(true);", "static void draw(int size) {}", JavaTurtleSource.Problem.ArgumentMismatch),
      ("missing();", "", JavaTurtleSource.Problem.UnknownMethod),
      ("draw(1);", "static void draw(int size) { main(); }", JavaTurtleSource.Problem.UnsupportedSyntax)) do {
      var adapted = false
      val result = for {
        parsed <- JavaTurtleSource.parse(s"class Drawing { $helpers public static void main(String[] args) { $main } }")
        structure <- JavaTurtleStructure.check(parsed)
        typed <- JavaTurtleSemantics.check(structure)
        resolved <- R.resolve(typed)
        vm <- { adapted = true; P.adapt(resolved) }
      } yield vm
      assertEquals(result.swap.toOption.get.problem, expected)
      assert(!adapted)
    }
    val source = javaProgram("run(true);", "static void run(boolean enabled) { int value; if (enabled) { value = 10; } else { return; } Turtle.forward(value); }")
    val compiled = P.adapt(source).toOption.get
    assertEquals(restoredJavaBlock(compiled.root.methods.head.body), source.methods.head.body)
  }

  test("Java VM programs handle checked method parameter local and nesting limits") {
    val helpers = (0 until 127).map(index => s"static void helper$index() {}").mkString(" ")
    val methods = P.adapt(javaProgram("", helpers)).toOption.get
    assertEquals(methods.root.methods.size, 128)
    assert(methods.root.entryPoint eq methods.root.methods.last)
    val parameters = (0 until 16).map(index => s"int p$index").mkString(", ")
    val locals = (0 until 128).map(index => s"int v$index = $index;").mkString(" ")
    val populatedSource = javaProgram("", s"static void populated($parameters) { $locals }")
    val populated = P.adapt(populatedSource).toOption.get
    assertEquals(populated.root.methods.head.binding.parameters.size, 16)
    assertEquals(populated.root.methods.head.body.statements.size, 128)
    assertEquals(restoredJavaBlock(populated.root.methods.head.body), populatedSource.methods.head.body)
    val nestedSource = javaProgram("while (true) { " * 12 + "return;" + " }" * 12)
    val nested = P.adapt(nestedSource).toOption.get
    assertEquals(restoredJavaBlock(nested.root.entryPoint.body), nestedSource.methods.head.body)
  }

  test("Java VM program elements reject generic execution editing and unsupported printers") {
    val compiled = P.adapt(javaProgram("draw(5);", "static void draw(int size) { int value = size; value += 1; if (value > 0) { Turtle.forward(value); } }")).toOption.get
    var pending: List[BeExpression] = List(compiled.root)
    var elements = 0
    while pending.nonEmpty do {
      val expression = pending.head
      pending = pending.tail
      pending = expression.structureInfo.getChildrenAsReference(BeScope.GlobalScope()).map(_.expr).toList ::: pending
      expression match {
        case element: P.Element =>
          elements += 1
          assert(element.structureInfo.withReplacedChildren(Map.empty) eq element)
          intercept[UnsupportedOperationException] { element.structureInfo.withReplacedChildren(Map(BeChildRole.NoRole -> literalOne)) }
          val state = BeSimulatorState(false, element, false, Nil, Nil, BeVirtualMachineState.emptyMachineState)
          intercept[UnsupportedOperationException] { element.expressionExecutor(BeSimulatorConfig(), state) }
          for language <- List(Python, Java, Cpp, JavaScript) do
            intercept[UnsupportedOperationException] { element.structureInfo.toStringInLanguage(language, English) }
        case _ => ()
      }
    }
    assertEquals(elements, 11)
    assertEquals(literalOne.structureInfo.toStringInLanguage(Python, English), "1")
  }

  test("Java VM execution distinguishes correct and incorrect parameterized squares") {
    val variants = Vector(
      ("for (int i = 0; i < 4; i += 1) { Turtle.forward(size); Turtle.turnRight(90); }", 10, true),
      ("for (int i = 0; i < 4; i += 1) { Turtle.forward(size); Turtle.turnRight(90); }", 40, true),
      ("for (int i = 0; i < 4; i += 1) { Turtle.forward(size); Turtle.turnRight(90); }", 100, true),
      ("int i = 0; while (i < 4) { Turtle.forward(size); Turtle.turnRight(90); i += 1; }", 10, true),
      ("int i = 0; while (i < 4) { Turtle.forward(size); Turtle.turnRight(90); i += 1; }", 40, true),
      ("int i = 0; while (i < 4) { Turtle.forward(size); Turtle.turnRight(90); i += 1; }", 100, true),
      ("for (int i = 0; i < 3; i += 1) { Turtle.forward(size); Turtle.turnRight(90); }", 40, false),
      ("for (int i = 0; i < 4; i += 1) { Turtle.forward(size); Turtle.turnRight(45); }", 40, false),
      ("for (int i = 0; i < 4; i += 1) { Turtle.forward(40); Turtle.turnRight(90); }", 10, false))
    for (body, size, correct) <- variants do {
      val source = javaProgram(s"square($size);", s"static void square(int size) { $body }")
      val program = P.adapt(source).toOption.get
      val execution = T.runVm(program)
      assertEquals(execution, T.run(source))
      assertEquals(execution.status, T.Status.Completed)
      assertEquals(execution.commands == Vector.fill(4)(Vector(forward(size), right(90))).flatten, correct)
      val method = program.root.methods.find(_.binding.originalName == "square").get.binding.id
      val invoked = T.invokeVm(program, method, Vector(E.Value.IntValue(size)))
      assertEquals(invoked, T.invoke(source, method, Vector(E.Value.IntValue(size))))
      assertEquals(invoked.commands, execution.commands)
    }
  }

  test("Java VM execution preserves pass by value and repeated helper frame isolation") {
    val source = javaProgram("int size = 5; outer(size); Turtle.forward(size); outer(7);", """
      static void outer(int size) { size += 10; inner(size); Turtle.forward(size); }
      static void inner(int size) { size *= 2; Turtle.forward(size); }
    """)
    val program = P.adapt(source).toOption.get
    val execution = T.runVm(program)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(30, 15, 5, 34, 17).map(forward))
    assertEquals(T.runVm(program), execution)
    val flags = javaProgram("boolean enabled = true; flip(enabled); if (enabled) { Turtle.forward(3); } flip(false);", """
      static void flip(boolean enabled) {
        enabled = !enabled; if (enabled) { Turtle.forward(1); } else { Turtle.forward(2); }
      }
    """)
    val booleanExecution = T.runVm(P.adapt(flags).toOption.get)
    assertEquals(booleanExecution, T.run(flags))
    assertEquals(booleanExecution.commands, Vector(2, 3, 1).map(forward))
  }

  test("Java VM execution keeps scopes loop updates and return control flow intact") {
    val source = javaProgram("draw(2); draw(1);", """
      static void draw(int count) {
        if (true) { int value = 10; Turtle.forward(value); }
        if (true) { int value = 20; Turtle.forward(value); }
        for (int i = 0; i < count; i += 1, Turtle.turnRight(i)) { Turtle.forward(i); }
        for (int i = 0; i < 0; i += 1) { Turtle.forward(999); }
        while (count < 0) { Turtle.forward(999); }
        for (;;) { Turtle.forward(30); return; }
      }
    """)
    val program = P.adapt(source).toOption.get
    val execution = T.runVm(program)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(forward(10), forward(20), forward(0), right(1), forward(1), right(2), forward(30),
      forward(10), forward(20), forward(0), right(1), forward(30)))
    val early = javaProgram("early(); Turtle.forward(2);", """
      static void early() {
        for (int i = 0; i < 4; Turtle.forward(999)) { Turtle.forward(1); return; }
        Turtle.forward(999);
      }
    """)
    val earlyExecution = T.runVm(P.adapt(early).toOption.get)
    assertEquals(earlyExecution, T.run(early))
    assertEquals(earlyExecution.commands, Vector(forward(1), forward(2)))
  }

  test("Java VM execution retains int32 arithmetic short circuit and failure prefixes") {
    val source = javaProgram("""
      int value = 2147483647; value += 1; Turtle.forward(value);
      value -= 1; Turtle.forward(value); value *= 2147483647; Turtle.forward(value);
      value = -2147483648; value /= -1; Turtle.forward(value); value %= -1; Turtle.forward(value);
      value = -9; value /= 2; Turtle.forward(value); value = -9; value %= 2; Turtle.forward(value);
      boolean enabled = false && 1 / 0 > 0;
      if (!enabled || 1 / 0 > 0) { Turtle.forward(2); }
    """)
    val execution = T.runVm(P.adapt(source).toOption.get)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(Int.MinValue, Int.MaxValue, 1, Int.MinValue, 0, -4, -1, 2).map(forward))
    for body <- Seq("int value = 5; value /= 0;", "int value = 5; value %= 0;", "pair(1, 5 / 0);") do {
      val failing = javaProgram(s"Turtle.forward(1); $body Turtle.forward(999);", "static void pair(int a, int b) { Turtle.forward(999); }")
      val program = P.adapt(failing).toOption.get
      val result = T.runVm(program)
      assertEquals(result, T.run(failing))
      assertEquals(result.status, T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero)))
      assertEquals(result.commands, Vector(forward(1)))
      assertEquals(T.runVm(program), result)
    }
  }

  test("Java VM execution uses the actual entry point and distinct Java method ids") {
    val text = """class Drawing {
      static void drawSquare(int size) { Turtle.forward(size); }
      public static void main(String[] argv) { drawSquare(10); draw_square(20); }
      static void draw_square(int size) { Turtle.turnRight(size); }
    }"""
    val source = (for {
      parsed <- JavaTurtleSource.parse(text)
      structure <- JavaTurtleStructure.check(parsed)
      typed <- JavaTurtleSemantics.check(structure)
      resolved <- R.resolve(typed)
    } yield resolved).toOption.get
    val program = P.adapt(source).toOption.get
    assertEquals(program.root.entryPoint.binding.id, R.MethodId(1))
    val execution = T.runVm(program)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(forward(10), right(20)))
    assertEquals(T.invokeVm(program, R.MethodId(2), Vector(E.Value.IntValue(30))).commands, Vector(right(30)))
    val another = P.adapt(javaProgram("int size = 7; Turtle.forward(size);")).toOption.get
    assertEquals(T.runVm(another).commands, Vector(forward(7)))
    assertEquals(T.runVm(program), execution)
  }

  test("Java VM execution rejects invalid limits and helper invocations before polling") {
    val source = javaProgram("draw(1);", "static void draw(int size) { Turtle.forward(size); }")
    val program = P.adapt(source).toOption.get
    val method = program.root.methods.head.binding.id
    val neverPoll = () => fail("Invalid input must not poll cancellation")
    for (id, arguments) <- Seq(method -> Vector.empty[E.Value], method -> Vector(E.Value.BooleanValue(true)),
      method -> Vector(E.Value.IntValue(1), E.Value.IntValue(2)), R.MethodId(999) -> Vector.empty[E.Value],
      program.root.entryPoint.binding.id -> Vector.empty[E.Value]) do {
      val result = T.invokeVm(program, id, arguments, isCancelled = neverPoll)
      assertEquals(result, singleMethodExecution(T.Status.Failed(T.Failure.InvalidInvocation), Vector.empty, 0))
      assertEquals(result, T.invoke(source, id, arguments, isCancelled = neverPoll))
    }
    for limits <- Seq(T.Limits(maxSteps = 0), T.Limits(maxSteps = -1), T.Limits(maxSteps = T.Limits.MaxSteps + 1),
      T.Limits(maxCommands = -1), T.Limits(maxCommands = T.Limits.MaxCommands + 1),
      T.Limits(maxCallDepth = 0), T.Limits(maxCallDepth = T.Limits.MaxCallDepth + 1),
      T.Limits(maxBlockDepth = 0), T.Limits(maxBlockDepth = T.Limits.MaxBlockDepth + 1)) do {
      val expected = singleMethodExecution(T.Status.Failed(T.Failure.InvalidLimits), Vector.empty, 0)
      assertEquals(T.runVm(program, limits, neverPoll), expected)
      assertEquals(T.invokeVm(program, method, Vector(E.Value.IntValue(1)), limits, neverPoll), expected)
    }
    assertEquals(T.runVm(P.adapt(javaProgram("")).toOption.get, T.Limits(maxCommands = 0)).status, T.Status.Completed)
    assertEquals(T.runVm(program, T.Limits(maxCommands = 0)).commands, Vector.empty)
    assertEquals(T.runVm(program, T.Limits(maxCommands = 0)).status, T.Status.LimitExceeded)
  }

  test("Java VM execution retains exact statement expression and command budgets") {
    val empty = P.adapt(javaProgram("")).toOption.get
    assertEquals(T.runVm(empty, T.Limits(maxSteps = 2)), singleMethodExecution(T.Status.Completed, Vector.empty, 2))
    val one = P.adapt(javaProgram("Turtle.forward(1);")).toOption.get
    assertEquals(T.runVm(one, T.Limits(maxSteps = 5)), singleMethodExecution(T.Status.Completed, Vector(forward(1)), 5))
    assertEquals(T.runVm(one, T.Limits(maxSteps = 4)), singleMethodExecution(T.Status.LimitExceeded, Vector.empty, 4))
    val expressions = P.adapt(javaProgram("int a = 1 + 2; int b = 3 + 4; Turtle.forward(a + b);")).toOption.get
    assertEquals(T.runVm(expressions, T.Limits(maxSteps = 15)), singleMethodExecution(T.Status.Completed, Vector(forward(10)), 15))
    assertEquals(T.runVm(expressions, T.Limits(maxSteps = 14)), singleMethodExecution(T.Status.LimitExceeded, Vector.empty, 14))
    val square = P.adapt(javaProgram("for (int i = 0; i < 4; i += 1) { Turtle.forward(10); Turtle.turnRight(90); }")).toOption.get
    val complete = T.runVm(square, T.Limits(maxCommands = 8))
    assertEquals(complete.status, T.Status.Completed)
    assertEquals(complete.commands.size, 8)
    val limited = T.runVm(square, T.Limits(maxCommands = 7))
    assertEquals(limited.status, T.Status.LimitExceeded)
    assertEquals(limited.commands, complete.commands.take(7))
  }

  test("Java VM endless loops cancellation and repeated execution use fresh state") {
    for body <- Seq("while (true) {}", "for (;;) {}", "for (;;) { ; }") do {
      val source = javaProgram(body)
      val program = P.adapt(source).toOption.get
      assertEquals(T.runVm(program, T.Limits(maxSteps = 20)), singleMethodExecution(T.Status.LimitExceeded, Vector.empty, 20))
      var polls = 0
      assertEquals(T.runVm(program, T.Limits(maxSteps = 30), () => { polls += 1; polls >= 10 }),
        singleMethodExecution(T.Status.Cancelled, Vector.empty, 9))
      assertEquals(polls, 10)
      assertEquals(T.runVm(program, T.Limits(maxSteps = 20)), T.run(source, T.Limits(maxSteps = 20)))
    }
    val program = P.adapt(javaProgram("Turtle.forward(1); Turtle.forward(2);")).toOption.get
    assertEquals(T.runVm(program, isCancelled = () => true), singleMethodExecution(T.Status.Cancelled, Vector.empty, 0))
    var polls = 0
    assertEquals(T.runVm(program, isCancelled = () => { polls += 1; polls >= 8 }),
      singleMethodExecution(T.Status.Cancelled, Vector(forward(1)), 7))
    assertEquals(polls, 8)
    assertEquals(T.runVm(program), singleMethodExecution(T.Status.Completed, Vector(forward(1), forward(2)), 8))
    val emptyLoop = P.adapt(javaProgram("while (true) {}")).toOption.get
    assertEquals(T.runVm(emptyLoop), singleMethodExecution(T.Status.LimitExceeded, Vector.empty, T.Limits.MaxSteps))
    val emitting = P.adapt(javaProgram("while (true) { Turtle.forward(1); }")).toOption.get
    val limited = T.runVm(emitting)
    assertEquals(limited.status, T.Status.LimitExceeded)
    assertEquals(limited.commands.size, T.Limits.MaxCommands)
    assert(limited.commands.forall(_ == forward(1)))
  }

  test("Java VM execution enforces exact call and block depths without recursive host stacks") {
    def chain(count: Int): R.ResolvedSource = javaProgram("m0();", (0 until count).map { index =>
      val body = if index == count - 1 then "Turtle.forward(1);" else s"m${index + 1}();"
      s"static void m$index() { $body }"
    }.mkString(" "))
    val allowedSource = chain(63)
    val allowed = P.adapt(allowedSource).toOption.get
    val full = T.runVm(allowed)
    assertEquals(full, T.run(allowedSource))
    assertEquals(full.status, T.Status.Completed)
    assertEquals(full.commands, Vector(forward(1)))
    val excessiveSource = chain(64)
    val excessive = P.adapt(excessiveSource).toOption.get
    val rejected = T.runVm(excessive)
    assertEquals(rejected, T.run(excessiveSource))
    assertEquals(rejected.status, T.Status.LimitExceeded)
    assertEquals(rejected.commands, Vector.empty)
    val direct = T.invokeVm(excessive, R.MethodId(0), Vector.empty)
    assertEquals(direct, T.invoke(excessiveSource, R.MethodId(0), Vector.empty))
    assertEquals(direct.status, T.Status.Completed)
    assertEquals(direct.commands, Vector(forward(1)))
    val nestedSource = javaProgram("if (true) { " * 8 + "Turtle.forward(1);" + " }" * 8)
    val nested = P.adapt(nestedSource).toOption.get
    val shallow = T.runVm(nested, T.Limits(maxBlockDepth = 8))
    assertEquals(shallow, T.run(nestedSource, T.Limits(maxBlockDepth = 8)))
    assertEquals(shallow.status, T.Status.LimitExceeded)
    assertEquals(shallow.commands, Vector.empty)
    val deep = T.runVm(nested, T.Limits(maxBlockDepth = 9))
    assertEquals(deep, T.run(nestedSource, T.Limits(maxBlockDepth = 9)))
    assertEquals(deep.status, T.Status.Completed)
    assertEquals(deep.commands, Vector(forward(1)))
    assertEquals(T.runVm(allowed), full)
  }

  test("Java recursive methods draw fractional lengths with bounded call evidence") {
    val helper = """
      static void split(int depth, double length) {
        if (depth == 0) { Turtle.forward(length); return; }
        split(depth - 1, length / 3);
        split(depth - 1, length / 3);
      }
    """
    for depth <- Vector(0, 1, 3) do {
      val source = javaProgram(s"split($depth, 10.5);", helper)
      val program = P.adapt(source).toOption.get
      val execution = T.runVm(program)
      val count = (1 << (depth + 1)) - 1
      val commands = Vector.fill(1 << depth)(T.Command(R.TurtleCommand.Forward, 10.5 / math.pow(3, depth)))
      assertEquals(execution, T.run(source))
      assertEquals(execution.status, T.Status.Completed)
      assertEquals(execution.commands, commands)
      assertEquals(execution.callEvidence, Some(T.CallEvidence(Vector(
        T.MethodCalls(R.MethodId(0), count, count - 1), T.MethodCalls(R.MethodId(1), 1, 0)), depth + 2)))
      val drawn = 1 << depth
      val recursive = if depth == 0 then 0 else drawn
      assertEquals(execution.drawingEvidence, Some(T.DrawingEvidence(Vector(
        T.MethodDrawing(R.MethodId(0), drawn, recursive), T.MethodDrawing(R.MethodId(1), drawn, 0)))))
      val arguments = Vector(E.Value.IntValue(depth), E.Value.DoubleValue(10.5))
      val invoked = T.invokeVm(program, R.MethodId(0), arguments)
      assertEquals(invoked, T.invoke(source, R.MethodId(0), arguments))
      assertEquals(invoked.commands, commands)
      assertEquals(invoked.callEvidence, Some(T.CallEvidence(Vector(T.MethodCalls(R.MethodId(0), count, count - 1)), depth + 1)))
      assertEquals(invoked.drawingEvidence, Some(T.DrawingEvidence(Vector(T.MethodDrawing(R.MethodId(0), drawn, recursive)))))
      assertEquals(T.runVm(program), execution)
    }
  }

  test("Java mutual recursion counts active methods rather than static call cycles") {
    val source = javaProgram("first(3);", """
      static void first(int depth) {
        Turtle.forward(depth);
        if (depth > 0) { second(depth - 1); }
        Turtle.turnRight(depth);
      }
      static void second(int depth) {
        if (depth > 0) { first(depth - 1); } else { Turtle.forward(9); }
      }
    """)
    val program = P.adapt(source).toOption.get
    val execution = T.runVm(program)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(forward(3), forward(1), forward(9), right(1), right(3)))
    assertEquals(execution.callEvidence, Some(T.CallEvidence(Vector(
      T.MethodCalls(R.MethodId(0), 2, 1), T.MethodCalls(R.MethodId(1), 2, 1), T.MethodCalls(R.MethodId(2), 1, 0)), 5)))
  }

  test("Java recursive frames preserve caller locals and sibling block declarations") {
    val source = javaProgram("int depth = 99; branch(1, 12.0); Turtle.forward(depth);", """
      static void branch(int depth, double length) {
        double local = length;
        if (depth == 0) {
          for (int i = 0; i < 2; i += 1) { int marker = i; Turtle.forward(local + marker); }
          return;
        }
        branch(depth - 1, length / 3);
        Turtle.forward(local);
        if (depth > 0) { int marker = 10; Turtle.turnRight(marker); }
        else { int marker = 20; Turtle.turnRight(marker); }
        branch(depth - 1, length / 2);
        Turtle.forward(local);
        for (int marker = 0; marker < 1; marker += 1) { Turtle.turnRight(marker); }
      }
    """)
    val execution = T.runVm(P.adapt(source).toOption.get)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(forward(4), forward(5), forward(12), right(10),
      forward(6), forward(7), forward(12), right(0), forward(99)))
    assertEquals(execution.callEvidence, Some(T.CallEvidence(Vector(
      T.MethodCalls(R.MethodId(0), 3, 2), T.MethodCalls(R.MethodId(1), 1, 0)), 3)))
  }

  test("Java returns unwind only the current recursive frame and its pending loops") {
    val source = javaProgram("draw(2); draw(0); Turtle.forward(9);", """
      static void draw(int depth) {
        if (depth == 0) { Turtle.forward(0); return; }
        while (true) {
          for (int i = 0; i < 2; i += 1) {
            draw(depth - 1);
            Turtle.forward(depth);
            return;
          }
        }
      }
    """)
    val program = P.adapt(source).toOption.get
    val execution = T.runVm(program)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(0, 1, 2, 0, 9).map(forward))
    assertEquals(execution.callEvidence, Some(T.CallEvidence(Vector(
      T.MethodCalls(R.MethodId(0), 4, 2), T.MethodCalls(R.MethodId(1), 1, 0)), 4)))
    assertEquals(T.runVm(program), execution)
  }

  test("Java unused cycles and repeated top-level calls do not prove executed recursion") {
    val source = javaProgram("for (int i = 0; i < 3; i += 1) { draw(i); }", """
      static void draw(int depth) {
        if (depth < 0) { draw(depth - 1); }
        Turtle.forward(depth);
      }
      static void unused() { unused(); }
    """)
    val program = P.adapt(source).toOption.get
    val execution = T.runVm(program)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(0, 1, 2).map(forward))
    assertEquals(execution.callEvidence, Some(T.CallEvidence(Vector(
      T.MethodCalls(R.MethodId(0), 3, 0), T.MethodCalls(R.MethodId(2), 1, 0)), 2)))
  }

  test("Java recursive call evidence excludes the rejected 65th frame") {
    val helper = "static void down(int depth) { if (depth > 0) { down(depth - 1); } else { Turtle.forward(1); } }"
    val source = javaProgram("down(62);", helper)
    val program = P.adapt(source).toOption.get
    val allowed = T.runVm(program)
    assertEquals(allowed, T.run(source))
    assertEquals(allowed.status, T.Status.Completed)
    assertEquals(allowed.commands, Vector(forward(1)))
    assertEquals(allowed.callEvidence, Some(T.CallEvidence(Vector(
      T.MethodCalls(R.MethodId(0), 63, 62), T.MethodCalls(R.MethodId(1), 1, 0)), 64)))
    val direct = T.invokeVm(program, R.MethodId(0), Vector(E.Value.IntValue(63)))
    assertEquals(direct.status, T.Status.Completed)
    assertEquals(direct.commands, Vector(forward(1)))
    assertEquals(direct.steps, 703)
    assertEquals(direct.callEvidence, Some(T.CallEvidence(Vector(T.MethodCalls(R.MethodId(0), 64, 63)), 64)))
    val rejected = T.invokeVm(program, R.MethodId(0), Vector(E.Value.IntValue(64)))
    assertEquals(rejected, T.invoke(source, R.MethodId(0), Vector(E.Value.IntValue(64))))
    assertEquals(rejected.status, T.Status.LimitExceeded)
    assertEquals(rejected.commands, Vector.empty)
    assertEquals(rejected.steps, 705)
    assertEquals(rejected.callEvidence, direct.callEvidence)
    assertEquals(T.invokeVm(program, R.MethodId(0), Vector(E.Value.IntValue(63))), direct)
  }

  test("Java recursion retains accepted evidence at budgets cancellation and errors") {
    val source = javaProgram("repeat(0);", "static void repeat(int n) { Turtle.forward(n); repeat(n + 1); }")
    val program = P.adapt(source).toOption.get
    val arguments = Vector(E.Value.IntValue(0))
    val commandLimit = T.invokeVm(program, R.MethodId(0), arguments, T.Limits(maxCommands = 2))
    assertEquals(commandLimit, T.invoke(source, R.MethodId(0), arguments, T.Limits(maxCommands = 2)))
    assertEquals(commandLimit, T.Execution(T.Status.LimitExceeded, Vector(forward(0), forward(1)), 23,
      Some(T.CallEvidence(Vector(T.MethodCalls(R.MethodId(0), 3, 2)), 3)),
      Some(T.DrawingEvidence(Vector(T.MethodDrawing(R.MethodId(0), 1, 1))))))
    val stepLimit = T.invokeVm(program, R.MethodId(0), arguments, T.Limits(maxSteps = 18))
    assertEquals(stepLimit, T.Execution(T.Status.LimitExceeded, Vector(forward(0), forward(1)), 18,
      Some(T.CallEvidence(Vector(T.MethodCalls(R.MethodId(0), 2, 1)), 2)),
      Some(T.DrawingEvidence(Vector(T.MethodDrawing(R.MethodId(0), 1, 1))))))
    var polls = 0
    val cancelled = T.invokeVm(program, R.MethodId(0), arguments, isCancelled = () => { polls += 1; polls >= 19 })
    assertEquals(cancelled, stepLimit.copy(status = T.Status.Cancelled))
    assertEquals(polls, 19)
    assertEquals(T.invokeVm(program, R.MethodId(0), arguments, isCancelled = () => true),
      T.Execution(T.Status.Cancelled, Vector.empty, 0, Some(T.CallEvidence(Vector.empty, 0)), Some(T.DrawingEvidence())))
    assertEquals(T.invokeVm(program, R.MethodId(0), arguments, T.Limits(maxCommands = 2)), commandLimit)
    val failedSource = javaProgram("fall(1);", """
      static void fall(int depth) {
        Turtle.forward(depth);
        if (depth > 0) { fall(depth - 1); }
        int invalid = 1 / 0;
      }
    """)
    val failed = T.runVm(P.adapt(failedSource).toOption.get)
    assertEquals(failed, T.run(failedSource))
    assertEquals(failed.status, T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero)))
    assertEquals(failed.commands, Vector(forward(1), forward(0)))
    assertEquals(failed.callEvidence, Some(T.CallEvidence(Vector(
      T.MethodCalls(R.MethodId(0), 2, 1), T.MethodCalls(R.MethodId(1), 1, 0)), 3)))
    assertEquals(failed.drawingEvidence, Some(T.DrawingEvidence(Vector(
      T.MethodDrawing(R.MethodId(0), 1, 0), T.MethodDrawing(R.MethodId(1), 1, 0)))))
  }

  test("Java drawing evidence attributes leaf helpers to their active recursive ancestors") {
    val source = javaProgram("split(2, 10.5);", """
      static void split(int depth, double length) {
        if (depth == 0) { stroke(length); return; }
        split(depth - 1, length / 3);
        split(depth - 1, length / 3);
      }
      static void stroke(double length) { Turtle.forward(length); }
    """)
    val program = P.adapt(source).toOption.get
    val execution = T.runVm(program)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands.size, 4)
    assertEquals(execution.drawingEvidence, Some(T.DrawingEvidence(Vector(
      T.MethodDrawing(R.MethodId(0), 4, 4), T.MethodDrawing(R.MethodId(1), 4, 0),
      T.MethodDrawing(R.MethodId(2), 4, 0)))))
    val invoked = T.invokeVm(program, R.MethodId(0), Vector(E.Value.IntValue(2), E.Value.DoubleValue(10.5)))
    assertEquals(invoked.drawingEvidence, Some(T.DrawingEvidence(Vector(
      T.MethodDrawing(R.MethodId(0), 4, 4), T.MethodDrawing(R.MethodId(1), 4, 0)))))
  }

  test("Java dummy recursion does not receive credit for a later iterative drawing") {
    val source = javaProgram("draw();", """
      static void unused(int depth) {
        if (depth > 0) { unused(depth - 1); }
      }
      static void draw() {
        unused(3);
        for (int i = 0; i < 4; i += 1) { Turtle.forward(10); Turtle.turnRight(90); }
      }
    """)
    val execution = T.runVm(P.adapt(source).toOption.get)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assert(execution.callEvidence.get.methods.exists(method => method.method == R.MethodId(0) && method.recursiveCalls == 3))
    assertEquals(execution.drawingEvidence, Some(T.DrawingEvidence(Vector(
      T.MethodDrawing(R.MethodId(1), 4, 0), T.MethodDrawing(R.MethodId(2), 4, 0)))))
  }

  test("Java drawing evidence separates a recursive tick from iterative shape commands") {
    val source = javaProgram("draw(1);", """
      static void draw(int depth) {
        if (depth > 0) { draw(depth - 1); }
        else { Turtle.forward(0.0001); }
        if (depth == 1) {
          for (int i = 0; i < 4; i += 1) { Turtle.forward(10); Turtle.turnRight(90); }
        }
      }
    """)
    val execution = T.runVm(P.adapt(source).toOption.get)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.drawingEvidence, Some(T.DrawingEvidence(Vector(
      T.MethodDrawing(R.MethodId(0), 5, 1), T.MethodDrawing(R.MethodId(1), 5, 0)))))
  }

  test("Java drawing evidence excludes turns and signed zero but retains backward strokes") {
    val source = javaProgram("draw(2);", """
      static void draw(int depth) {
        Turtle.forward(0.0);
        Turtle.forward(-0.0);
        Turtle.turnRight(60);
        if (depth > 0) { draw(depth - 1); }
        else { Turtle.forward(-2.5); }
      }
    """)
    val program = P.adapt(source).toOption.get
    val execution = T.runVm(program)
    assertEquals(execution, T.run(source))
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands.size, 10)
    assertEquals(execution.drawingEvidence, Some(T.DrawingEvidence(Vector(
      T.MethodDrawing(R.MethodId(0), 1, 1), T.MethodDrawing(R.MethodId(1), 1, 0)))))
    val zeroSource = javaProgram("draw(2);", """
      static void draw(int depth) {
        Turtle.forward(0);
        Turtle.turnRight(60);
        if (depth > 0) { draw(depth - 1); }
      }
    """)
    assertEquals(T.runVm(P.adapt(zeroSource).toOption.get).drawingEvidence, Some(T.DrawingEvidence()))
  }

  test("Java mutual recursion credits only methods repeated at the emitted stroke") {
    val helpers = """
      static void first(int depth) {
        if (depth > 0) { second(depth - 1); } else { Turtle.forward(1); }
      }
      static void second(int depth) {
        if (depth > 0) { first(depth - 1); } else { Turtle.forward(1); }
      }
    """
    for depth <- Vector(1, 2, 3) do {
      val source = javaProgram(s"first($depth);", helpers)
      val execution = T.runVm(P.adapt(source).toOption.get)
      assertEquals(execution, T.run(source))
      assertEquals(execution.status, T.Status.Completed)
      assertEquals(execution.commands, Vector(forward(1)))
      assertEquals(execution.drawingEvidence, Some(T.DrawingEvidence(Vector(
        T.MethodDrawing(R.MethodId(0), 1, if depth >= 2 then 1 else 0),
        T.MethodDrawing(R.MethodId(1), 1, if depth >= 3 then 1 else 0),
        T.MethodDrawing(R.MethodId(2), 1, 0)))))
    }
  }

  test("Java drawing evidence retains accepted prefixes without accumulating across attempts") {
    val source = javaProgram("draw(2);", """
      static void draw(int depth) {
        Turtle.forward(depth + 1);
        if (depth > 0) { draw(depth - 1); }
      }
    """)
    val program = P.adapt(source).toOption.get
    val arguments = Vector(E.Value.IntValue(2))
    val limited = T.invokeVm(program, R.MethodId(0), arguments, T.Limits(maxCommands = 2))
    assertEquals(limited.status, T.Status.LimitExceeded)
    assertEquals(limited.commands, Vector(forward(3), forward(2)))
    assertEquals(limited.drawingEvidence, Some(T.DrawingEvidence(Vector(T.MethodDrawing(R.MethodId(0), 2, 1)))))
    var polls = 0
    val cancelled = T.invokeVm(program, R.MethodId(0), arguments,
      isCancelled = () => { polls += 1; polls >= limited.steps })
    assertEquals(cancelled, limited.copy(status = T.Status.Cancelled, steps = limited.steps - 1))
    assertEquals(T.invokeVm(program, R.MethodId(0), arguments, T.Limits(maxCommands = 0)).drawingEvidence,
      Some(T.DrawingEvidence()))
    val complete = T.invokeVm(program, R.MethodId(0), arguments)
    assertEquals(complete.status, T.Status.Completed)
    assertEquals(complete.drawingEvidence, Some(T.DrawingEvidence(Vector(T.MethodDrawing(R.MethodId(0), 3, 2)))))
    assertEquals(T.invokeVm(program, R.MethodId(0), arguments), complete)
    assertEquals(T.invokeVm(program, R.MethodId(0), arguments, T.Limits(maxCommands = 2)), limited)
    val failedSource = javaProgram("draw(1);", """
      static void draw(int depth) {
        if (depth > 0) { draw(depth - 1); }
        Turtle.forward(2);
        Turtle.forward(1.0 / 0.0);
      }
    """)
    val failed = T.runVm(P.adapt(failedSource).toOption.get)
    assertEquals(failed, T.run(failedSource))
    assertEquals(failed.status, T.Status.Failed(T.Failure.NonFiniteCommand))
    assertEquals(failed.commands, Vector(forward(2)))
    assertEquals(failed.drawingEvidence, Some(T.DrawingEvidence(Vector(
      T.MethodDrawing(R.MethodId(0), 1, 1), T.MethodDrawing(R.MethodId(1), 1, 0)))))
  }

  test("Java invocation traces retain widened entry arguments through mutation loops and helpers") {
    val resolved = javaProgram("draw(1, 21);", """
      static void draw(int n, double length) {
        if (n == 0) { stroke(length); return; }
        n -= 1;
        length /= 3.0;
        for (int i = 0; i < 4; i += 1) { draw(n, length); }
      }
      static void stroke(double length) { Turtle.forward(length / 2); Turtle.forward(length / 2); }
    """)
    val program = P.adapt(resolved).toOption.get
    val arguments = Vector(E.Value.IntValue(1), E.Value.IntValue(21))
    val plain = T.invokeVm(program, R.MethodId(0), arguments)
    val traced = T.invokeVm(program, R.MethodId(0), arguments, traceInvocations = true)
    assertEquals(traced.copy(invocationEvidence = None), plain)
    assertEquals(T.invoke(resolved, R.MethodId(0), arguments, traceInvocations = true), traced)
    val root = T.MethodInvocation(None, Vector(E.Value.IntValue(1), E.Value.DoubleValue(21)), 0, Some(8))
    val leaves = (0 until 4).map(index => T.MethodInvocation(Some(0),
      Vector(E.Value.IntValue(0), E.Value.DoubleValue(7)), index * 2, Some(index * 2 + 2))).toVector
    assertEquals(traced.invocationEvidence, Some(T.InvocationEvidence(R.MethodId(0), root +: leaves)))
    assert(JavaTurtleInvocationTrace.valid(traced.invocationEvidence.get, traced.callEvidence.get, 8, true))
    val main = T.runVm(program, traceInvocations = true)
    assertEquals(main.invocationEvidence.get.activations.size, 1)
    assertEquals(main.invocationEvidence.get.activations.head.arguments, Vector.empty)
  }

  test("Java invocation traces leave interrupted activations open and keep later attempts independent") {
    val program = P.adapt(javaProgram("walk(2);", """
      static void walk(int n) { Turtle.forward(n + 1); if (n > 0) { walk(n - 1); } }
    """)).toOption.get
    val arguments = Vector(E.Value.IntValue(2))
    val limited = T.invokeVm(program, R.MethodId(0), arguments, T.Limits(maxCommands = 2), traceInvocations = true)
    assertEquals(limited.status, T.Status.LimitExceeded)
    assertEquals(limited.invocationEvidence.get.activations.map(_.lastCommand), Vector(None, None, None))
    assert(JavaTurtleInvocationTrace.valid(limited.invocationEvidence.get, limited.callEvidence.get, 2, false))
    val cancelled = T.invokeVm(program, R.MethodId(0), arguments, isCancelled = () => true, traceInvocations = true)
    assertEquals(cancelled.invocationEvidence, Some(T.InvocationEvidence(R.MethodId(0), Vector.empty)))
    assertEquals(T.invokeVm(program, R.MethodId(0), Vector.empty, traceInvocations = true).invocationEvidence, None)
    val complete = T.invokeVm(program, R.MethodId(0), arguments, traceInvocations = true)
    assertEquals(complete.invocationEvidence.get.activations.map(_.lastCommand), Vector.fill(3)(Some(3)))
    assertEquals(T.invokeVm(program, R.MethodId(0), arguments, traceInvocations = true), complete)
  }

  test("Java invocation capture is bounded without changing execution budgets") {
    val program = P.adapt(javaProgram("branch(5);", """
      static void branch(int n) {
        if (n > 0) { for (int i = 0; i < 4; i += 1) { branch(n - 1); } }
      }
    """)).toOption.get
    val plain = T.invokeVm(program, R.MethodId(0), Vector(E.Value.IntValue(5)))
    val traced = T.invokeVm(program, R.MethodId(0), Vector(E.Value.IntValue(5)), traceInvocations = true)
    assertEquals(traced.copy(invocationEvidence = None), plain)
    assertEquals(traced.status, T.Status.Completed)
    val trace = traced.invocationEvidence.get
    assertEquals(trace.activations.size, T.Limits.MaxInvocations)
    assert(trace.truncated)
    assert(trace.activations.forall(_.lastCommand.contains(0)))
    assert(JavaTurtleInvocationTrace.valid(trace, traced.callEvidence.get, 0, true))
  }

  private def kochMethod(leaf: String = "Turtle.forward(length);"): String = s"""
    static void koch(int depth, double length) {
      if (depth == 0) { $leaf return; }
      koch(depth - 1, length / 3.0);
      Turtle.turnRight(-60);
      koch(depth - 1, length / 3.0);
      Turtle.turnRight(120);
      koch(depth - 1, length / 3.0);
      Turtle.turnRight(-60);
      koch(depth - 1, length / 3.0);
    }
  """

  private def kochProgram(helpers: String = kochMethod()): (P.Program, R.MethodId) = {
    val source = s"""class Drawing {
      static void marker() { Turtle.forward(99); }
      $helpers
      public static void main(String[] args) { marker(); }
    }"""
    val program = P.compile(source).fold(problem => fail(problem.message), identity)
    val method = program.root.methods.find(_.binding.originalName == "koch").get.binding.id
    (program, method)
  }

  private def invokeKoch(program: P.Program, method: R.MethodId, example: K.KochCase): T.Execution =
    T.invokeVm(program, method, Vector(E.Value.IntValue(example.depth), E.Value.DoubleValue(example.length)))

  test("Koch assessment accepts the recursive curve and subdivided leaf helpers across scales") {
    val variants = Vector(kochMethod(), kochMethod("stroke(length);") + """
      static void stroke(double length) {
        Turtle.forward(-0.0);
        Turtle.forward(length / 2.0);
        Turtle.forward(length / 2.0);
      }
    """)
    for {
      helper <- variants
      depth <- 0 to 4
      length <- Vector(1e-12, 1.0, 10.5, 81.0, 1e300)
    } {
      val (program, method) = kochProgram(helper)
      val example = K.KochCase(depth, length)
      val execution = invokeKoch(program, method, example)
      assertEquals(execution.status, T.Status.Completed)
      val result = K.assess(program, method, example, execution)
      assertEquals(result.verdict, K.Verdict.Passed, clue = (depth, length, helper))
      assert(result.comparison.exists(_.matches), clue = result)
    }
  }

  test("Koch assessment rejects wrong scaling turns base cases and hardcoded lengths") {
    val variants = Vector(
      kochMethod().replace("length / 3.0", "length / 2.0"),
      kochMethod().replace("turnRight(120)", "turnRight(60)"),
      kochMethod().replace("turnRight(-60)", "turnRight(60)"),
      kochMethod("Turtle.forward(length * 0.8);"),
      kochMethod("Turtle.forward(1.0);"),
      kochMethod().replace("Turtle.turnRight(-60);", ""),
      kochMethod().replace("depth == 0", "depth <= 1")
    )
    val example = K.KochCase(2, 10.5)
    for helper <- variants do {
      val (program, method) = kochProgram(helper)
      val execution = invokeKoch(program, method, example)
      assertEquals(execution.status, T.Status.Completed)
      val result = K.assess(program, method, example, execution)
      assertEquals(result.verdict, K.Verdict.WrongDrawing, clue = helper)
      assert(result.comparison.exists(!_.matches), clue = result)
    }
  }

  test("Koch assessment rejects an iterative curve with unused empty or tiny recursion") {
    val drawing = """
      Turtle.forward(length / 3.0);
      Turtle.turnRight(-60);
      Turtle.forward(length / 3.0);
      Turtle.turnRight(120);
      Turtle.forward(length / 3.0);
      Turtle.turnRight(-60);
      Turtle.forward(length / 3.0);
    """
    val quiet = "static void quiet(int depth) { if (depth > 0) { quiet(depth - 1); } }"
    val preparations = Vector("", "quiet(2);", "for (int i = 0; i < 4; i += 1) { koch(0, 0.0); }",
      "for (int i = 0; i < 4; i += 1) { koch(0, length * 0.0000000001); }")
    for preparation <- preparations do {
      val (program, method) = kochProgram(s"""
        $quiet
        static void koch(int depth, double length) {
          if (depth == 0) { Turtle.forward(length); return; }
          $preparation
          $drawing
        }
      """)
      val example = K.KochCase(1, 10.5)
      val execution = invokeKoch(program, method, example)
      val result = K.assess(program, method, example, execution)
      assertEquals(execution.status, T.Status.Completed)
      assert(result.comparison.exists(_.matches), clue = preparation)
      assertEquals(result.verdict, K.Verdict.RecursionMismatch, clue = preparation)
    }
    val (baseProgram, baseMethod) = kochProgram("""
      static void koch(int depth, double length) {
        if (length > 0.0) { koch(0, 0.0); }
        Turtle.forward(length);
      }
    """)
    val base = K.KochCase(0, 10.5)
    assertEquals(K.assess(baseProgram, baseMethod, base, invokeKoch(baseProgram, baseMethod, base)).verdict,
      K.Verdict.RecursionMismatch)
  }

  test("Koch assessment validates cases and the exact assessed method signature") {
    val (program, method) = kochProgram()
    val valid = K.KochCase(0, 10.5)
    val execution = invokeKoch(program, method, valid)
    val invalidCases = Vector(K.KochCase(-1, 1.0), K.KochCase(5, 1.0), K.KochCase(Int.MaxValue, 1.0)) ++
      Vector(0.0, -0.0, -1.0, Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity)
        .map(K.KochCase(0, _)) ++ Vector(K.KochCase(1, java.lang.Double.MIN_VALUE),
          K.KochCase(1, java.lang.Double.MIN_VALUE * 2.0))
    invalidCases.foreach { example =>
      assertEquals(K.assess(program, method, example, execution).verdict, K.Verdict.InvalidCase)
    }
    val signatures: Vector[(String, Vector[E.Value])] = Vector(
      ("boolean depth, double length", Vector(E.Value.BooleanValue(false), E.Value.DoubleValue(10.5))),
      ("int depth, int length", Vector(E.Value.IntValue(0), E.Value.IntValue(10))),
      ("double depth, double length", Vector(E.Value.DoubleValue(0.0), E.Value.DoubleValue(10.5))),
      ("int depth, double length, int extra", Vector(E.Value.IntValue(0), E.Value.DoubleValue(10.5), E.Value.IntValue(0)))
    )
    signatures.foreach { (parameters, arguments) =>
      val (wrongProgram, wrongMethod) = kochProgram(s"static void koch($parameters) { Turtle.forward(length); }")
      val wrongExecution = T.invokeVm(wrongProgram, wrongMethod, arguments)
      assertEquals(wrongExecution.status, T.Status.Completed)
      assertEquals(K.assess(wrongProgram, wrongMethod, valid, wrongExecution).verdict, K.Verdict.InvalidMethod)
    }
    assertEquals(K.assess(program, program.root.entryPoint.binding.id, valid, T.runVm(program)).verdict, K.Verdict.InvalidMethod)
    assertEquals(K.assess(program, R.MethodId(127), valid, execution).verdict, K.Verdict.InvalidMethod)
  }

  test("Koch assessment never accepts an unfinished execution with a matching picture") {
    val (program, method) = kochProgram()
    val example = K.KochCase(0, 10.5)
    val execution = invokeKoch(program, method, example)
    val statuses = Vector(T.Status.Cancelled, T.Status.LimitExceeded,
      T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero)), T.Status.Failed(T.Failure.NonFiniteCommand))
    for status <- statuses do {
      val result = K.assess(program, method, example, execution.copy(status = status))
      assertEquals(result.verdict, K.Verdict.Incomplete(status))
      assertEquals(result.comparison, None)
    }
  }

  test("Koch assessment distinguishes unavailable worker evidence from incorrect recursion") {
    val (program, method) = kochProgram()
    for depth <- Vector(0, 1) do {
      val example = K.KochCase(depth, 10.5)
      val execution = invokeKoch(program, method, example)
      val legacy = Vector(execution.copy(callEvidence = None, drawingEvidence = None),
        execution.copy(callEvidence = None), execution.copy(drawingEvidence = None))
      legacy.foreach { result =>
        val assessment = K.assess(program, method, example, result)
        assertEquals(assessment.verdict, K.Verdict.MissingEvidence)
        assertEquals(assessment.comparison, None)
      }
    }
  }

  test("Koch assessment rejects inconsistent call and drawing evidence") {
    val (program, method) = kochProgram()
    val example = K.KochCase(1, 10.5)
    val execution = invokeKoch(program, method, example)
    val calls = execution.callEvidence.get
    val drawing = execution.drawingEvidence.get
    val called = calls.methods.head
    val drawn = drawing.methods.head
    val unknown = R.MethodId(127)
    val badCalls = Vector(
      calls.copy(methods = Vector.empty, maxDepth = 0),
      calls.copy(methods = calls.methods ++ calls.methods),
      calls.copy(methods = Vector(called.copy(method = unknown))),
      calls.copy(methods = Vector(called.copy(calls = -1))),
      calls.copy(methods = Vector(called.copy(calls = 0))),
      calls.copy(methods = Vector(called.copy(calls = T.Limits.MaxSteps + 1))),
      calls.copy(methods = Vector(called.copy(recursiveCalls = called.calls))),
      calls.copy(methods = Vector(called.copy(recursiveCalls = -1))),
      calls.copy(maxDepth = 0), calls.copy(maxDepth = T.Limits.MaxCallDepth + 1)
    )
    val badDrawing = Vector(
      drawing.copy(methods = Vector.empty),
      drawing.copy(methods = drawing.methods ++ drawing.methods),
      drawing.copy(methods = Vector(drawn.copy(method = unknown))),
      drawing.copy(methods = Vector(drawn.copy(forwardCommands = -1))),
      drawing.copy(methods = Vector(drawn.copy(forwardCommands = drawn.forwardCommands + 1))),
      drawing.copy(methods = Vector(drawn.copy(recursiveForwardCommands = -1))),
      drawing.copy(methods = Vector(drawn.copy(recursiveForwardCommands = drawn.forwardCommands + 1)))
    )
    val invalid = badCalls.map(value => execution.copy(callEvidence = Some(value))) ++
      badDrawing.map(value => execution.copy(drawingEvidence = Some(value))) ++
      Vector(execution.copy(steps = -1), execution.copy(steps = 0), execution.copy(steps = T.Limits.MaxSteps + 1))
    invalid.foreach { result =>
      val assessment = K.assess(program, method, example, result)
      assertEquals(assessment.verdict, K.Verdict.InvalidEvidence, clue = result)
      assertEquals(assessment.comparison, None)
    }
  }

  test("Koch assessment keeps executions independent and rejects stale method evidence") {
    val (program, method) = kochProgram()
    val example = K.KochCase(2, 10.5)
    val execution = invokeKoch(program, method, example)
    val beforeMethods = program.root.methods.map(_.binding)
    val first = K.assess(program, method, example, execution)
    assertEquals(first.verdict, K.Verdict.Passed)
    assert(K.assess(program, method, example.copy(depth = 1), execution).verdict != K.Verdict.Passed)
    val (changed, changedMethod) = kochProgram("static void another() {}" + kochMethod())
    assertEquals(K.assess(changed, changedMethod, example, execution).verdict, K.Verdict.InvalidEvidence)
    assertEquals(K.assess(changed, changedMethod, example, invokeKoch(changed, changedMethod, example)).verdict, K.Verdict.Passed)
    assertEquals(K.assess(program, method, example, execution), first)
    assertEquals(invokeKoch(program, method, example), execution)
    assertEquals(program.root.methods.map(_.binding), beforeMethods)
  }

  test("Java Python export is deterministic and keeps execution behind one entry point") {
    val program = P.adapt(javaProgram("draw(5);", "static void draw(int size) { Turtle.forward(size); }")).toOption.get
    val exported = Y.render(program)
    assertEquals(Y.render(program), exported)
    assert(exported.source.startsWith(s"def ${Y.EntryPoint}(method=None, arguments=(), *,"))
    assert(exported.source.endsWith("        return _result(problem.status, problem.problem)\n"))
    assert(exported.source.linesIterator.forall(line => line.isEmpty || line.startsWith("    ") || line.startsWith("def ")))
    assertEquals(exported.methods, Map("draw" -> R.MethodId(0)))
    assertEquals(T.runVm(program).commands, Vector(forward(5)))
    assertEquals(Y.render(P.adapt(javaProgram("")).toOption.get).methods, Map.empty[String, R.MethodId])
  }

  test("Java Python export uses source-local ids instead of Python names") {
    val program = P.adapt(javaProgram("drawSquare(10); draw_square(20);", """
      static void drawSquare(int lambda) { if (true) { int match = lambda; Turtle.forward(match); } }
      static void draw_square(int lambda) { if (true) { int match = lambda; Turtle.turnRight(match); } }
    """)).toOption.get
    val exported = Y.render(program)
    assertEquals(exported.methods, Map("drawSquare" -> R.MethodId(0), "draw_square" -> R.MethodId(1)))
    assert(exported.source.contains("def _method_0(_variable_0_0):"))
    assert(exported.source.contains("def _method_1(_variable_1_0):"))
    assert(exported.source.contains("_variable_0_1 = _value(lambda: _variable_0_0)"))
    assert(exported.source.contains("_variable_1_1 = _value(lambda: _variable_1_0)"))
    assert(!exported.source.contains("drawSquare"))
    assert(!exported.source.contains("draw_square"))
    assert(!exported.source.contains("match" + " ="))
  }

  test("Java Python export wraps arithmetic and uses truncating division and signed remainder") {
    val source = Y.render(P.adapt(javaProgram("""
      int n = 2147483647; n += 1; n -= 1; n *= 3; n /= -2; n %= 2;
      Turtle.forward(+n + -n - n * (n / 2) % 3);
    """)).toOption.get).source
    for operation <- Seq("_int32(_variable_0_1 +", "_int32(_variable_0_1 -", "_int32(_variable_0_1 *",
      "_divide(_variable_0_1,", "_remainder(_variable_0_1,", "_int32(-_value(") do assert(source.contains(operation))
    assert(source.contains("quotient = abs(left) // abs(right)"))
    assert(source.contains("return _int32(left - _divide(left, right) * right)"))
    val integerRuntime = source.substring(source.indexOf("def _divide("), source.indexOf("def _double_divide("))
    assert(!integerRuntime.contains("left / right"))
    assert(!integerRuntime.contains("left % right"))
    assert(source.contains("raise _Stop(\"Failed\", \"DivisionByZero\")"))
  }

  test("Java Python export keeps lazy boolean operands inside gated expressions") {
    val source = Y.render(P.adapt(javaProgram("""
      boolean b = false && 1 / 0 > 0;
      if (!b || 1 / 0 > 0) { Turtle.forward(1); }
      b = b == true; if (b != false && 1 < 2 && 2 <= 2 && 3 > 2 && 3 >= 3) {}
    """)).toOption.get).source
    assert(source.contains("_value(lambda: (_value(lambda: False) and _value(lambda:"))
    assert(source.contains(" or _value(lambda:"))
    assert(source.contains("_value(lambda: (not _value(lambda:"))
    for operator <- Seq(" == ", " != ", " < ", " <= ", " > ", " >= ") do assert(source.contains(operator))
  }

  test("Java Python export lowers for loops without running updates after return") {
    val source = Y.render(P.adapt(javaProgram("""
      for (int i = 0; i < 4; i += 1, Turtle.turnRight(i)) { Turtle.forward(i); return; }
    """)).toOption.get).source
    assert(source.contains("_block(2)\n            _gate()\n            _variable_0_1 = _value(lambda: 0)"))
    assert(source.contains("while _loop() and _value(lambda:"))
    assert(source.contains("                _block(3)"))
    val returned = source.indexOf("                return\n")
    val updated = source.indexOf("                _variable_0_1 = _int32(_variable_0_1 +")
    assert(returned >= 0 && updated > returned)
    assertEquals(source.linesIterator.count(_.trim == "_block(2)"), 1)
    val endless = Y.render(P.adapt(javaProgram("for (;;) { ; }")).toOption.get).source
    assert(endless.contains("while _loop():\n                _block(3)\n                _gate()"))
  }

  test("Java Python export preserves branches empty bodies and declaration identities") {
    val source = Y.render(P.adapt(javaProgram("""
      int n; n = 1;
      if (true) { int x = n; Turtle.forward(x); } else {}
      if (true) { int x = 2; Turtle.forward(x); }
      while (n < 0) {}
    """)).toOption.get).source
    assert(source.contains("_variable_0_1 = None"))
    assert(source.contains("_variable_0_2 = _value(lambda: _variable_0_1)"))
    assert(source.contains("_variable_0_3 = _value(lambda: 2)"))
    assert(source.contains("            else:\n                _block(2)"))
    assert(source.contains("while _loop() and _value(lambda: (_value(lambda: _variable_0_1) < _value(lambda: 0))):\n                _block(2)"))
  }

  test("Java Python export uses the actual main and exact helper parameter types") {
    val text = """class Drawing {
      static void draw(int n, boolean b) { if (b) { Turtle.forward(n); } }
      public static void main(String[] argv) { draw(5, true); }
      static void empty() {}
    }"""
    val program = (for {
      parsed <- JavaTurtleSource.parse(text)
      structure <- JavaTurtleStructure.check(parsed)
      typed <- JavaTurtleSemantics.check(structure)
      resolved <- R.resolve(typed)
      compiled <- P.adapt(resolved)
    } yield compiled).toOption.get
    val exported = Y.render(program)
    assertEquals(exported.methods, Map("draw" -> R.MethodId(0), "empty" -> R.MethodId(2)))
    assert(exported.source.contains("_target = _method_1"))
    assert(exported.source.contains("0: (_method_0, (\"int\", \"boolean\",)),"))
    assert(exported.source.contains("2: (_method_2, ()),"))
    assert(!exported.source.contains("1: (_method_1,"))
    assert(!exported.source.contains("argv"))
    assert(exported.source.contains("type(value) is bool"))
    assert(exported.source.contains("type(value) is int and -2147483648 <= value <= 2147483647"))
  }

  test("Java Python export isolates run state and validates limits before invocation") {
    val source = Y.render(P.adapt(javaProgram("Turtle.forward(1);")).toOption.get).source
    assert(source.contains("    _commands = []\n    _steps = 0\n    _call_depth = 0\n"))
    assert(source.contains("finally:\n            _leave("))
    assert(source.contains("    _active.pop()\n        _call_depth -= 1"))
    assert(source.contains(s"(max_steps, 1, ${T.Limits.MaxSteps})"))
    assert(source.contains(s"(max_commands, 0, ${T.Limits.MaxCommands})"))
    assert(source.contains(s"(max_call_depth, 1, ${T.Limits.MaxCallDepth})"))
    assert(source.contains(s"(max_block_depth, 1, ${T.Limits.MaxBlockDepth})"))
    assert(source.indexOf("return _result(\"Failed\", \"InvalidLimits\")") < source.indexOf("if method is None:"))
    assert(source.indexOf("if is_cancelled():") < source.indexOf("if _steps >= max_steps:"))
    assert(source.contains("if len(_commands) >= max_commands:"))
    assert(source.contains("if _call_depth >= max_call_depth:"))
    assert(source.contains("if depth > max_block_depth:"))
  }
}
