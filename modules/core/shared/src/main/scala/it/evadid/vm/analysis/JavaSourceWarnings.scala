package it.evadid.vm.analysis

import it.evadid.vm.parsing.java.clean.JavaSourceMap
import it.evadid.vm.parsing.source.JavaSource
import it.evadid.vm.parsing.java.clean.model.JavaAST
import it.evadid.vm.parsing.java.clean.model.JavaAST.*

/** Locates uses of unknown dependencies and unreadable statements with the ranges of the Java syntax tree (step 2 of
  * [[it.evadid.vm.parsing.source.SourceParsing]]); Python's syntax tree does not locate expressions yet.
  */
object JavaSourceWarnings {
  /** The uses of the dotted `paths` (unknown calls and attributes) and the unreadable statements of `source`. */
  def of(source: JavaSource, paths: Set[Seq[String]]): Seq[SourceWarning] =
    source.syntax.toOption.toSeq.flatMap { syntax =>
      val located = syntax.sourceMap
      def warning(kind: SourceWarning.Kind, subject: String, range: JavaSourceMap.SourceRange) = for {
        start <- located.position(range.start)
        end <- located.position(range.end)
      } yield SourceWarning(kind, subject, start.line, start.column, end.line, end.column)
      // A use is marked as a whole: its receivers (`System.out2` in `System.out2.println`) are not visited.
      def visit(node: JavaSourceMap.Node[JavaAST]): Seq[SourceWarning] = (node.value, useOf(node)) match {
        case (JavaUnparsableStatement(text), _) =>
          warning(SourceWarning.Kind.Unreadable, text.trim.linesIterator.nextOption().getOrElse(""), node.range).toSeq
        case (_, Some((path, range, rest))) if paths(path) =>
          warning(SourceWarning.Kind.UnknownDependency, path.mkString("."), range).toSeq ++ rest.flatMap(visit)
        case _ => node.children.flatMap(visit)
      }
      visit(located.root)
    }

  /** The used path of a call (its callee), an assignment (its target) or an attribute read, with the rest to visit. */
  private def useOf(node: JavaSourceMap.Node[JavaAST]): Option[(Seq[String], JavaSourceMap.SourceRange, Seq[JavaSourceMap.Node[JavaAST]])] = {
    def first(used: JavaAST) = for {
      path <- pathOf(used)
      head <- node.children.headOption
    } yield (path, head.range, node.children.drop(1))
    node.value match {
      case JavaCallExpression(callee, _) => first(callee)
      case JavaFunctionCall(target, _) => first(target)
      case JavaAssignment(target, _) => first(target)
      case access: JavaAttributeAccess => pathOf(access).map(path => (path, node.range, Nil))
      case _ => None
    }
  }

  private def pathOf(expression: JavaAST): Option[Seq[String]] = expression match {
    case JavaTarget(name, location, None) => Some(location :+ name)
    case JavaAttributeAccess(receiver, name) => pathOf(receiver).map(_ :+ name)
    case _ => None
  }
}
