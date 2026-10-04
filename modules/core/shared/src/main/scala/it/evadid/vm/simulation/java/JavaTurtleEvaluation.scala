package it.evadid.vm.simulation.java

import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R}

object JavaTurtleEvaluation {
  enum Value {
    case IntValue(value: Int)
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

  def evaluate(expression: R.Expression, read: Reader, limits: Limits = Limits()): Either[Failure, Value] =
    evaluateWithGate(expression, read, limits, () => Right(()))

  private[java] def evaluateWithGate(expression: R.Expression, read: Reader, limits: Limits,
      beforeNode: () => Either[Failure, Unit]): Either[Failure, Value] =
    if limits.maxDepth <= 0 || limits.maxDepth > Limits.MaxDepth ||
        limits.maxNodes <= 0 || limits.maxNodes > Limits.MaxNodes then Left(Failure.LimitExceeded)
    else new Evaluator(read, limits, beforeNode).visit(expression, 1)

  private class Evaluator(read: Reader, limits: Limits, beforeNode: () => Either[Failure, Unit]) {
    private var visited = 0

    def visit(expression: R.Expression, depth: Int): Result[Value] =
      if depth > limits.maxDepth || visited >= limits.maxNodes then Left(Failure.LimitExceeded)
      else beforeNode().flatMap { _ =>
        visited += 1
        expression match {
          case R.IntLiteral(value) => Right(Value.IntValue(value))
          case R.BooleanLiteral(value) => Right(Value.BooleanValue(value))
          case R.Read(variable) => readVariable(variable)
          case R.Group(inner) => visit(inner, depth + 1)
          case R.Unary(operator, operand) =>
            visit(operand, depth + 1).flatMap { value =>
              (operator, value) match {
                case (R.UnaryOperator.Plus, Value.IntValue(number)) => Right(Value.IntValue(number))
                case (R.UnaryOperator.Negate, Value.IntValue(number)) => Right(Value.IntValue(JavaInt32.negate(number)))
                case (R.UnaryOperator.Not, Value.BooleanValue(flag)) => Right(Value.BooleanValue(!flag))
                case _ => Left(Failure.TypeMismatch)
              }
            }
          case R.Binary(operator, left, right) =>
            for {
              a <- visit(left, depth + 1)
              _ <- validLeft(operator, a)
              b <- visit(right, depth + 1)
              value <- binary(operator, a, b)
            } yield value
          case R.ShortCircuit(operator, left, right) =>
            boolean(left, depth + 1).flatMap { flag =>
              operator match {
                case R.ShortCircuitOperator.And =>
                  if !flag then Right(Value.BooleanValue(false))
                  else boolean(right, depth + 1).map(Value.BooleanValue(_))
                case R.ShortCircuitOperator.Or =>
                  if flag then Right(Value.BooleanValue(true))
                  else boolean(right, depth + 1).map(Value.BooleanValue(_))
              }
            }
        }
      }

    private def readVariable(variable: R.Variable): Result[Value] =
      if variable.valueType == R.ValueType.MainArguments then Left(Failure.TypeMismatch)
      else read(variable).flatMap { value =>
        (variable.valueType, value) match {
          case (R.ValueType.IntValue, _: Value.IntValue) => Right(value)
          case (R.ValueType.BooleanValue, _: Value.BooleanValue) => Right(value)
          case _ => Left(Failure.TypeMismatch)
        }
      }

    private def boolean(expression: R.Expression, depth: Int): Result[Boolean] =
      visit(expression, depth).flatMap {
        case Value.BooleanValue(flag) => Right(flag)
        case _ => Left(Failure.TypeMismatch)
      }

    private def validLeft(operator: R.BinaryOperator, value: Value): Result[Unit] =
      (operator, value) match {
        case (R.BinaryOperator.Equal | R.BinaryOperator.NotEqual, _: Value.IntValue | _: Value.BooleanValue) => Right(())
        case (R.BinaryOperator.Add | R.BinaryOperator.Subtract | R.BinaryOperator.Multiply |
            R.BinaryOperator.Divide | R.BinaryOperator.Remainder | R.BinaryOperator.Less |
            R.BinaryOperator.LessEqual | R.BinaryOperator.Greater | R.BinaryOperator.GreaterEqual,
            _: Value.IntValue) => Right(())
        case _ => Left(Failure.TypeMismatch)
      }

    private def binary(operator: R.BinaryOperator, left: Value, right: Value): Result[Value] =
      (left, right) match {
        case (Value.IntValue(a), Value.IntValue(b)) => operator match {
          case R.BinaryOperator.Add => Right(Value.IntValue(JavaInt32.add(a, b)))
          case R.BinaryOperator.Subtract => Right(Value.IntValue(JavaInt32.subtract(a, b)))
          case R.BinaryOperator.Multiply => Right(Value.IntValue(JavaInt32.multiply(a, b)))
          case R.BinaryOperator.Divide => arithmetic(JavaInt32.divide(a, b))
          case R.BinaryOperator.Remainder => arithmetic(JavaInt32.remainder(a, b))
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
        case _ => Left(Failure.TypeMismatch)
      }

    private def arithmetic(result: Either[JavaInt32.Error, Int]): Result[Value] =
      result.left.map {
        case JavaInt32.Error.DivisionByZero => Failure.DivisionByZero
      }.map(Value.IntValue(_))
  }
}
