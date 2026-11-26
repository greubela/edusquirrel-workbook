package it.evadid.vm.analysis

import it.evadid.vm.parsing.python.clean.model.PyAST
import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.parsing.source.PythonSource

object PythonSourceWarnings {
  def of(source: PythonSource, paths: Set[Seq[String]]): Seq[SourceWarning] = {
    val uses = source.syntax.toOption.toSeq.flatMap { syntax =>
      def children(node: PyAST) = node.getChildren().collect { case child: PyAST => child }
      def path(node: PyAST): Option[Seq[String]] = node match {
        case target: PyTarget => Some(target.locationString :+ target.identifier)
        case PyAttributeAccess(receiver, name) => path(receiver).map(_ :+ name)
        case _ => None
      }
      def visit(node: PyAST): Seq[SourceWarning] = {
        val use = node match {
          case PyFunctionCall(callee, arguments) => Some((callee, arguments))
          case PyCallExpression(callee, arguments) => Some((callee, arguments))
          case PySimpleAssignment(target, value) => Some((target, value.toSeq))
          case access: PyAttributeAccess => Some((access, Seq.empty[PyAST]))
          case _ => None
        }
        val unknown = for {
          (callee, rest) <- use
          names <- path(callee) if paths(names)
          span <- syntax.sourceMap.rangeOf(callee)
        } yield ParsedSourceWarnings.at(source.source, SourceWarning.Kind.UnknownDependency, names.mkString("."), span) +: rest.flatMap(visit)
        unknown.getOrElse(children(node).flatMap(visit))
      }
      visit(syntax.program)
    }
    (uses ++ ParsedSourceWarnings.of(source.source, source.neutral)).distinct
  }
}
