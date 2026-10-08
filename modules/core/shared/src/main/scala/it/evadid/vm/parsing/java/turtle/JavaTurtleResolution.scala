package it.evadid.vm.parsing.java.turtle

import it.evadid.vm.parsing.java.clean.model.JavaAST.*
import it.evadid.vm.parsing.java.clean.model.JavaType
import it.evadid.vm.parsing.java.clean.model.JavaType.*
import it.evadid.vm.parsing.java.turtle.JavaTurtleSemantics.TypedSource
import it.evadid.vm.parsing.java.turtle.JavaTurtleSource.{Diagnostic, Problem}

object JavaTurtleResolution {
  enum ValueType {
    case IntValue, DoubleValue, BooleanValue, MainArguments
  }

  def isNumeric(valueType: ValueType): Boolean = valueType == ValueType.IntValue || valueType == ValueType.DoubleValue

  case class MethodId(index: Int)
  case class VariableId(method: MethodId, index: Int)
  case class Variable(id: VariableId, name: String, valueType: ValueType)

  enum UnaryOperator {
    case Plus, Negate, Not
  }

  enum BinaryOperator {
    case Add, Subtract, Multiply, Divide, Remainder
    case Less, LessEqual, Greater, GreaterEqual, Equal, NotEqual
  }

  enum ShortCircuitOperator {
    case And, Or
  }

  enum AssignmentOperator {
    case Set, Add, Subtract, Multiply, Divide, Remainder
  }

  enum TurtleCommand {
    case Forward, TurnRight
  }

  enum CallTarget {
    case Helper(method: MethodId)
    case Turtle(command: TurtleCommand)
  }

  sealed trait Expression {
    def valueType: ValueType
  }
  case class IntLiteral(value: Int) extends Expression {
    val valueType: ValueType = ValueType.IntValue
  }
  case class DoubleLiteral(value: Double) extends Expression {
    val valueType: ValueType = ValueType.DoubleValue
  }
  case class Widen(expression: Expression) extends Expression {
    val valueType: ValueType = ValueType.DoubleValue
  }
  case class BooleanLiteral(value: Boolean) extends Expression {
    val valueType: ValueType = ValueType.BooleanValue
  }
  case class Read(variable: Variable) extends Expression {
    def valueType: ValueType = variable.valueType
  }
  case class Group(expression: Expression) extends Expression {
    def valueType: ValueType = expression.valueType
  }
  case class Unary(operator: UnaryOperator, operand: Expression) extends Expression {
    def valueType: ValueType =
      if operator == UnaryOperator.Not then ValueType.BooleanValue else operand.valueType
  }
  case class Binary(operator: BinaryOperator, left: Expression, right: Expression) extends Expression {
    def valueType: ValueType = operator match {
      case BinaryOperator.Add | BinaryOperator.Subtract | BinaryOperator.Multiply |
          BinaryOperator.Divide | BinaryOperator.Remainder =>
        if left.valueType == ValueType.DoubleValue || right.valueType == ValueType.DoubleValue then ValueType.DoubleValue else ValueType.IntValue
      case _ => ValueType.BooleanValue
    }
  }
  case class ShortCircuit(operator: ShortCircuitOperator, left: Expression, right: Expression) extends Expression {
    val valueType: ValueType = ValueType.BooleanValue
  }

  sealed trait Statement
  case object Empty extends Statement
  case object Return extends Statement
  case class Declare(variable: Variable, initialValue: Option[Expression]) extends Statement
  case class Assign(variable: Variable, operator: AssignmentOperator, value: Expression) extends Statement
  case class Call(target: CallTarget, arguments: Vector[Expression]) extends Statement
  case class If(condition: Expression, thenBlock: Block, elseBlock: Option[Block]) extends Statement
  case class While(condition: Expression, body: Block) extends Statement
  case class For(init: Block, condition: Option[Expression], update: Block, body: Block) extends Statement
  case class Block(statements: Vector[Statement])
  case class Method(id: MethodId, name: String, parameters: Vector[Variable], body: Block)

  final class ResolvedSource private[JavaTurtleResolution](
      val typedSource: TypedSource,
      val methods: Vector[Method],
      val entryPoint: MethodId
  ) {
    def source: String = typedSource.structure.parsedSource.source
    def className: String = typedSource.structure.classDef.name
  }

  private type Result[A] = Either[Diagnostic, A]
  private type Env = Map[String, Variable]

  def resolve(source: TypedSource): Either[Diagnostic, ResolvedSource] = {
    val definitions = source.structure.methods.toVector.zipWithIndex.map { (method, index) =>
      method -> MethodId(index)
    }
    val methodIds = definitions.map { (method, id) => method.name -> id }.toMap
    val parameterTypes = definitions.map { (method, id) =>
      id -> method.parameters.map(parameter => kind(parameter.javaType).getOrElse(ValueType.MainArguments)).toVector
    }.toMap
    traverse(definitions) { (method, id) =>
      new MethodResolver(source.structure.classDef.name, id, methodIds, parameterTypes).resolve(method)
    }.map(methods => new ResolvedSource(source, methods, methodIds(source.structure.main.name)))
  }

