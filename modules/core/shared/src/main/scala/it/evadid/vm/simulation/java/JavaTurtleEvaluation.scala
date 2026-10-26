package it.evadid.vm.simulation.java

import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R}

object JavaTurtleEvaluation {
  enum Value {
    case IntValue(value: Int)
    case DoubleValue(value: Double)
    case BooleanValue(value: Boolean)
  }

  enum Failure {
    case TypeMismatch, DivisionByZero, LimitExceeded, Cancelled
    case MissingValue(variable: R.VariableId)
  }

  object Limits {
    val MaxDepth = 256
    val MaxNodes = 10000
  }

  case class Limits(maxDepth: Int = Limits.MaxDepth, maxNodes: Int = Limits.MaxNodes)

  private type Result[A] = Either[Failure, A]
  type Reader = R.Variable => Either[Failure, Value]
  type Writer = (R.Variable, Value) => Either[Failure, Unit]

  private[java] case class Observed(value: Value, dependency: Option[Int])
  private[java] trait Observer {
    def isRecording: Boolean
    def read(path: Vector[Int], variable: R.Variable, value: Value): Option[Int]
    def write(path: Vector[Int], variable: R.Variable, value: Value, dependencies: Vector[Int]): Option[Int]
    def result(path: Vector[Int], value: Value, dependencies: Vector[Int]): Option[Int]
  }

  def widen(value: Value, expected: R.ValueType): Either[Failure, Value] = (expected, value) match {
    case (R.ValueType.IntValue, _: Value.IntValue) | (R.ValueType.DoubleValue, _: Value.DoubleValue) |
        (R.ValueType.BooleanValue, _: Value.BooleanValue) => Right(value)
    case (R.ValueType.DoubleValue, Value.IntValue(number)) => Right(Value.DoubleValue(number.toDouble))
    case _ => Left(Failure.TypeMismatch)
  }

  def arithmetic(operator: R.BinaryOperator, left: Value, right: Value): Either[Failure, Value] =
    binary(operator, left, right)

  def evaluate(expression: R.Expression, read: Reader, limits: Limits = Limits()): Either[Failure, Value] =
    evaluateWithGate(expression, read, limits, () => Right(()))

  def evaluateMutating(expression: R.Expression, read: Reader, write: Writer,
      limits: Limits = Limits()): Either[Failure, Value] =
    evaluateWithGate(expression, read, limits, () => Right(()), write)

  private[java] def evaluateWithGate(expression: R.Expression, read: Reader, limits: Limits,
      beforeNode: () => Either[Failure, Unit], write: Writer = (_, _) => Left(Failure.TypeMismatch)): Either[Failure, Value] =
    evaluated(expression, read, limits, beforeNode, write, Unobserved)

  private[java] def evaluateObservedWithGate(expression: R.Expression, read: Reader, limits: Limits,
      beforeNode: () => Either[Failure, Unit], write: Writer, observer: Option[Observer]): Either[Failure, Observed] =
    observer match {
      case Some(observer) => evaluated(expression, read, limits, beforeNode, write, new Tracing(observer))
      case None => evaluateWithGate(expression, read, limits, beforeNode, write).map(Observed(_, None))
    }

  private def evaluated[A](expression: R.Expression, read: Reader, limits: Limits,
      beforeNode: () => Either[Failure, Unit], write: Writer, observation: Observation[A]): Result[A] =
    if limits.maxDepth <= 0 || limits.maxDepth > Limits.MaxDepth ||
        limits.maxNodes <= 0 || limits.maxNodes > Limits.MaxNodes then Left(Failure.LimitExceeded)
    else new Evaluator(read, write, limits, beforeNode, observation).visit(expression, 1, Vector.empty)

  private trait Observation[A] {
    def value(result: A): Value
    def child(path: Vector[Int], index: Int): Vector[Int]
    def result(path: Vector[Int], value: Value): A
    def result(path: Vector[Int], value: Value, operand: A): A
    def result(path: Vector[Int], value: Value, left: A, right: A): A
    def read(path: Vector[Int], variable: R.Variable, value: Value): A
    def updated(path: Vector[Int], variable: R.Variable, old: A, value: Value, prefix: Boolean): A
  }

  private object Unobserved extends Observation[Value] {
    def value(result: Value): Value = result
    def child(path: Vector[Int], index: Int): Vector[Int] = path
    def result(path: Vector[Int], value: Value): Value = value
    def result(path: Vector[Int], value: Value, operand: Value): Value = value
    def result(path: Vector[Int], value: Value, left: Value, right: Value): Value = value
    def read(path: Vector[Int], variable: R.Variable, value: Value): Value = value
    def updated(path: Vector[Int], variable: R.Variable, old: Value, value: Value, prefix: Boolean): Value =
      if prefix then value else old
  }

