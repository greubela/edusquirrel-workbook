package it.evadid.core.datastructures.vectorShapes.renderer

import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.parsing.python.clean.PythonAstParserSimple
import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}

import scala.collection.mutable
import scala.util.control.NonFatal

/** A line-addressed graph for an editor gutter. Source is never rewritten.
  * The Python conversion decides support; the fastparse syntax tree supplies statements and
  * [[SourceStructure.pythonCodeLines]] their physical rows.
  */
object PythonFlowchartGutter {
  case class Node(line: Int, kind: String, depth: Int, label: String) derives upickle.default.ReadWriter
  case class Edge(from: Int, to: Int, kind: String = "next") derives upickle.default.ReadWriter
  case class FlowLine(line: Int, kind: String) derives upickle.default.ReadWriter
  case class Chart(nodes: List[Node], edges: List[Edge], entries: List[Int],
                   flow: List[List[FlowLine]] = Nil) derives upickle.default.ReadWriter
  val empty: Chart = Chart(Nil, Nil, Nil)

  private case class Positioned(line: Int, text: String, depth: Int,
                                body: List[Positioned] = Nil,
                                otherwise: List[Positioned] = Nil,
                                kind: String = "process", elseLine: Option[Int] = None) derives upickle.default.ReadWriter

  def fromPython(source: String): Chart =
    parseChart(source).getOrElse {
      if (hasUnsupportedStatement(source)) empty
      else recoverIncompleteTail(source).getOrElse(empty)
    }

  /** Parses exactly `source`. `None` means the program is incomplete or unsupported. */
  private def parseChart(source: String): Option[Chart] = try {
    val expression = PythonToExpression.program(source).fullProgram
    val expressions = expression.recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values
      .collect { case BeExpressionReference(_, value) => value }
    if (expressions.exists {
      case _: BeExpressionUnparsable | _: BeExpressionUnsupported => true
      case _ => false
    }) None
    else {
      val program = PythonAstParserSimple.parse(source).fold(error => throw error, identity)
      // Statements consume code rows in order; comment-only and blank rows stay without nodes.
      val rows = SourceStructure.pythonCodeLines(normalized(source))
      def row(cursor: Int): SourceStructure.CodeLine = rows.lift(cursor).getOrElse(throw IllegalArgumentException("Cannot align statement to source"))
      def startsWith(cursor: Int, keyword: String): Boolean = rows.lift(cursor).exists(next => keywordOf(next.code) == keyword)
      def body(statements: Seq[PyStatement], depth: Int, cursor: Int): (List[Positioned], Int) =
        statements.foldLeft((List.empty[Positioned], cursor)) { case ((done, current), statement) =>
          val (items, next) = position(statement, depth, current)
          (done ++ items, next)
        }
      def position(statement: PyStatement, depth: Int, cursor: Int): (List[Positioned], Int) = statement match {
        case PyEmptyStatement | _: PyCommentStatement => (Nil, cursor)
        case PyBreakStatement | PyContinueStatement | _: PyTryStatement =>
          throw IllegalArgumentException("Unsupported control-flow statement")
        case PyIfStatement(_, yes, no) =>
          val (thenBody, afterThen) = body(yes.statements, depth + 1, cursor + 1)
          // An elif is the nested decision itself; only a physical else consumes a row.
          val (elseLine, otherwise, next) =
            if startsWith(afterThen, "elif") then {
              val (nested, after) = body(no.statements, depth, afterThen)
              (None, nested, after)
            } else if startsWith(afterThen, "else") then {
              val (elseBody, after) = body(no.statements, depth + 1, afterThen + 1)
              (Some(row(afterThen).row), elseBody, after)
            } else (None, Nil, afterThen)
          (List(Positioned(row(cursor).row, row(cursor).code, depth, thenBody, otherwise, "decision", elseLine)), next)
        case loop @ (_: PyWhileStatement | _: PyForStatement) =>
          val statements = loop match {
            case value: PyWhileStatement => value.bodyBlock.statements
            case value: PyForStatement => value.bodyBlock.statements
          }
          val (items, next) = body(statements, depth + 1, cursor + 1)
          (List(Positioned(row(cursor).row, row(cursor).code, depth, items, kind = "loop")), next)
        case definition: PyFunctionDef =>
          val header = cursor + definition.decorators.size
          val (items, next) = body(definition.block.statements, depth + 1, header + 1)
          (List(Positioned(row(header).row, row(header).code, depth, items, kind = "function")), next)
        case definition: PyClassDef =>
          val (items, next) = body(definition.block.statements, depth + 1, cursor + 1)
          (List(Positioned(row(cursor).row, row(cursor).code, depth, items, kind = "class")), next)
        case _ => (List(Positioned(row(cursor).row, row(cursor).code, depth)), cursor + 1)
      }
      val (positioned, consumed) = body(program.statements.map(_.statement), 0, 0)
      require(consumed == rows.size, "Cannot align all source statements")
      Some(build(positioned))
    }
  } catch {
    case NonFatal(_) => None
  }

  private def normalized(source: String): String = PythonAstParserSimple.normalizeLineEndingsAndDetab(source)

  private val CompoundKeywords = Set("for", "while", "if", "elif", "else", "def", "class")
  private val UnsupportedKeywords = Set("break", "continue", "try", "except", "finally")
  private val MaxDroppedLines = 3

  /** `break`, `continue` and `try`/`except` stay empty instead of hiding behind a prefix. */
  private def hasUnsupportedStatement(source: String): Boolean =
    SourceStructure.pythonCodeLines(normalized(source)).exists(row => UnsupportedKeywords.contains(keywordOf(row.code)))

