package it.evadid.vm.parsing.java.turtle

import it.evadid.vm.parsing.java.clean.model.JavaAST.*
import it.evadid.vm.parsing.java.clean.model.JavaType.*
import it.evadid.vm.parsing.java.turtle.JavaTurtleSource.{Diagnostic, Problem}
import it.evadid.vm.parsing.java.turtle.JavaTurtleStructure.StructuredSource

object JavaTurtleSemantics {
  final class TypedSource private[JavaTurtleSemantics](val structure: StructuredSource)

  def check(structure: StructuredSource): Either[Diagnostic, TypedSource] =
    new Checker(structure).check().map(_ => new TypedSource(structure))

  private enum Kind {
    case IntValue, DoubleValue, BooleanValue, MainArguments
  }

  private enum Constant {
    case Number(value: Int)
    case Decimal(value: Double)
    case Flag(value: Boolean)
  }

  private case class Env(bindings: Map[String, Kind], initialized: Set[String]) {
    def under(assigned: Set[String]): Env = copy(initialized = assigned)
    def project(parent: Env): Env = Env(parent.bindings, initialized.intersect(parent.bindings.keySet))
  }

  private case class Value(
      kind: Kind,
      constant: Option[Constant],
      whenTrue: Set[String],
      whenFalse: Set[String]
  )

  private type Result[A] = Either[Diagnostic, A]
  private type Flow = Option[Env]

  private def problem(kind: Problem, message: String): Diagnostic = Diagnostic(kind, message, None)
  private def unsupported(message: String): Diagnostic = problem(Problem.UnsupportedSyntax, message)

  private class Checker(structure: StructuredSource) {
    private val methods = structure.methods.map(method => method.name -> method).toMap
    private val objectMethods = Set("wait", "notify", "notifyAll", "toString", "hashCode", "getClass", "clone", "finalize")
    private var currentMethod = ""
    private var calls = Map.empty[String, Set[String]].withDefaultValue(Set.empty)

    def check(): Result[Unit] =
      structure.methods.foldLeft[Result[Unit]](Right(())) { (result, method) =>
        result.flatMap { _ =>
          currentMethod = method.name
          if method.name != "main" && method.parameters.isEmpty && objectMethods.contains(method.name) then
            Left(problem(Problem.UnsupportedStructure, s"The zero-argument method ${method.name} conflicts with a method inherited from Object."))
          else {
            val bindings = method.parameters.map { parameter =>
              parameter.name -> (if method.name == "main" then Kind.MainArguments else kind(parameter.javaType).get)
            }.toMap
            block(method.body, Env(bindings, bindings.keySet)).map(_ => ())
          }
        }
      }.flatMap(_ => checkRecursion())

    private def kind(javaType: it.evadid.vm.parsing.java.clean.model.JavaType[?]): Option[Kind] = javaType match {
      case _: JAVA_INTEGER => Some(Kind.IntValue)
      case _: JAVA_FLOAT => Some(Kind.DoubleValue)
      case _: JAVA_BOOL => Some(Kind.BooleanValue)
      case _ => None
    }

    private def block(body: JavaExecutionBlock, env: Env): Result[Flow] =
      statements(body.statements, env).map(_.map(_.project(env)))

    private def statements(nodes: Seq[JavaStatement], env: Env): Result[Flow] =
      nodes.foldLeft[Result[Flow]](Right(Some(env))) { (result, node) =>
        result.flatMap {
          case Some(active) => statement(node, active)
          case None => Left(problem(Problem.UnreachableStatement, "This statement cannot be reached."))
        }
      }

