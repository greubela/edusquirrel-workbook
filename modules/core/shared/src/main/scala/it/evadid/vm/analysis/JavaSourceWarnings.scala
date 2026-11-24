package it.evadid.vm.analysis

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.parsing.java.clean.{JavaParser, JavaSourceMap}
import it.evadid.vm.parsing.java.clean.model.JavaAST
import it.evadid.vm.parsing.java.clean.model.JavaAST.*

/** Locates uses of unknown dependencies in Java source with the ranges of the located Java parser; Python's
  * parser does not locate expressions yet. Verbatim library definitions are masked first, as for conversion.
  */
object JavaSourceWarnings {
  private val parser = new JavaParser(retainParentheses = false, retainComments = true)

  /** The uses of the dotted `paths` (unknown calls and attributes) in `source`. */
  def unknownUses(source: String, paths: Set[Seq[String]]): Seq[SourceWarning] =
    if paths.isEmpty then Nil
    else parser.parseWithLocations(KnownDependencies.maskDefinitions(source, Java)).toOption.toSeq.flatMap { located =>
      def warning(path: Seq[String], range: JavaSourceMap.SourceRange) = for {
        start <- located.position(range.start)
        end <- located.position(range.end)
      } yield SourceWarning(SourceWarning.Kind.UnknownDependency, path.mkString("."), start.line, start.column, end.line, end.column)
      // A use is marked as a whole: its receivers (`System.out2` in `System.out2.println`) are not visited.
      def visit(node: JavaSourceMap.Node[JavaAST]): Seq[SourceWarning] = useOf(node) match {
        case Some((path, range, rest)) if paths(path) => warning(path, range).toSeq ++ rest.flatMap(visit)
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
