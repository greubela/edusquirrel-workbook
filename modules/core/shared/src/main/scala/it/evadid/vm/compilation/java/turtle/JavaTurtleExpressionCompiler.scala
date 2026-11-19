package it.evadid.vm.compilation.java.turtle

import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.vm.compilation.java.turtle.JavaTurtleSymbolResolver as R
import it.evadid.vm.compilation.java.turtle.JavaTurtleSourceParser.{Diagnostic, Problem}
import it.evadid.vm.compilation.java.turtle.JavaTurtleExecutionBindings.Bindings
import it.evadid.vm.simulation.java.JavaTurtleEvaluation as E
import it.evadid.vm.types.BeUseValueReference


object JavaTurtleExpressionCompiler {
  enum Node {
    case IntLiteral(value: Int)
    case DoubleLiteral(value: Double)
    case Widen(expression: Expression)
    case BooleanLiteral(value: Boolean)
    case Read(variable: R.Variable, reference: BeUseValue)
    case Update(variable: R.Variable, reference: BeUseValue, operator: R.UpdateOperator, prefix: Boolean)
    case Group(expression: Expression)
    case Unary(operator: R.UnaryOperator, operand: Expression)
    case Binary(operator: R.BinaryOperator, left: Expression, right: Expression)
    case ShortCircuit(operator: R.ShortCircuitOperator, left: Expression, right: Expression)
  }

  final class Expression private[JavaTurtleExpressionCompiler](val node: Node, val valueType: R.ValueType)

  final class BoundExpression private[JavaTurtleExpressionCompiler](val bindings: Bindings, val expression: Expression,
      private[vm] val resolved: R.Expression, private[vm] val definitions: Map[R.Variable, BeDefineVariable])

  type Reader = BeDefineVariable => Either[E.Failure, E.Value]
  type Writer = (BeDefineVariable, E.Value) => Either[E.Failure, Unit]

  // This binds one expression; it does not validate a complete program or enable legacy exporters.
  def adapt(bindings: Bindings, expression: R.Expression): Either[Diagnostic, BoundExpression] =
    checkSize(expression).flatMap { _ =>
      new Compiler(bindings).compile(expression).map { compiled =>
        val definitions = collectDefinitions(compiled)
        new BoundExpression(bindings, compiled, restore(compiled), definitions)
      }
    }

  def evaluate(expression: BoundExpression, read: Reader, limits: E.Limits = E.Limits()): Either[E.Failure, E.Value] =
    E.evaluate(expression.resolved, variable =>
      expression.definitions.get(variable).toRight(E.Failure.MissingValue(variable.id)).flatMap(read), limits)

  def evaluateMutating(expression: BoundExpression, read: Reader, write: Writer,
      limits: E.Limits = E.Limits()): Either[E.Failure, E.Value] =
    E.evaluateMutating(expression.resolved,
      variable => expression.definitions.get(variable).toRight(E.Failure.MissingValue(variable.id)).flatMap(read),
      (variable, value) => expression.definitions.get(variable).toRight(E.Failure.MissingValue(variable.id))
        .flatMap(definition => write(definition, value)), limits)

  private def mismatch: Diagnostic =
    Diagnostic(Problem.TypeMismatch, "Use matching numeric or boolean operands for this Java expression.", None)

  private class Compiler(bindings: Bindings) {
    def compile(expression: R.Expression): Either[Diagnostic, Expression] = expression match {
      case R.IntLiteral(value) => Right(new Expression(Node.IntLiteral(value), R.ValueType.IntValue))
      case R.DoubleLiteral(value) =>
        if !value.isFinite then Left(Diagnostic(Problem.UnsupportedNumber, "Use a finite double literal.", None))
        else Right(new Expression(Node.DoubleLiteral(value), R.ValueType.DoubleValue))
      case R.Widen(inner) => compile(inner).flatMap { child =>
        if child.valueType != R.ValueType.IntValue then Left(mismatch)
        else Right(new Expression(Node.Widen(child), R.ValueType.DoubleValue))
      }
      case R.BooleanLiteral(value) => Right(new Expression(Node.BooleanLiteral(value), R.ValueType.BooleanValue))
      case R.Read(variable) => bindings.reference(variable).map { reference =>
        new Expression(Node.Read(variable, reference), variable.valueType)
      }
      case R.Update(variable, operator, prefix) =>
        if !R.isNumeric(variable.valueType) then Left(mismatch)
        else bindings.reference(variable).map { reference =>
          new Expression(Node.Update(variable, reference, operator, prefix), variable.valueType)
        }
      case R.Group(inner) => compile(inner).map(child => new Expression(Node.Group(child), child.valueType))
      case R.Unary(operator, operand) =>
        compile(operand).flatMap { child =>
          val valid = if operator == R.UnaryOperator.Not then child.valueType == R.ValueType.BooleanValue else R.isNumeric(child.valueType)
          if !valid then Left(mismatch)
          else Right(new Expression(Node.Unary(operator, child), child.valueType))
        }
      case R.Binary(operator, left, right) =>
        for {
          a <- compile(left)
          b <- compile(right)
          _ <- Either.cond((R.isNumeric(a.valueType) && R.isNumeric(b.valueType)) ||
            (a.valueType == R.ValueType.BooleanValue && b.valueType == R.ValueType.BooleanValue &&
              (operator == R.BinaryOperator.Equal || operator == R.BinaryOperator.NotEqual)),
            (), mismatch)
        } yield new Expression(Node.Binary(operator, a, b), expression.valueType)
      case R.ShortCircuit(operator, left, right) =>
        for {
          a <- compile(left)
          b <- compile(right)
          _ <- Either.cond(a.valueType == R.ValueType.BooleanValue && b.valueType == R.ValueType.BooleanValue, (), mismatch)
        } yield new Expression(Node.ShortCircuit(operator, a, b), R.ValueType.BooleanValue)
    }
  }