    private def statement(node: JavaStatement, env: Env): Result[Flow] = node match {
      case JavaEmptyStatement => Right(Some(env))
      case declaration: JavaVariableDeclaration => declare(declaration, env).map(Some(_))
      case JavaAssignment(target, value) => assign(target, "=", value, env).map(Some(_))
      case JavaAugAssignment(target, operator, value) => assign(target, operator, value, env).map(Some(_))
      case JavaAssignmentExpression(target, operator, value) => assign(target, operator, value, env).map(Some(_))
      case call: JavaFunctionCall => checkCall(call, env).map(_ => Some(env))
      case call: JavaCallExpression => checkCall(call, env).map(_ => Some(env))
      case JavaReturnStatement(None) => Right(None)
      case _: JavaReturnStatement => Left(problem(Problem.TypeMismatch, "A void method returns without a value."))
      case JavaIfStatement(condition, thenBody, elseBody) =>
        for {
          value <- boolean(condition, env)
          thenFlow <- block(thenBody, env.under(value.whenTrue))
          elseFlow <- elseBody.fold[Result[Flow]](Right(Some(env.under(value.whenFalse))))(
            body => block(body, env.under(value.whenFalse))
          )
        } yield join(thenFlow, elseFlow)
      case JavaWhileStatement(condition, body) =>
        for {
          value <- boolean(condition, env)
          _ <- reachableBody(value)
          _ <- block(body, env.under(value.whenTrue))
        } yield if value.constant.contains(Constant.Flag(true)) then None else Some(env.under(value.whenFalse))
      case loop: JavaForStatement => checkFor(loop, env)
      case _ => Left(unsupported("Use local variables, assignments, method calls, if, for, while or a bare return."))
    }

    private def join(left: Flow, right: Flow): Flow = (left, right) match {
      case (Some(a), Some(b)) => Some(a.under(a.initialized.intersect(b.initialized)))
      case (Some(a), None) => Some(a)
      case (None, Some(b)) => Some(b)
      case _ => None
    }

    private def reachableBody(value: Value): Result[Unit] =
      if value.constant.contains(Constant.Flag(false)) then
        Left(problem(Problem.UnreachableStatement, "The constant false condition makes this loop body unreachable."))
      else Right(())

    private def checkFor(loop: JavaForStatement, env: Env): Result[Flow] =
      for {
        initialized <- statements(loop.init, env)
        loopEnv <- initialized.toRight(unsupported("The for initializer must complete normally."))
        condition <- loop.condition.fold[Result[Value]](Right(flag(true, loopEnv)))(boolean(_, loopEnv))
        _ <- reachableBody(condition)
        bodyFlow <- block(loop.bodyBlock, loopEnv.under(condition.whenTrue))
        updateEnv = bodyFlow.getOrElse(loopEnv.under(loopEnv.bindings.keySet))
        _ <- statements(loop.update, updateEnv)
      } yield {
        if condition.constant.contains(Constant.Flag(true)) then None
        else Some(loopEnv.under(condition.whenFalse).project(env))
      }

    private def declare(declaration: JavaVariableDeclaration, env: Env): Result[Env] =
      if declaration.modifiers.nonEmpty then Left(unsupported("Local variable modifiers are not supported yet."))
      else if env.bindings.contains(declaration.name) then
        Left(problem(Problem.DuplicateDeclaration, s"The name ${declaration.name} is already declared in this scope."))
      else for {
        declaredKind <- kind(declaration.javaType).toRight(problem(Problem.UnsupportedType, "Use int, double or boolean for local variables."))
        declared = Env(env.bindings.updated(declaration.name, declaredKind), env.initialized - declaration.name)
        result <- declaration.value.fold[Result[Env]](Right(declared)) { expression =>
          value(expression, declared).flatMap { initial =>
            requireAssignable(initial.kind, declaredKind, s"The initial value of ${declaration.name} has the wrong type.")
              .map(_ => declared.under(declared.initialized + declaration.name))
          }
        }
      } yield result

    private def local(target: JavaTarget, env: Env): Result[Kind] =
      if target.locationString.nonEmpty || target.sliceExpr.nonEmpty then
        Left(unsupported("Assign to a local variable, not a field or an array element."))
      else env.bindings.get(target.name).toRight(problem(Problem.UnknownVariable, s"Declare ${target.name} before using it."))
        .flatMap { actual =>
          if actual == Kind.MainArguments then Left(problem(Problem.UnsupportedType, "The main argument is only a starting-method wrapper."))
          else Right(actual)
        }

    private def initialized(name: String, env: Env): Result[Unit] =
      if env.initialized.contains(name) then Right(())
      else Left(problem(Problem.UninitializedVariable, s"Give ${name} a value on every path before using it."))

