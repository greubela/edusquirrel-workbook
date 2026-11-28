package it.evadid.vm.simulation.turtle

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.core.util.io.Serializer
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.conversion.snapexpression.{SnapControlFlow, SnapExpressionSupport}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.modules.BeModules
import it.evadid.vm.types.{BeDataValueLiteral, BeScope}
import it.evadid.workbook.interactions.programming.state.snap.SnapTurtleCatalog
import scala.annotation.tailrec

private[simulation] object TurtleCommandSites {
  /** Preorder traversal follows structural children, never a call's definition reference. */
  def nodes(root: BeExpression): List[(Vector[Int], BeExpression)] = {
    @tailrec def visit(pending: List[(Vector[Int], BeExpression, BeScope)], result: List[(Vector[Int], BeExpression)]): List[(Vector[Int], BeExpression)] =
      pending match {
        case Nil => result.reverse
        case (path, node, scope) :: rest =>
          val children = node.structureInfo.getChildrenAsReference(scope).zipWithIndex.map {
            case (BeExpressionReference(info, child), index) => (path :+ index, child, info.myScope)
          }.toList
          visit(children ::: rest, (path -> node) :: result)
      }
    visit(List((Vector.empty, root, BeScope.GlobalScope())), Nil)
  }

  def apply(root: BeExpression): List[TurtleCommandSite[Double]] = {
    val all = nodes(root)
    val helpers = all.collect { case (_, definition: BeDefineFunction) => SnapExpressionSupport.pythonNameOf(definition) }.toSet
    all.flatMap {
      case (path, call: BeFunctionCall) if !call.isOperator && BeModules.qualifiedName(call.funcDef).isEmpty &&
          (TurtleCommand.isTurtleMethod(call.funcDef) || !helpers(SnapExpressionSupport.pythonName(call))) =>
        val python = SnapExpressionSupport.pythonName(call)
        val name = SnapTurtleCatalog.turtleCommandByPythonName.getOrElse(python, python)
        Some(site(path, BeEntityName.fromCodeString(name), call.arguments.toVector))
      case _ => None
    }
  }

  private def site(path: Vector[Int], name: BeEntityName, arguments: Vector[BeExpression]): TurtleCommandSite[Double] = {
    val literals = arguments.map(literal)
    val command = Option.when(literals.forall(_.nonEmpty)) {
      val values = literals.flatten
      TurtleCommand.fromCall(name, values.flatMap(_.left.toOption), values.flatMap(_.toOption))
    }
    TurtleCommandSite(path, TurtleCommand.canonicalName(name), arguments, command)
  }

  /** Only literal arguments are classified here; arithmetic and variables remain symbolic. */
  private def literal(expression: BeExpression): Option[Either[Double, String]] = expression match {
    case BeUseValue(BeDataValueLiteral(value), _) =>
      value.trim.toDoubleOption.filter(_.isFinite).map(number => Left(number)).orElse {
        Some(Right(Serializer.stringLiteralIO(parseEverything = true).deserialize(value)))
      }
    case call: BeFunctionCall if call.isOperator =>
      call.arguments match {
        case argument :: Nil =>
          val symbol = call.funcDef.functionTypeInfo.displayName.universalInterpretation()
          literal(argument).collect {
            case Left(number) if symbol == "-" => Left(-number)
            case Left(number) if symbol == "+" => Left(number)
          }
        case _ => None
      }
    case _ => None
  }
}
