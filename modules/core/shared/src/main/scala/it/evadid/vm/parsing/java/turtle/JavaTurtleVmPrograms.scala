package it.evadid.vm.parsing.java.turtle

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmBindings as V, JavaTurtleVmExpressions as X}
import it.evadid.vm.parsing.java.turtle.JavaTurtleSource.{Diagnostic, Problem}
import it.evadid.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}

object JavaTurtleVmPrograms {
  enum CallTarget {
    case Helper(method: V.MethodBinding)
    case Turtle(command: R.TurtleCommand)
  }

  enum Node {
    case Empty, Return
    case Declare(variable: R.Variable, definition: BeDefineVariable, initial: Option[X.BoundExpression])
    case Assign(variable: R.Variable, definition: BeDefineVariable, operator: R.AssignmentOperator, value: X.BoundExpression)
    case Call(target: CallTarget, arguments: Vector[X.BoundExpression])
    case If(condition: X.BoundExpression, positive: Block, negative: Option[Block])
    case While(condition: X.BoundExpression, body: Block)
    case For(init: Block, condition: Option[X.BoundExpression], update: Block, body: Block)
  }

  sealed abstract class Element private[JavaTurtleVmPrograms]() extends BeExpression {
    protected def children: Vector[(BeChildRole, BeExpression)]

    override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
      override def hasSideEffects: Boolean = true
    }

    override lazy val structureInfo: BeExpressionStructureInfo[Element] = new BeExpressionStructureInfo[Element](this) {
      override def withReplacedChildren(replacements: Map[BeChildRole, BeExpression]): Element = {
        if replacements.nonEmpty then
          throw new UnsupportedOperationException("Recompile edited Java source to replace program children.")
        Element.this
      }

      override def toJavaStyleLines(info: BeChildInfo): Seq[BeSegmentedCodeElement] =
        asExpressionLine(ControlFlowDown(), info)

      override def getChildrenAndExtension(scope: BeScope): Seq[BeExpressionNode] =
        children.map { (role, child) => BeExpressionReference(BeChildInfo(role, scope), child) }
    }