  /**
   * Keeps a finished prefix while the tail is still being typed.
   * Tries the last few code lines from the end; a compound header gets `pass`.
   * Line numbers of the kept source stay put because the edit is only at the tail.
   */
  private def recoverIncompleteTail(source: String): Option[Chart] = {
    val text = normalized(source)
    val lines = text.split("\n", -1).toVector
    val codeRows = SourceStructure.pythonCodeLines(text).map(_.row - 1).reverse.take(MaxDroppedLines)
    codeRows.iterator.flatMap { last =>
      val kept = lines.take(last + 1)
      val candidate =
        if (isCompoundHeader(codeOf(kept(last)))) withPass(kept, last)
        else kept.mkString("\n")
      // parseChart already rejected the original source; skip that candidate.
      Option.when(candidate != text)(candidate).flatMap(parseChart)
    }.nextOption()
  }

  private def withPass(lines: Vector[String], headerIndex: Int): String = {
    val indent = lines(headerIndex).takeWhile(_ == ' ')
    val passLine = indent + "    pass"
    val (before, after) = lines.splitAt(headerIndex + 1)
    (before :+ passLine :++ after).mkString("\n")
  }

  private def isCompoundHeader(code: String): Boolean =
    CompoundKeywords.contains(keywordOf(code)) && code.endsWith(":")

  private def keywordOf(code: String): String =
    code.trim.takeWhile(ch => ch.isLetterOrDigit || ch == '_')

  /** The line's code without its comment. */
  private def codeOf(line: String): String = SourceStructure.pythonCodeLines(line).headOption.map(_.code).getOrElse("")

  private def build(statements: List[Positioned]): Chart = {
    val nodes = mutable.ListBuffer.empty[Node]
    val edges = mutable.ListBuffer.empty[Edge]
    val entries = mutable.ListBuffer.empty[Int]
    def edge(from: Int, to: Option[Int], kind: String = "next"): Unit =
      to match {
        case Some(target) => edges += Edge(from, target, kind)
        case None if kind == "yes" || kind == "no" => edges += Edge(from, from, kind + "-end")
        case _ => ()
      }
    def append(items: List[Positioned], next: Option[Int]): Option[Int] =
      items.foldRight(next) { (item, continuation) =>
        if (item.text.startsWith("#") || item.text == "pass" ||
            Set("from typing import Any", "from datetime import date").contains(item.text)) continuation
        else {
          val kind = if (item.text == "return" || item.text.startsWith("return ")) "return" else item.kind
          nodes += Node(item.line, kind, item.depth, item.text)
          kind match {
            case "decision" =>
              edge(item.line, append(item.body, continuation), "yes")
              edge(item.line, append(item.otherwise, continuation), "no")
            case "loop" =>
              edge(item.line, append(item.body, Some(item.line)), "yes")
              edge(item.line, continuation, "no")
            case "function" | "class" =>
              // A declaration falls through; its body is a separate graph,
              // never an execution path from the declaration or a call site.
              append(item.body, None).foreach(entries += _)
              edge(item.line, continuation)
            case "return" => ()
            case _ => edge(item.line, continuation)
          }
          Some(item.line)
        }
      }
    append(statements, None).foreach(entries += _)
    // Drop statements after unconditional returns from each execution graph.
    val outgoing = edges.toList.groupBy(_.from)
    val reachable = mutable.Set.empty[Int]
    val pending = mutable.Queue.from(entries)
    while (pending.nonEmpty) {
      val line = pending.dequeue()
      if (reachable.add(line)) outgoing.getOrElse(line, Nil).foreach(edge => pending.enqueue(edge.to))
    }
    val visible = nodes.filter(n => reachable(n.line)).sortBy(_.line).toList
    Chart(visible, edges.filter(e => reachable(e.from)).toList, entries.toList,
      flowPlan(statements, visible.map(_.line).toSet))
  }
  /** Structural rows consumed by the existing nested-block flow renderer.
    * Closing rows share a physical source line; they never insert editor lines.
    */
  private def flowPlan(statements: List[Positioned], visible: Set[Int]): List[List[FlowLine]] = {
    def rows(items: List[Positioned]): List[FlowLine] = items.filter(item => visible(item.line)).flatMap { item =>
      val body = rows(item.body)
      val otherwise = rows(item.otherwise)
      val end = (otherwise.lastOption orElse body.lastOption).map(_.line).getOrElse(item.line)
      item.kind match {
        case "decision" =>
          FlowLine(item.line, "if") :: body ::: List(FlowLine(item.elseLine.getOrElse(
            otherwise.headOption.orElse(body.lastOption).map(_.line).getOrElse(item.line)), "else")) :::
            otherwise ::: List(FlowLine(end, "endif"))
        case "loop" => FlowLine(item.line, "loop") :: body ::: List(FlowLine(end, "endloop"))
        case _ => List(FlowLine(item.line, item.kind))
      }
    }
    def group(items: List[Positioned]): List[FlowLine] = rows(items) match {
      case Nil => Nil
      case result => FlowLine(result.head.line, "start") :: result ::: List(FlowLine(result.last.line, "stop"))
    }
    def declarations(items: List[Positioned]): List[List[FlowLine]] = items.flatMap { item =>
      val own = if Set("function", "class")(item.kind) then List(group(item.body)).filter(_.nonEmpty) else Nil
      own ::: declarations(item.body) ::: declarations(item.otherwise)
    }
    List(group(statements)).filter(_.nonEmpty) ::: declarations(statements)
  }

}
