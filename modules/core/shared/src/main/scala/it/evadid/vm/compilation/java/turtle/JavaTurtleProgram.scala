package it.evadid.vm.compilation.java.turtle

import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.compilation.java.turtle.{JavaTurtleSymbolResolver as R, JavaTurtleExecutionBindings as V, JavaTurtleExpressionCompiler as X}


object JavaTurtleProgram {
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
  final class Statement private[turtle](val node: Node)
  final class Block private[turtle](val statements: Vector[Statement])
  final class Method private[turtle](val binding: V.MethodBinding, val body: Block)
  final class Root private[turtle](val bindings: V.Bindings, val methods: Vector[Method], val entryPoint: Method)

  def fromRoot(root: Root): JavaTurtleProgram = new JavaTurtleProgram(root.bindings, root)

  private[turtle] def restore(block: Block): R.Block = R.Block(block.statements.map { statement => statement.node match {
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

final class JavaTurtleProgram private[turtle](val bindings: V.Bindings, val root: JavaTurtleProgram.Root) {
    private[vm] val resolvedMethods: Vector[R.Method] = root.methods.map { method =>
      R.Method(method.binding.id, method.binding.originalName, method.binding.parameters.map(_.variable), JavaTurtleProgram.restore(method.body))
    }
  }