    private def assign(target: JavaTarget, operator: String, expression: JavaExpression, env: Env): Result[Env] =
      for {
        targetKind <- local(target, env)
        _ <- if operator == "=" then Right(()) else initialized(target.name, env)
        assigned <- value(expression, env)
        _ <- if operator == "=" then requireAssignable(assigned.kind, targetKind, s"The value assigned to ${target.name} has the wrong type.")
          else if Set("+=", "-=", "*=", "/=", "%=").contains(operator) then
            for {
              _ <- requireNumeric(targetKind, "Arithmetic assignments need an int or double variable.")
              _ <- requireNumeric(assigned.kind, "Arithmetic assignments need an int or double value.")
              _ <- Either.cond(!(targetKind == Kind.IntValue && assigned.kind == Kind.DoubleValue), (),
                unsupported("Mixed double-to-int compound assignments are not supported yet."))
            } yield ()
          else Left(unsupported("This assignment operator is not supported yet."))
      } yield env.under(env.initialized + target.name)

    private def requireKind(actual: Kind, expected: Kind, message: String): Result[Unit] =
      if actual == expected then Right(()) else Left(problem(Problem.TypeMismatch, message))

    private def numeric(kind: Kind): Boolean = kind == Kind.IntValue || kind == Kind.DoubleValue

    private def assignable(actual: Kind, expected: Kind): Boolean =
      actual == expected || actual == Kind.IntValue && expected == Kind.DoubleValue

    private def requireAssignable(actual: Kind, expected: Kind, message: String): Result[Unit] =
      if assignable(actual, expected) then Right(()) else Left(problem(Problem.TypeMismatch, message))

    private def requireNumeric(actual: Kind, message: String): Result[Unit] =
      if numeric(actual) then Right(()) else Left(problem(Problem.TypeMismatch, message))

    private def plain(kind: Kind, constant: Option[Constant], env: Env): Value =
      constant match {
        case Some(Constant.Flag(flagValue)) => flag(flagValue, env)
        case _ => Value(kind, constant, env.initialized, env.initialized)
      }

    private def flag(flagValue: Boolean, env: Env): Value =
      Value(Kind.BooleanValue, Some(Constant.Flag(flagValue)),
        if flagValue then env.initialized else env.bindings.keySet,
        if flagValue then env.bindings.keySet else env.initialized)

    private def boolean(expression: JavaExpression, env: Env): Result[Value] =
      value(expression, env).flatMap { result =>
        requireKind(result.kind, Kind.BooleanValue, "Use a boolean condition.").map(_ => result)
      }