  private def checkSize(expression: R.Expression): Either[Diagnostic, Unit] = {
    var pending = List(expression -> 1)
    var visited = 0
    while pending.nonEmpty do {
      val (node, depth) = pending.head
      pending = pending.tail
      visited += 1
      if visited > JavaTurtleInputLimits.MaxAstNodes || depth > JavaTurtleInputLimits.MaxAstDepth then
        return Left(Diagnostic(Problem.InputLimit, "Split this Java expression into smaller expressions.", None))
      node match {
        case R.IntLiteral(_) | R.DoubleLiteral(_) | R.BooleanLiteral(_) | R.Read(_) | R.Update(_, _, _) => ()
        case R.Widen(inner) => pending = (inner -> (depth + 1)) :: pending
        case R.Group(inner) => pending = (inner -> (depth + 1)) :: pending
        case R.Unary(_, operand) => pending = (operand -> (depth + 1)) :: pending
        case R.Binary(_, left, right) => pending = (left -> (depth + 1)) :: (right -> (depth + 1)) :: pending
        case R.ShortCircuit(_, left, right) => pending = (left -> (depth + 1)) :: (right -> (depth + 1)) :: pending
      }
    }
    Right(())
  }

  private def collectDefinitions(expression: Expression): Map[R.Variable, BeDefineVariable] = {
    var definitions = Map.empty[R.Variable, BeDefineVariable]
    var pending = List(expression)
    while pending.nonEmpty do {
      val current = pending.head
      pending = pending.tail
      current.node match {
        case Node.Read(variable, reference) => reference.value match {
          case BeUseValueReference(definition) => definitions = definitions.updated(variable, definition)
          case _ => throw new IllegalStateException("Expected a bound Java variable reference.")
        }
        case Node.Update(variable, reference, _, _) => reference.value match {
          case BeUseValueReference(definition) => definitions = definitions.updated(variable, definition)
          case _ => throw new IllegalStateException("Expected a bound Java variable reference.")
        }
        case Node.Group(inner) => pending = inner :: pending
        case Node.Widen(inner) => pending = inner :: pending
        case Node.Unary(_, operand) => pending = operand :: pending
        case Node.Binary(_, left, right) => pending = left :: right :: pending
        case Node.ShortCircuit(_, left, right) => pending = left :: right :: pending
        case Node.IntLiteral(_) | Node.DoubleLiteral(_) | Node.BooleanLiteral(_) => ()
      }
    }
    definitions
  }

  private def restore(expression: Expression): R.Expression = expression.node match {
    case Node.IntLiteral(value) => R.IntLiteral(value)
    case Node.DoubleLiteral(value) => R.DoubleLiteral(value)
    case Node.Widen(inner) => R.Widen(restore(inner))
    case Node.BooleanLiteral(value) => R.BooleanLiteral(value)
    case Node.Read(variable, _) => R.Read(variable)
    case Node.Update(variable, _, operator, prefix) => R.Update(variable, operator, prefix)
    case Node.Group(inner) => R.Group(restore(inner))
    case Node.Unary(operator, operand) => R.Unary(operator, restore(operand))
    case Node.Binary(operator, left, right) => R.Binary(operator, restore(left), restore(right))
    case Node.ShortCircuit(operator, left, right) => R.ShortCircuit(operator, restore(left), restore(right))
  }
}
