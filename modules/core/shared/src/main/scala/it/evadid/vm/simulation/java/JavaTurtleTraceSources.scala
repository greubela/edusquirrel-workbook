package it.evadid.vm.simulation.java

import it.evadid.vm.parsing.java.clean.JavaSourceMap.{Node, SourceRange}
import it.evadid.vm.parsing.java.clean.model.JavaAST
import it.evadid.vm.parsing.java.clean.model.JavaAST.*
import it.evadid.vm.parsing.java.clean.model.JavaType.*
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R}
import it.evadid.vm.simulation.java.JavaTurtleTrace.SourceNode

private[java] object JavaTurtleTraceSources {
  private type Syntax = Node[JavaAST]
  case class Entry(range: SourceRange, isReadExpression: Boolean = false,
      isCalculation: Boolean = false, isBranchCondition: Boolean = false)
  private type Entries = Vector[(SourceNode, Entry)]

  def build(source: R.ResolvedSource): Option[Map[SourceNode, Entry]] = {
    val root = source.typedSource.structure.parsedSource.sourceMap.root
    for {
      wrapper <- only(root.children)
      clazz <- only(wrapper.children)
      if clazz.value.isInstanceOf[JavaClassDef]
      body <- only(clazz.children)
      if body.value.isInstanceOf[JavaExecutionBlock]
      if body.children.size == source.methods.size
      entries <- collect(source.methods.zip(body.children)) { (method, syntax) => methodEntries(method, syntax) }
      indexed = entries.toMap
      if indexed.size == entries.size
    } yield indexed
  }

  private def only(nodes: Vector[Syntax]): Option[Syntax] = nodes match {
    case Vector(node) => Some(node)
    case _ => None
  }

  private def collect[A](values: Vector[A])(visit: A => Option[Entries]): Option[Entries] =
    values.foldLeft(Option(Vector.empty[(SourceNode, Entry)])) { (result, value) =>
      for { previous <- result; next <- visit(value) } yield previous ++ next
    }

  private def optional[A](value: Option[A], syntax: Option[Syntax])(
      visit: (A, Syntax) => Option[Entries]): Option[Entries] = (value, syntax) match {
    case (None, None) => Some(Vector.empty)
    case (Some(value), Some(syntax)) => visit(value, syntax)
    case _ => None
  }

  private def methodEntries(method: R.Method, syntax: Syntax): Option[Entries] = syntax.value match {
    case definition: JavaMethodDef if definition.name == method.name &&
        definition.parameters.size == method.parameters.size && syntax.children.size == method.parameters.size + 1 =>
      val node = SourceNode(method.id, Vector.empty)
      block(method.body, syntax.children.last, node.child("body")).map { body =>
        Vector(node.child("entry") -> Entry(syntax.range)) ++
          syntax.children.take(method.parameters.size).zipWithIndex.map { (parameter, index) =>
            node.child("parameter").child(index.toString) -> Entry(parameter.range)
          } ++ body
      }
    case _ => None
  }

  private def statements(values: Vector[R.Statement], syntax: Vector[Syntax], node: SourceNode): Option[Entries] =
    if values.size != syntax.size then None
    else collect(values.zip(syntax).zipWithIndex) { case ((value, syntax), index) =>
      statement(value, syntax, node.child(index.toString))
    }

  private def block(value: R.Block, syntax: Syntax, node: SourceNode): Option[Entries] = syntax.value match {
    case _: JavaExecutionBlock => statements(value.statements, syntax.children, node).map((node -> Entry(syntax.range)) +: _)
    case _ => None
  }

  private def statement(value: R.Statement, syntax: Syntax, node: SourceNode): Option[Entries] = {
    val children: Option[Entries] = (value, syntax.value) match {
      case (R.Empty, JavaEmptyStatement) | (R.Return, JavaReturnStatement(None)) => Some(Vector.empty)
      case (R.Declare(_, initial), _: JavaVariableDeclaration) if syntax.children.size == initial.size =>
        optional(initial, syntax.children.headOption)((value, syntax) => expression(value, syntax, node.child("initial")))
      case (R.Assign(_, _, value), _: JavaAssignment | _: JavaAugAssignment | _: JavaAssignmentExpression) =>
        syntax.children match {
          case Vector(target, assigned) => expression(value, assigned, node.child("value")).map {
            entries => (node.child("target") -> Entry(target.range)) +: entries
          }
          case _ => None
        }
      case (R.Evaluate(value), _: JavaUpdateExpression) => expression(value, syntax, node.child("expression"))
      case (R.Call(_, arguments), _: JavaFunctionCall | _: JavaCallExpression)
          if syntax.children.size == arguments.size + 1 =>
        collect(arguments.zip(syntax.children.tail).zipWithIndex) { case ((argument, syntax), index) =>
          expression(argument, syntax, node.child("argument").child(index.toString))
        }
      case (R.If(condition, yes, no), _: JavaIfStatement) if syntax.children.size == 2 + no.size =>
        for {
          test <- expression(condition, syntax.children(0), node.child("condition")).map(_.map { (at, entry) =>
            at -> entry.copy(isBranchCondition = at == node.child("condition"))
          })
          positive <- block(yes, syntax.children(1), node.child("then"))
          negative <- optional(no, syntax.children.lift(2))((value, syntax) => block(value, syntax, node.child("else")))
        } yield test ++ positive ++ negative
      case (R.While(condition, body), _: JavaWhileStatement) => syntax.children match {
        case Vector(test, repeated) =>
          for { condition <- expression(condition, test, node.child("condition")); body <- block(body, repeated, node.child("body")) }
            yield condition ++ body
        case _ => None
      }
      case (R.For(init, condition, update, body), loop: JavaForStatement)
          if init.statements.size == loop.init.size && condition.size == loop.condition.size &&
            update.statements.size == loop.update.size &&
            syntax.children.size == loop.init.size + loop.condition.size + loop.update.size + 1 =>
        val testIndex = loop.init.size
        val updateIndex = testIndex + loop.condition.size
        for {
          initial <- statements(init.statements, syntax.children.take(testIndex), node.child("init"))
          test <- optional(condition, Option.when(condition.nonEmpty)(syntax.children(testIndex)))(
            (value, syntax) => expression(value, syntax, node.child("condition")))
          last <- statements(update.statements, syntax.children.slice(updateIndex, updateIndex + loop.update.size), node.child("update"))
          repeated <- block(body, syntax.children.last, node.child("body"))
        } yield initial ++ test ++ last ++ repeated
      case _ => None
    }
    children.map((node -> Entry(syntax.range)) +: _)
  }

  private def expression(value: R.Expression, syntax: Syntax, node: SourceNode): Option[Entries] = {
    val children: Option[Entries] = (value, syntax.value) match {
      case (R.Widen(inner), _) => expression(inner, syntax, node.child("0"))
      case (R.IntLiteral(_), JavaLiteral(_, _: JAVA_INTEGER)) |
          (R.DoubleLiteral(_), JavaLiteral(_, _: JAVA_FLOAT)) |
          (R.BooleanLiteral(_), JavaLiteral(_, _: JAVA_BOOL)) |
          (_: R.Read, _: JavaTarget) | (_: R.Update, _: JavaUpdateExpression) => Some(Vector.empty)
      case (R.IntLiteral(Int.MinValue), JavaOperationUnary("-", JavaLiteral("2147483648", _: JAVA_INTEGER))) =>
        Some(Vector.empty)
      case (R.Group(inner), _: JavaParenthesizedExpression) =>
        only(syntax.children).flatMap(expression(inner, _, node.child("0")))
      case (R.Unary(_, inner), _: JavaOperationUnary) =>
        only(syntax.children).flatMap(expression(inner, _, node.child("0")))
      case (R.Binary(_, left, right), _: JavaOperationBinary) => operands(left, right, syntax, node)
      case (R.ShortCircuit(_, left, right), _: JavaOperationBinary) => operands(left, right, syntax, node)
      case _ => None
    }
    val calculation = value match {
      case _: R.Unary | _: R.Binary | _: R.ShortCircuit | _: R.Update => true
      case _ => false
    }
    children.map((node -> Entry(syntax.range, value.isInstanceOf[R.Read], calculation)) +: _)
  }

  private def operands(left: R.Expression, right: R.Expression, syntax: Syntax, node: SourceNode): Option[Entries] =
    syntax.children match {
      case Vector(a, b) =>
        for { left <- expression(left, a, node.child("0")); right <- expression(right, b, node.child("1")) } yield left ++ right
      case _ => None
    }
}