  private def unsupported: Diagnostic =
    Diagnostic(Problem.UnsupportedSyntax, "This Java construct is not supported by the translation yet.", None)

  private def traverse[A, B](items: Seq[A])(resolve: A => Result[B]): Result[Vector[B]] =
    items.foldLeft[Result[Vector[B]]](Right(Vector.empty)) { (result, item) =>
      for { values <- result; value <- resolve(item) } yield values :+ value
    }

  private val unaryOperators = Map("+" -> UnaryOperator.Plus, "-" -> UnaryOperator.Negate, "!" -> UnaryOperator.Not)
  private val binaryOperators = Map(
    "+" -> BinaryOperator.Add, "-" -> BinaryOperator.Subtract, "*" -> BinaryOperator.Multiply,
    "/" -> BinaryOperator.Divide, "%" -> BinaryOperator.Remainder, "<" -> BinaryOperator.Less,
    "<=" -> BinaryOperator.LessEqual, ">" -> BinaryOperator.Greater, ">=" -> BinaryOperator.GreaterEqual,
    "==" -> BinaryOperator.Equal, "!=" -> BinaryOperator.NotEqual
  )
  private val assignmentOperators = Map(
    "=" -> AssignmentOperator.Set, "+=" -> AssignmentOperator.Add, "-=" -> AssignmentOperator.Subtract,
    "*=" -> AssignmentOperator.Multiply, "/=" -> AssignmentOperator.Divide, "%=" -> AssignmentOperator.Remainder
  )

  private def kind(javaType: JavaType[?]): Option[ValueType] = javaType match {
    case _: JAVA_INTEGER => Some(ValueType.IntValue)
    case _: JAVA_FLOAT => Some(ValueType.DoubleValue)
    case _: JAVA_BOOL => Some(ValueType.BooleanValue)
    case _ => None
  }

  private def widen(value: Expression, expected: ValueType): Result[Expression] =
    if value.valueType == expected then Right(value)
    else if value.valueType == ValueType.IntValue && expected == ValueType.DoubleValue then Right(Widen(value))
    else Left(unsupported)

