package it.evadid.vm.analysis

import it.evadid.vm.parsing.java.clean.JavaSourceMap
import it.evadid.vm.parsing.source.JavaSource
import it.evadid.vm.parsing.java.clean.model.JavaAST
import it.evadid.vm.parsing.java.clean.model.JavaAST.*

/** Locates uses of unknown dependencies and unreadable statements with the ranges of the syntax tree and adapter (steps 2–3 of
  * [[it.evadid.vm.parsing.source.SourceParsing]]).
  */
object JavaSourceWarnings {
  /** The uses of the dotted `paths` (unknown calls and attributes) and the unreadable statements of `source`. */
  def of(source: JavaSource, paths: Set[Seq[String]]): Seq[SourceWarning] = {
    val syntaxWarnings = source.syntax.toOption.toSeq.flatMap { syntax =>
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
    val adapterWarnings = source.neutral.fold(error => error.error.span.toSeq.map(span =>
      ParsedSourceWarnings.at(source.source, SourceWarning.Kind.Unsupported,
        span.text(source.source).trim.linesIterator.nextOption().getOrElse(""), span)), ParsedSourceWarnings.of(source.source, _))
    val failures = source.syntax.swap.toOption.toSeq.map { failure =>
      ParsedSourceWarnings.at(source.source, SourceWarning.Kind.Unreadable, failure.message,
        it.evadid.vm.parsing.source.SourceLines(source.source).span(failure.offset, (failure.offset + 1).min(source.source.length)))
    }
    (syntaxWarnings ++ adapterWarnings ++ failures).distinct
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