  private class Tracing(observer: Observer) extends Observation[Observed] {
    def value(result: Observed): Value = result.value
    def child(path: Vector[Int], index: Int): Vector[Int] = if observer.isRecording then path :+ index else path
    def result(path: Vector[Int], value: Value): Observed =
      Observed(value, if observer.isRecording then observer.result(path, value, Vector.empty) else None)
    def result(path: Vector[Int], value: Value, operand: Observed): Observed =
      Observed(value, if observer.isRecording then observer.result(path, value, operand.dependency.toVector) else None)
    def result(path: Vector[Int], value: Value, left: Observed, right: Observed): Observed =
      Observed(value, if observer.isRecording then observer.result(path, value, left.dependency.toVector ++ right.dependency.toVector) else None)
    def read(path: Vector[Int], variable: R.Variable, value: Value): Observed =
      Observed(value, if observer.isRecording then observer.read(path, variable, value) else None)
    def updated(path: Vector[Int], variable: R.Variable, old: Observed, value: Value, prefix: Boolean): Observed = {
      val result = if prefix then value else old.value
      if !observer.isRecording then Observed(result, None)
      else {
        val written = observer.write(path, variable, value, old.dependency.toVector)
        Observed(result, if observer.isRecording then
          observer.result(path, result, (if prefix then written else old.dependency).toVector) else None)
      }
    }
  }

  private class Evaluator[A](read: Reader, write: Writer, limits: Limits, beforeNode: () => Either[Failure, Unit],
      observation: Observation[A]) {
    private var visited = 0

    def visit(expression: R.Expression, depth: Int, path: Vector[Int]): Result[A] =
      if depth > limits.maxDepth || visited >= limits.maxNodes then Left(Failure.LimitExceeded)
      else beforeNode().flatMap { _ =>
        visited += 1
        expression match {
          case R.IntLiteral(value) => Right(observation.result(path, Value.IntValue(value)))
          case R.DoubleLiteral(value) => Right(observation.result(path, Value.DoubleValue(value)))
          case R.Widen(inner) => visit(inner, depth + 1, observation.child(path, 0)).flatMap { operand => observation.value(operand) match {
            case Value.IntValue(number) => Right(observation.result(path, Value.DoubleValue(number.toDouble), operand))
            case _ => Left(Failure.TypeMismatch)
          }
          }
          case R.BooleanLiteral(value) => Right(observation.result(path, Value.BooleanValue(value)))
          case R.Read(variable) => readVariable(variable, path)
          case R.Update(variable, operator, prefix) =>
            for {
              old <- readVariable(variable, path)
              updated <- binary(if operator == R.UpdateOperator.Increment then R.BinaryOperator.Add else R.BinaryOperator.Subtract,
                observation.value(old), Value.IntValue(1))
              _ <- write(variable, updated)
            } yield observation.updated(path, variable, old, updated, prefix)
          case R.Group(inner) => visit(inner, depth + 1, observation.child(path, 0)).map(value => observation.result(path, observation.value(value), value))
          case R.Unary(operator, operand) =>
            visit(operand, depth + 1, observation.child(path, 0)).flatMap { value =>
              val evaluated: Result[Value] = (operator, observation.value(value)) match {
                case (R.UnaryOperator.Plus, Value.IntValue(number)) => Right(Value.IntValue(number))
                case (R.UnaryOperator.Negate, Value.IntValue(number)) => Right(Value.IntValue(JavaInt32.negate(number)))
                case (R.UnaryOperator.Plus, Value.DoubleValue(number)) => Right(Value.DoubleValue(number))
                case (R.UnaryOperator.Negate, Value.DoubleValue(number)) => Right(Value.DoubleValue(-number))
                case (R.UnaryOperator.Not, Value.BooleanValue(flag)) => Right(Value.BooleanValue(!flag))
                case _ => Left(Failure.TypeMismatch)
              }
              evaluated.map(observation.result(path, _, value))
            }
          case R.Binary(operator, left, right) =>
            for {
              a <- visit(left, depth + 1, observation.child(path, 0))
              _ <- validLeft(operator, observation.value(a))
              b <- visit(right, depth + 1, observation.child(path, 1))
              value <- binary(operator, observation.value(a), observation.value(b))
            } yield observation.result(path, value, a, b)
          case R.ShortCircuit(operator, left, right) =>
            boolean(left, depth + 1, observation.child(path, 0)).flatMap { first =>
              val flag = observation.value(first).asInstanceOf[Value.BooleanValue].value
              operator match {
                case R.ShortCircuitOperator.And =>
                  if !flag then Right(observation.result(path, Value.BooleanValue(false), first))
                  else boolean(right, depth + 1, observation.child(path, 1)).map(second => observation.result(path, observation.value(second), first, second))
                case R.ShortCircuitOperator.Or =>
                  if flag then Right(observation.result(path, Value.BooleanValue(true), first))
                  else boolean(right, depth + 1, observation.child(path, 1)).map(second => observation.result(path, observation.value(second), first, second))
              }
            }
        }
      }

    private def readVariable(variable: R.Variable, path: Vector[Int]): Result[A] =
      if variable.valueType == R.ValueType.MainArguments then Left(Failure.TypeMismatch)
      else read(variable).flatMap { value =>
        val checked: Result[Value] = (variable.valueType, value) match {
          case (R.ValueType.IntValue, _: Value.IntValue) => Right(value)
          case (R.ValueType.DoubleValue, _: Value.DoubleValue) => Right(value)
          case (R.ValueType.BooleanValue, _: Value.BooleanValue) => Right(value)
          case _ => Left(Failure.TypeMismatch)
        }
        checked.map(value => observation.read(path, variable, value))
      }

    private def boolean(expression: R.Expression, depth: Int, path: Vector[Int]): Result[A] =
      visit(expression, depth, path).flatMap { value => observation.value(value) match {
        case _: Value.BooleanValue => Right(value)
        case _ => Left(Failure.TypeMismatch)
      }
      }

    private def validLeft(operator: R.BinaryOperator, value: Value): Result[Unit] =
      (operator, value) match {
        case (R.BinaryOperator.Equal | R.BinaryOperator.NotEqual, _: Value.IntValue | _: Value.DoubleValue | _: Value.BooleanValue) => Right(())
        case (R.BinaryOperator.Add | R.BinaryOperator.Subtract | R.BinaryOperator.Multiply |
            R.BinaryOperator.Divide | R.BinaryOperator.Remainder | R.BinaryOperator.Less |
            R.BinaryOperator.LessEqual | R.BinaryOperator.Greater | R.BinaryOperator.GreaterEqual,
            _: Value.IntValue | _: Value.DoubleValue) => Right(())
        case _ => Left(Failure.TypeMismatch)
      }

  }