  private class MethodResolver(className: String, methodId: MethodId, methodIds: Map[String, MethodId],
      parameterTypes: Map[MethodId, Vector[ValueType]]) {
    private var nextVariable = 0

    def resolve(method: JavaMethodDef): Result[Method] =
      for {
        parameters <- traverse(method.parameters)(parameter => variable(parameter, method.name == "main"))
        body <- block(method.body, parameters.map(parameter => parameter.name -> parameter).toMap)
      } yield Method(methodId, method.name, parameters, body)

    private def variable(declaration: JavaVariableDeclaration, mainArgument: Boolean = false): Result[Variable] = {
      val valueType = if mainArgument then Some(ValueType.MainArguments) else kind(declaration.javaType)
      valueType.toRight(unsupported).map { kind =>
        val result = Variable(VariableId(methodId, nextVariable), declaration.name, kind)
        nextVariable += 1
        result
      }
    }

    private def block(body: JavaExecutionBlock, env: Env): Result[Block] =
      statements(body.statements, env).map(_._1)

    private def statements(nodes: Seq[JavaStatement], env: Env): Result[(Block, Env)] =
      nodes.foldLeft[Result[(Block, Env)]](Right(Block(Vector.empty) -> env)) { (result, node) =>
        for {
          previous <- result
          resolved <- statement(node, previous._2)
        } yield Block(previous._1.statements :+ resolved._1) -> resolved._2
      }

    private def statement(node: JavaStatement, env: Env): Result[(Statement, Env)] = node match {
      case JavaEmptyStatement => Right(Empty -> env)
      case JavaReturnStatement(None) => Right(Return -> env)
      case declaration: JavaVariableDeclaration =>
        for {
          declared <- variable(declaration)
          active = env.updated(declared.name, declared)
          initial <- declaration.value.fold[Result[Option[Expression]]](Right(None))(
            value => expression(value, active).flatMap(widen(_, declared.valueType)).map(Some(_))
          )
        } yield Declare(declared, initial) -> active
      case JavaAssignment(target, value) => assignment(target, "=", value, env).map(_ -> env)
      case JavaAugAssignment(target, operator, value) => assignment(target, operator, value, env).map(_ -> env)
      case JavaAssignmentExpression(target, operator, value) => assignment(target, operator, value, env).map(_ -> env)
      case call: JavaFunctionCall => resolveCall(call, env).map(_ -> env)
      case call: JavaCallExpression => resolveCall(call, env).map(_ -> env)
      case JavaIfStatement(condition, thenBlock, elseBlock) =>
        for {
          test <- expression(condition, env)
          positive <- block(thenBlock, env)
          negative <- elseBlock.fold[Result[Option[Block]]](Right(None))(body => block(body, env).map(Some(_)))
        } yield If(test, positive, negative) -> env
      case JavaWhileStatement(condition, body) =>
        for { test <- expression(condition, env); resolved <- block(body, env) }
          yield While(test, resolved) -> env
      case loop: JavaForStatement =>
        for {
          initial <- statements(loop.init, env)
          condition <- loop.condition.fold[Result[Option[Expression]]](Right(None))(
            value => expression(value, initial._2).map(Some(_))
          )
          body <- block(loop.bodyBlock, initial._2)
          update <- statements(loop.update, initial._2)
        } yield For(initial._1, condition, update._1, body) -> env
      case _ => Left(unsupported)
    }

    private def local(target: JavaTarget, env: Env): Result[Variable] =
      if target.locationString.nonEmpty || target.sliceExpr.nonEmpty then Left(unsupported)
      else env.get(target.name).toRight(unsupported)

    private def assignment(target: JavaTarget, operator: String, value: JavaExpression, env: Env): Result[Assign] =
      for {
        variable <- local(target, env)
        operation <- assignmentOperators.get(operator).toRight(unsupported)
        resolved <- expression(value, env)
        converted <- widen(resolved, variable.valueType)
      } yield Assign(variable, operation, converted)

    private def expression(value: JavaExpression, env: Env): Result[Expression] = value match {
      case JavaParenthesizedExpression(inner) => expression(inner, env).map(Group(_))
      case target: JavaTarget => local(target, env).map(Read(_))
      case JavaLiteral(raw, _: JAVA_INTEGER) => raw.toIntOption.toRight(unsupported).map(IntLiteral(_))
      case JavaLiteral(raw, _: JAVA_FLOAT) => raw.toDoubleOption.filter(_.isFinite).toRight(unsupported).map(DoubleLiteral(_))
      case JavaLiteral(raw, _: JAVA_BOOL) => Right(BooleanLiteral(raw == "true"))
      case JavaOperationUnary("-", JavaLiteral("2147483648", _: JAVA_INTEGER)) => Right(IntLiteral(Int.MinValue))
      case JavaOperationUnary(operator, operand) =>
        for {
          operation <- unaryOperators.get(operator).toRight(unsupported)
          resolved <- expression(operand, env)
        } yield Unary(operation, resolved)
      case JavaOperationBinary(left, operator @ ("&&" | "||"), right) =>
        for { a <- expression(left, env); b <- expression(right, env) }
          yield ShortCircuit(if operator == "&&" then ShortCircuitOperator.And else ShortCircuitOperator.Or, a, b)
      case JavaOperationBinary(left, operator, right) =>
        for {
          operation <- binaryOperators.get(operator).toRight(unsupported)
          a <- expression(left, env)
          b <- expression(right, env)
          common = if isNumeric(a.valueType) && isNumeric(b.valueType) &&
            (a.valueType == ValueType.DoubleValue || b.valueType == ValueType.DoubleValue) then Some(ValueType.DoubleValue) else None
          promotedA <- common.fold[Result[Expression]](Right(a))(widen(a, _))
          promotedB <- common.fold[Result[Expression]](Right(b))(widen(b, _))
        } yield Binary(operation, promotedA, promotedB)
      case _ => Left(unsupported)
    }

    private def resolveCall(value: JavaExpression, env: Env): Result[Call] = {
      val target = value match {
        case JavaFunctionCall(JavaTarget(name, locations, None), arguments) if locations.isEmpty =>
          methodIds.get(name).map(id => CallTarget.Helper(id) -> arguments)
        case JavaCallExpression(JavaAttributeAccess(JavaTarget(receiver, locations, None), name), arguments)
            if locations.isEmpty && !env.contains(receiver) =>
          if receiver == className then methodIds.get(name).map(id => CallTarget.Helper(id) -> arguments)
          else if receiver == "Turtle" then name match {
            case "forward" => Some(CallTarget.Turtle(TurtleCommand.Forward) -> arguments)
            case "turnRight" => Some(CallTarget.Turtle(TurtleCommand.TurnRight) -> arguments)
            case _ => None
          }
          else None
        case _ => None
      }
      for {
        call <- target.toRight(unsupported)
        arguments <- traverse(call._2)(expression(_, env))
        converted <- call._1 match {
          case CallTarget.Helper(id) =>
            val expected = parameterTypes(id)
            if expected.size != arguments.size then Left(unsupported)
            else traverse(arguments.zip(expected)) { (value, kind) => widen(value, kind) }
          case CallTarget.Turtle(_) => Right(arguments)
        }
      } yield Call(call._1, converted)
    }
  }
}