    override def expressionExecutor(config: BeSimulatorConfig, state: BeSimulatorState): BeExpressionExecutor =
      throw new UnsupportedOperationException("Java program nodes do not support the generic simulator.")
  }

  final class Statement private[JavaTurtleVmPrograms](val node: Node) extends Element {
    protected def children: Vector[(BeChildRole, BeExpression)] = node match {
      case Node.Empty | Node.Return => Vector.empty
      case Node.Declare(_, definition, initial) =>
        Vector(BeChildRole.NoRole -> definition) ++ initial.toVector.map(value =>
          BeChildRole.ValueForVariable(definition) -> value.expression)
      case Node.Assign(_, definition, _, value) =>
        Vector(BeChildRole.NoRole -> definition, BeChildRole.ValueInAssignment -> value.expression)
      case Node.Call(_, arguments) => arguments.zipWithIndex.map { (argument, index) =>
        BeChildRole.FunctionParameter(index) -> argument.expression
      }
      case Node.If(condition, positive, negative) =>
        Vector(BeChildRole.ConditionInControlStructure -> condition.expression, BeChildRole.BodySequence(0) -> positive) ++
          negative.toVector.map(BeChildRole.BodySequence(1) -> _)
      case Node.While(condition, body) =>
        Vector(BeChildRole.ConditionInControlStructure -> condition.expression, BeChildRole.BodySequence(0) -> body)
      case Node.For(init, condition, update, body) =>
        Vector(BeChildRole.BodySequence(0) -> init) ++ condition.toVector.map(value =>
          BeChildRole.ConditionInControlStructure -> value.expression) ++
          Vector(BeChildRole.BodySequence(1) -> body, BeChildRole.BodySequence(2) -> update)
    }
  }

  final class Block private[JavaTurtleVmPrograms](val statements: Vector[Statement]) extends Element {
    protected def children: Vector[(BeChildRole, BeExpression)] = statements.zipWithIndex.map { (statement, index) =>
      BeChildRole.ExpressionInSequence(index) -> statement
    }
  }

  final class Method private[JavaTurtleVmPrograms](val binding: V.MethodBinding, val body: Block) extends Element {
    protected def children: Vector[(BeChildRole, BeExpression)] =
      binding.parameters.zipWithIndex.flatMap { (parameter, index) =>
        parameter.definition.map(BeChildRole.FunctionParameter(index) -> _)
      } :+ (BeChildRole.BodySequence(0) -> body)
  }

  final class Root private[JavaTurtleVmPrograms](val methods: Vector[Method], val entryPoint: Method) extends Element {
    protected def children: Vector[(BeChildRole, BeExpression)] = methods.zipWithIndex.map { (method, index) =>
      BeChildRole.MethodInClass(index) -> method
    }
  }

  final class Program private[JavaTurtleVmPrograms](val bindings: V.Bindings, val root: Root) {
    val vm: BeProgram = BeProgram(root)
    private[vm] val resolvedMethods: Vector[R.Method] = root.methods.map { method =>
      R.Method(method.binding.id, method.binding.originalName, method.binding.parameters.map(_.variable), restore(method.body))
    }
  }

  // Only a checked source can supply statements; arbitrary blocks would bypass Java scope checks.
  def adapt(source: R.ResolvedSource): Either[Diagnostic, Program] = {
    val bindings = V.bind(source)
    val compiler = new Compiler(bindings)
    traverse(source.methods) { method =>
      methodBinding(bindings, method.id).flatMap(binding => compiler.block(method.body).map(body => new Method(binding, body)))
    }.map { methods =>
      new Program(bindings, new Root(methods, methods.find(_.binding.id == source.entryPoint).get))
    }
  }

  private type Result[A] = Either[Diagnostic, A]

  private def traverse[A, B](items: Vector[A])(compile: A => Result[B]): Result[Vector[B]] =
    items.foldLeft[Result[Vector[B]]](Right(Vector.empty)) { (result, item) =>
      for { previous <- result; value <- compile(item) } yield previous :+ value
    }

  private def methodBinding(bindings: V.Bindings, id: R.MethodId): Result[V.MethodBinding] =
    bindings.method(id).toRight(Diagnostic(Problem.UnknownMethod, "This method is not part of the Java program.", None))

  private class Compiler(bindings: V.Bindings) {
    private def expression(value: R.Expression): Result[X.BoundExpression] = X.adapt(bindings, value)

    private def optional(value: Option[R.Expression]): Result[Option[X.BoundExpression]] =
      value.fold[Result[Option[X.BoundExpression]]](Right(None))(inner => expression(inner).map(Some(_)))

    def block(value: R.Block): Result[Block] = traverse(value.statements)(statement).map(new Block(_))

    private def statement(value: R.Statement): Result[Statement] = {
      val node: Result[Node] = value match {
        case R.Empty => Right(Node.Empty)
        case R.Return => Right(Node.Return)
        case R.Declare(variable, initial) =>
          for { definition <- bindings.definition(variable); compiled <- optional(initial) }
            yield Node.Declare(variable, definition, compiled)
        case R.Assign(variable, operator, value) =>
          for { definition <- bindings.definition(variable); compiled <- expression(value) }
            yield Node.Assign(variable, definition, operator, compiled)
        case R.Call(target, arguments) =>
          val boundTarget: Result[CallTarget] = target match {
            case R.CallTarget.Helper(id) => methodBinding(bindings, id).map(CallTarget.Helper(_))
            case R.CallTarget.Turtle(command) => Right(CallTarget.Turtle(command))
          }
          for { bound <- boundTarget; compiled <- traverse(arguments)(expression) } yield Node.Call(bound, compiled)
        case R.If(condition, positive, negative) =>
          for {
            test <- expression(condition)
            yes <- block(positive)
            no <- negative.fold[Result[Option[Block]]](Right(None))(body => block(body).map(Some(_)))
          } yield Node.If(test, yes, no)
        case R.While(condition, body) =>
          for { test <- expression(condition); compiled <- block(body) } yield Node.While(test, compiled)
        case R.For(init, condition, update, body) =>
          for { initial <- block(init); test <- optional(condition); last <- block(update); repeated <- block(body) }
            yield Node.For(initial, test, last, repeated)
      }
      node.map(new Statement(_))
    }
  }

  private def restore(block: Block): R.Block = R.Block(block.statements.map { statement => statement.node match {
    case Node.Empty => R.Empty
    case Node.Return => R.Return
    case Node.Declare(variable, _, initial) => R.Declare(variable, initial.map(_.resolved))
    case Node.Assign(variable, _, operator, value) => R.Assign(variable, operator, value.resolved)
    case Node.Call(target, arguments) =>
      val resolved = target match {
        case CallTarget.Helper(method) => R.CallTarget.Helper(method.id)
        case CallTarget.Turtle(command) => R.CallTarget.Turtle(command)
      }
      R.Call(resolved, arguments.map(_.resolved))
    case Node.If(condition, positive, negative) => R.If(condition.resolved, restore(positive), negative.map(restore))
    case Node.While(condition, body) => R.While(condition.resolved, restore(body))
    case Node.For(init, condition, update, body) => R.For(restore(init), condition.map(_.resolved), restore(update), restore(body))
  } })
}
