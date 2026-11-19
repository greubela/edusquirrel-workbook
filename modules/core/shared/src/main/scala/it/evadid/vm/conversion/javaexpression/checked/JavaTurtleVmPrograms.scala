package it.evadid.vm.conversion.javaexpression.checked

import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.conversion.javaexpression.checked.{JavaTurtleResolution as R, JavaTurtleVmBindings as V, JavaTurtleVmExpressions as X}
import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleSource.{Diagnostic, Problem}


object JavaTurtleVmPrograms {
  enum CallTarget {
    case Helper(method: V.MethodBinding)
    case Turtle(command: R.TurtleCommand)
  }

  enum Node {
    case Empty, Return
    case Declare(variable: R.Variable, definition: BeDefineVariable, initial: Option[X.BoundExpression])
    case Assign(variable: R.Variable, definition: BeDefineVariable, operator: R.AssignmentOperator, value: X.BoundExpression)
    case Evaluate(value: X.BoundExpression)
    case Call(target: CallTarget, arguments: Vector[X.BoundExpression])
    case If(condition: X.BoundExpression, positive: Block, negative: Option[Block])
    case While(condition: X.BoundExpression, body: Block)
    case For(init: Block, condition: Option[X.BoundExpression], update: Block, body: Block)
  }

  // Checked Java runtime nodes are deliberately separate from the language-neutral expression tree.
  final class Statement private[JavaTurtleVmPrograms](val node: Node)
  final class Block private[JavaTurtleVmPrograms](val statements: Vector[Statement])
  final class Method private[JavaTurtleVmPrograms](val binding: V.MethodBinding, val body: Block)
  final class Root private[JavaTurtleVmPrograms](val bindings: V.Bindings, val methods: Vector[Method], val entryPoint: Method)

  final class Program private[JavaTurtleVmPrograms](val bindings: V.Bindings, val root: Root) {
    private[vm] val resolvedMethods: Vector[R.Method] = root.methods.map { method =>
      R.Method(method.binding.id, method.binding.originalName, method.binding.parameters.map(_.variable), restore(method.body))
    }
  }

  def compile(source: String): Either[Diagnostic, Program] =
    for {
      parsed <- JavaTurtleSource.parse(source)
      structure <- JavaTurtleStructure.check(parsed)
      checked <- JavaTurtleSemantics.check(structure)
      resolved <- R.resolve(checked)
      program <- adapt(resolved)
    } yield program

  def fromRoot(root: Root): Program = new Program(root.bindings, root)

  // Only a checked source can supply statements; arbitrary blocks would bypass Java scope checks.
  def adapt(source: R.ResolvedSource): Either[Diagnostic, Program] = {
    val bindings = V.bind(source)
    val compiler = new Compiler(bindings)
    traverse(source.methods) { method =>
      methodBinding(bindings, method.id).flatMap(binding => compiler.block(method.body).map(body => new Method(binding, body)))
    }.map { methods =>
      new Program(bindings, new Root(bindings, methods, methods.find(_.binding.id == source.entryPoint).get))
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
        case R.Evaluate(value) => expression(value).map(Node.Evaluate(_))
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
    case Node.Evaluate(value) => R.Evaluate(value.resolved)
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
