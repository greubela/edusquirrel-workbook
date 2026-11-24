package it.evadid.core.datastructures.vectorShapes.renderer

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.parsing.java.clean.{JavaParser, JavaSourceMap}
import it.evadid.vm.parsing.java.clean.model.JavaAST
import it.evadid.vm.parsing.java.clean.model.JavaAST.*

import scala.util.control.NonFatal

/** Places Java statements on source lines for a [[FlowchartGutter]] chart, with the ranges of the located Java
  * parser. Each method is its own graph. The chart stays empty unless it describes the source exactly: the
  * source parses, has no unsupported statements (try, break, continue, unparsable syntax) and no two charted
  * statements share a line. Verbatim library definitions are masked and not charted.
  */
object JavaFlowchartGutter {
  import FlowchartGutter.{Chart, Positioned, build, empty}

  private val parser = new JavaParser(retainParentheses = false, retainComments = true)
  private val wrapperHeader = "class Fragment {\n"

  /** A chart of a program of classes, of one class, or of class members on their own (one method). */
  def fromJava(source: String): Chart = chart(source).getOrElse(empty)

  private final class Unsupported extends RuntimeException

  private def chart(source: String): Option[Chart] = try {
    val masked = KnownDependencies.maskDefinitions(source, Java)
    // Members without their class are charted inside a wrapper class, one line above the source.
    val wrapped = SourceStructure.javaClasses(masked).isEmpty
    val located = parser.parseWithLocations(if wrapped then wrapperHeader + masked + "\n}" else masked).toOption.get
    val positioned = positions(located)
    val statements = if wrapped then positioned.flatMap(_.body).map(unwrapped) else positioned
    def lines(items: List[Positioned]): List[Int] = items.flatMap(item => item.line :: lines(item.body) ::: lines(item.otherwise))
    val all = lines(statements)
    Option.when(all.distinct.size == all.size)(build(statements))
  } catch {
    case NonFatal(_) => None
  }

  private def positions(located: JavaSourceMap): List[Positioned] = {
    def line(node: JavaSourceMap.Node[JavaAST]) = located.position(node.range.start).get.line
    def text(node: JavaSourceMap.Node[JavaAST]) = located.source.linesIterator.drop(line(node) - 1).nextOption().getOrElse("").trim
    def block(node: JavaSourceMap.Node[JavaAST]) = node.children.lastOption.filter(_.value.isInstanceOf[JavaExecutionBlock])
      .getOrElse(throw Unsupported())
    def items(nodes: Seq[JavaSourceMap.Node[JavaAST]], depth: Int): List[Positioned] = nodes.toList.flatMap(position(_, depth))
    def position(node: JavaSourceMap.Node[JavaAST], depth: Int): List[Positioned] = node.value match {
      case _: JavaProgram | _: StatementWithLineNumber | _: JavaExecutionBlock => items(node.children, depth)
      case _: JavaCommentStatement | JavaEmptyStatement | _: JavaImportStatement | _: JavaPackageStatement => Nil
      case _: JavaUnparsableStatement | JavaBreakStatement | JavaContinueStatement | _: JavaTryStatement => throw Unsupported()
      case _: JavaClassDef => List(Positioned(line(node), text(node), depth, items(block(node).children, depth + 1), kind = "class"))
      case _: JavaMethodDef => List(Positioned(line(node), text(node), depth, items(block(node).children, depth + 1), kind = "function"))
      case JavaIfStatement(_, _, elseBlock) =>
        val thenBody = items(node.children(1).children, depth + 1)
        val (elseLine, otherwise) = elseBlock.fold((Option.empty[Int], List.empty[Positioned])) { _ =>
          val branch = node.children(2)
          branch.children match {
            // `else if` is the nested decision itself, like Python's elif.
            case Seq(nested) if nested.value.isInstanceOf[JavaIfStatement] && nested.range.start == branch.range.start =>
              (None, position(nested, depth))
            case statements => (Some(line(branch)), items(statements, depth + 1))
          }
        }
        List(Positioned(line(node), text(node), depth, thenBody, otherwise, "decision", elseLine))
      case _: JavaWhileStatement | _: JavaForStatement =>
        List(Positioned(line(node), text(node), depth, items(block(node).children, depth + 1), kind = "loop"))
      case _ => List(Positioned(line(node), text(node), depth))
    }
    position(located.root, 0)
  }

  /** A member of the wrapper class on the line and depth it has in the source without the wrapper. */
  private def unwrapped(item: Positioned): Positioned =
    item.copy(line = item.line - 1, depth = item.depth - 1, body = item.body.map(unwrapped), otherwise = item.otherwise.map(unwrapped),
      elseLine = item.elseLine.map(_ - 1))
}