    private def value(expression: JavaExpression, env: Env): Result[Value] = expression match {
      case JavaParenthesizedExpression(inner) => value(inner, env)
      case target: JavaTarget =>
        for {
          actual <- local(target, env)
          _ <- initialized(target.name, env)
        } yield plain(actual, None, env)
      case JavaLiteral(raw, _: JAVA_INTEGER) =>
        raw.toIntOption.toRight(problem(Problem.IntegerRange, "This integer literal is outside the int range."))
          .map(number => plain(Kind.IntValue, Some(Constant.Number(number)), env))
      case JavaLiteral(raw, _: JAVA_FLOAT) =>
        raw.toDoubleOption.filter(_.isFinite)
          .toRight(problem(Problem.UnsupportedNumber, "Use a finite double literal."))
          .map(number => plain(Kind.DoubleValue, Some(Constant.Decimal(number)), env))
      case JavaLiteral(raw, _: JAVA_BOOL) => Right(flag(raw == "true", env))
      case _: JavaLiteral[?] => Left(problem(Problem.UnsupportedType, "Use int, double or boolean values."))
      case _: JavaFunctionCall | _: JavaCallExpression =>
        Left(problem(Problem.TypeMismatch, "A void method call does not produce a value."))
      case JavaOperationUnary("-", JavaLiteral("2147483648", _: JAVA_INTEGER)) =>
        Right(plain(Kind.IntValue, Some(Constant.Number(Int.MinValue)), env))
      case JavaOperationUnary(operator, operand) =>
        value(operand, env).flatMap { result =>
          operator match {
            case "!" => requireKind(result.kind, Kind.BooleanValue, "The ! operator needs a boolean value.").map { _ =>
              Value(Kind.BooleanValue, result.constant.collect { case Constant.Flag(flagValue) => Constant.Flag(!flagValue) },
                result.whenFalse, result.whenTrue)
            }
            case "+" | "-" => requireNumeric(result.kind, "Unary + and - need an int or double value.").map { _ =>
              val constant = result.constant.collect {
                case Constant.Number(number) => Constant.Number(if operator == "-" then -number else number)
                case Constant.Decimal(number) => Constant.Decimal(if operator == "-" then -number else number)
              }
              plain(result.kind, constant, env)
            }
            case _ => Left(unsupported("This unary operator is not supported yet."))
          }
        }
      case JavaOperationBinary(left, operator @ ("&&" | "||"), right) =>
        for {
          a <- boolean(left, env)
          b <- boolean(right, env.under(if operator == "&&" then a.whenTrue else a.whenFalse))
        } yield {
          val constant = for {
            leftConstant <- a.constant.collect { case Constant.Flag(flagValue) => flagValue }
            rightConstant <- b.constant.collect { case Constant.Flag(flagValue) => flagValue }
          } yield Constant.Flag(if operator == "&&" then leftConstant && rightConstant else leftConstant || rightConstant)
          if operator == "&&" then Value(Kind.BooleanValue, constant, b.whenTrue, a.whenFalse.intersect(b.whenFalse))
          else Value(Kind.BooleanValue, constant, a.whenTrue.intersect(b.whenTrue), b.whenFalse)
        }
      case JavaOperationBinary(left, operator, right) =>
        for {
          a <- value(left, env)
          b <- value(right, env)
          result <- binary(a, operator, b, env)
        } yield result
      case _ => Left(unsupported("Use int, double or boolean expressions without assignments, calls, fields or array access."))
    }

    private def binary(a: Value, operator: String, b: Value, env: Env): Result[Value] = {
      val arithmetic = Set("+", "-", "*", "/", "%")
      val comparison = Set("<", "<=", ">", ">=")
      if operator == "==" || operator == "!=" then {
        val compatible = a.kind == Kind.BooleanValue && b.kind == Kind.BooleanValue || numeric(a.kind) && numeric(b.kind)
        Either.cond(compatible, (), problem(Problem.TypeMismatch, "Compare two numeric values or two boolean values.")).map { _ =>
          val equal = (a.constant, b.constant) match {
            case (Some(Constant.Flag(left)), Some(Constant.Flag(right))) => Some(left == right)
            case (Some(left), Some(right)) => for { x <- decimalConstant(left); y <- decimalConstant(right) } yield x == y
            case _ => None
          }
          val constant = equal.map(value => Constant.Flag(if operator == "==" then value else !value))
          plain(Kind.BooleanValue, constant, env)
        }
      } else if arithmetic.contains(operator) || comparison.contains(operator) then
        for {
          _ <- requireNumeric(a.kind, "This operator needs int or double operands.")
          _ <- requireNumeric(b.kind, "This operator needs int or double operands.")
        } yield {
          val resultKind = if a.kind == Kind.DoubleValue || b.kind == Kind.DoubleValue then Kind.DoubleValue else Kind.IntValue
          val constant = (a.constant, b.constant) match {
            case (Some(Constant.Number(left)), Some(Constant.Number(right))) if resultKind == Kind.IntValue => operator match {
              case "+" => Some(Constant.Number(left + right))
              case "-" => Some(Constant.Number(left - right))
              case "*" => Some(Constant.Number(left * right))
              case "/" if right != 0 => Some(Constant.Number(left / right))
              case "%" if right != 0 => Some(Constant.Number(left % right))
              case "<" => Some(Constant.Flag(left < right))
              case "<=" => Some(Constant.Flag(left <= right))
              case ">" => Some(Constant.Flag(left > right))
              case ">=" => Some(Constant.Flag(left >= right))
              case _ => None
            }
            case (Some(left), Some(right)) => for {
              x <- decimalConstant(left)
              y <- decimalConstant(right)
              result <- operator match {
                case "+" => Some(Constant.Decimal(x + y))
                case "-" => Some(Constant.Decimal(x - y))
                case "*" => Some(Constant.Decimal(x * y))
                case "/" => Some(Constant.Decimal(x / y))
                case "%" => Some(Constant.Decimal(x % y))
                case "<" => Some(Constant.Flag(x < y))
                case "<=" => Some(Constant.Flag(x <= y))
                case ">" => Some(Constant.Flag(x > y))
                case ">=" => Some(Constant.Flag(x >= y))
                case _ => None
              }
            } yield result
            case _ => None
          }
          plain(if comparison.contains(operator) then Kind.BooleanValue else resultKind, constant, env)
        }
      else Left(unsupported("This binary operator is not supported yet."))
    }