  private def binary(operator: R.BinaryOperator, left: Value, right: Value): Result[Value] =
    (left, right) match {
      case (Value.IntValue(a), Value.IntValue(b)) => operator match {
        case R.BinaryOperator.Add => Right(Value.IntValue(JavaInt32.add(a, b)))
        case R.BinaryOperator.Subtract => Right(Value.IntValue(JavaInt32.subtract(a, b)))
        case R.BinaryOperator.Multiply => Right(Value.IntValue(JavaInt32.multiply(a, b)))
        case R.BinaryOperator.Divide => intArithmetic(JavaInt32.divide(a, b))
        case R.BinaryOperator.Remainder => intArithmetic(JavaInt32.remainder(a, b))
        case R.BinaryOperator.Less => Right(Value.BooleanValue(a < b))
        case R.BinaryOperator.LessEqual => Right(Value.BooleanValue(a <= b))
        case R.BinaryOperator.Greater => Right(Value.BooleanValue(a > b))
        case R.BinaryOperator.GreaterEqual => Right(Value.BooleanValue(a >= b))
        case R.BinaryOperator.Equal => Right(Value.BooleanValue(a == b))
        case R.BinaryOperator.NotEqual => Right(Value.BooleanValue(a != b))
      }
      case (Value.BooleanValue(a), Value.BooleanValue(b)) => operator match {
        case R.BinaryOperator.Equal => Right(Value.BooleanValue(a == b))
        case R.BinaryOperator.NotEqual => Right(Value.BooleanValue(a != b))
        case _ => Left(Failure.TypeMismatch)
      }
      case (Value.IntValue(a), Value.DoubleValue(b)) => doubleBinary(operator, a.toDouble, b)
      case (Value.DoubleValue(a), Value.IntValue(b)) => doubleBinary(operator, a, b.toDouble)
      case (Value.DoubleValue(a), Value.DoubleValue(b)) => doubleBinary(operator, a, b)
      case _ => Left(Failure.TypeMismatch)
    }

  private def doubleBinary(operator: R.BinaryOperator, left: Double, right: Double): Result[Value] = Right(operator match {
    case R.BinaryOperator.Add => Value.DoubleValue(left + right)
    case R.BinaryOperator.Subtract => Value.DoubleValue(left - right)
    case R.BinaryOperator.Multiply => Value.DoubleValue(left * right)
    case R.BinaryOperator.Divide => Value.DoubleValue(left / right)
    case R.BinaryOperator.Remainder => Value.DoubleValue(left % right)
    case R.BinaryOperator.Less => Value.BooleanValue(left < right)
    case R.BinaryOperator.LessEqual => Value.BooleanValue(left <= right)
    case R.BinaryOperator.Greater => Value.BooleanValue(left > right)
    case R.BinaryOperator.GreaterEqual => Value.BooleanValue(left >= right)
    case R.BinaryOperator.Equal => Value.BooleanValue(left == right)
    case R.BinaryOperator.NotEqual => Value.BooleanValue(left != right)
  })

  private def intArithmetic(result: Either[JavaInt32.Error, Int]): Result[Value] =
    result.left.map {
      case JavaInt32.Error.DivisionByZero => Failure.DivisionByZero
    }.map(Value.IntValue(_))
}
