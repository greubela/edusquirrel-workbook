package it.evadid.vm.compilation.java.turtle

import it.evadid.vm.compilation.java.turtle.{JavaTurtleSymbolResolver as R, JavaTurtleExecutionBindings as V, JavaTurtleExpressionCompiler as X}
import it.evadid.vm.compilation.java.turtle.JavaTurtleSourceParser.{Diagnostic, Problem}
import it.evadid.vm.compilation.java.turtle.JavaTurtleProgram.*

/** Compiles the supported Java turtle subset for the interpreter. */
object JavaTurtleCompiler {
  def compile(source: String): Either[Diagnostic, JavaTurtleProgram] =
    for {
      parsed <- JavaTurtleSourceParser.parse(source)
      structure <- JavaTurtleStructureValidator.check(parsed)
      checked <- JavaTurtleSemanticValidator.check(structure)
      resolved <- R.resolve(checked)
      program <- compileResolved(resolved)
    } yield program

  // Only a checked source can supply statements; arbitrary blocks would bypass Java scope checks.
  def compileResolved(source: R.ResolvedSource): Either[Diagnostic, JavaTurtleProgram] = {
    val bindings = V.bind(source)
    val compiler = new Compiler(bindings)
    traverse(source.methods) { method =>
      methodBinding(bindings, method.id).flatMap(binding => compiler.block(method.body).map(body => new Method(binding, body)))
    }.map { methods =>
      new JavaTurtleProgram(bindings, new Root(bindings, methods, methods.find(_.binding.id == source.entryPoint).get))
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

}