    private def decimalConstant(value: Constant): Option[Double] = value match {
      case Constant.Number(number) => Some(number.toDouble)
      case Constant.Decimal(number) => Some(number)
      case _ => None
    }

    private def checkCall(expression: JavaExpression, env: Env): Result[Unit] = expression match {
      case JavaFunctionCall(JavaTarget(name, locations, None), arguments) if locations.isEmpty =>
        if name == "yield" then Left(unsupported("Call yield through the class name, not as an unqualified method."))
        else helperCall(name, arguments, env)
      case JavaCallExpression(JavaAttributeAccess(JavaTarget(receiver, locations, None), name), arguments) if locations.isEmpty =>
        if env.bindings.contains(receiver) then Left(problem(Problem.TypeMismatch, s"The variable $receiver is not a class receiver."))
        else if receiver == structure.classDef.name then helperCall(name, arguments, env)
        else if receiver == "Turtle" && Set("forward", "turnRight").contains(name) then
          if arguments.size != 1 then Left(problem(Problem.ArgumentMismatch, "The number of arguments does not match this method."))
          else value(arguments.head, env).flatMap { actual =>
            if numeric(actual.kind) then Right(())
            else Left(problem(Problem.ArgumentMismatch, "A Turtle argument must be an int or double value."))
          }
        else Left(unsupported("Use an own static method, Turtle.forward or Turtle.turnRight."))
      case _ => Left(unsupported("Call an own static method or a supported Turtle method directly."))
    }

    private def helperCall(name: String, arguments: Seq[JavaExpression], env: Env): Result[Unit] =
      if name == "main" then Left(unsupported("Call a helper method instead of calling main again."))
      else methods.get(name).toRight(problem(Problem.UnknownMethod, s"There is no supported static method named $name."))
        .flatMap { method =>
          checkArguments(arguments, method.parameters.map(parameter => kind(parameter.javaType).get), env).map { _ =>
            calls = calls.updated(currentMethod, calls(currentMethod) + name)
          }
        }

    private def checkArguments(arguments: Seq[JavaExpression], expected: Seq[Kind], env: Env): Result[Unit] =
      if arguments.size != expected.size then Left(problem(Problem.ArgumentMismatch, "The number of arguments does not match this method."))
      else arguments.zip(expected).foldLeft[Result[Unit]](Right(())) { case (result, (argument, expectedKind)) =>
        result.flatMap(_ => value(argument, env)).flatMap { actual =>
          if assignable(actual.kind, expectedKind) then Right(())
          else Left(problem(Problem.ArgumentMismatch, "An argument has the wrong type for this method."))
        }
      }

    private def checkRecursion(): Result[Unit] = {
      var visited = Set.empty[String]
      var active = Set.empty[String]
      def visit(name: String): Option[String] =
        if active.contains(name) then Some(name)
        else if visited.contains(name) then None
        else {
          active += name
          val cycle = calls(name).toSeq.sorted.iterator.map(visit).collectFirst { case Some(method) => method }
          active -= name
          visited += name
          cycle
        }
      structure.methods.iterator.map(method => visit(method.name)).collectFirst { case Some(name) => name } match {
        case Some(name) => Left(unsupported(s"Recursive calls involving $name are not supported yet."))
        case None => Right(())
      }
    }
  }
}
