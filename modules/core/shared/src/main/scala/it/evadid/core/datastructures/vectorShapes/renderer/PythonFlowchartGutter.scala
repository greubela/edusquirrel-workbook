package it.evadid.core.datastructures.vectorShapes.renderer

import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.parsing.python.{PythonNormalizer, PythonParser}
import it.evadid.vm.parsing.python.normalization.PythonNormalizationModel.*
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}

import scala.collection.mutable
import scala.util.control.NonFatal

/** A line-addressed graph for an editor gutter. Source is never rewritten.
  * The existing BeExpression parser decides support; its normalization tree
  * supplies statement boundaries, including rewritten elif/augmented assignments.
  */
object PythonFlowchartGutter {
  case class Node(line: Int, kind: String, depth: Int, label: String) derives upickle.default.ReadWriter
  case class Edge(from: Int, to: Int, kind: String = "next") derives upickle.default.ReadWriter
  case class Chart(nodes: List[Node], edges: List[Edge], entries: List[Int]) derives upickle.default.ReadWriter
  val empty: Chart = Chart(Nil, Nil, Nil)

  private case class Positioned(line: Int, text: String, depth: Int,
                                body: List[Positioned] = Nil,
                                otherwise: List[Positioned] = Nil,
                                kind: String = "process") derives upickle.default.ReadWriter

  def fromPython(source: String): Chart =
    parseChart(source).getOrElse {
      if (hasUnsupportedStatement(source)) empty
      else recoverIncompleteTail(source).getOrElse(empty)
    }

  /** Parses exactly `source`. `None` means the program is incomplete or unsupported. */
  private def parseChart(source: String): Option[Chart] = try {
    val expression = PythonParser.parsePython(source)
    val expressions = expression.recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values
      .collect { case BeExpressionReference(_, value) => value }
    if (expressions.exists {
      case _: BeExpressionUnparsable | _: BeExpressionUnsupported => true
      case _ => false
    }) None
    else {
      val normalizer = PythonNormalizer.default
      // Use the normalizer's own tokenization, one physical line at a time.
      // Inline comments occupy the same row as their statement; blank rows stay
      // in the document but do not acquire executable nodes.
      val tokens = normalizer.normalizeLineEndings(source).split("\n", -1).toList.zipWithIndex.flatMap {
        case (line, index) => normalizer.extractRawLines(line).map(raw => (index + 1, raw.text))
      }.toVector
      var cursor = 0
      def consume(): Int = {
        require(cursor < tokens.size, "Cannot align normalized statement to source")
        val line = tokens(cursor)._1
        cursor += 1
        line
      }
      def position(statements: List[Statement], depth: Int): List[Positioned] = statements.map {
        case SimpleStatement(text) =>
          // The current parser treats these keywords as unresolved names,
          // rather than providing control-flow expressions for them.
          require(!Set("break", "continue").contains(text), "Unsupported control-flow keyword")
          Positioned(consume(), text, depth)
        case CompoundStatement(header, body) =>
          val line = consume()
          val kind = if (header.startsWith("while ") || header.startsWith("for ")) "loop"
            else if (header.startsWith("def ")) "function"
            else if (header.startsWith("class ")) "class"
            else "process"
          Positioned(line, header, depth, position(body, depth + 1), kind = kind)
        case IfStatement(condition, yes, no) =>
          require(yes.nonEmpty, "Missing conditional body")
          val line = consume()
          val thenBody = position(yes, depth + 1)
          // Normalization lowers elif to else + if. Only a physical else
          // consumes a token; an elif is consumed by the nested decision.
          val elseDepth = if (no.nonEmpty && cursor < tokens.size && tokens(cursor)._2 == "else:") {
            consume()
            depth + 1
          } else depth
          Positioned(line, condition, depth, thenBody, no.toList.flatMap(position(_, elseDepth)), "decision")
      }
      val positioned = position(normalizer.runPipeline(source).statementTree.statements, 0)
      require(cursor == tokens.size, "Cannot align all source statements")
      // A header without a body is stored as a plain statement, not an unparsable node.
      if (headerWithoutBody(positioned)) None else Some(build(positioned))
    }
  } catch {
    case NonFatal(_) => None
  }

  private val CompoundKeywords = Set("for", "while", "if", "elif", "else", "def", "class")
  private val UnsupportedKeywords = Set("break", "continue", "try", "except", "finally")
  private val MaxDroppedLines = 3

  /** `break`, `continue` and `try`/`except` stay empty instead of hiding behind a prefix. */
  private def hasUnsupportedStatement(source: String): Boolean =
    PythonNormalizer.default.normalizeLineEndings(source).split("\n", -1).exists { line =>
      UnsupportedKeywords.contains(keywordOf(line))
    }

  /**
   * Keeps a finished prefix while the tail is still being typed.
   * Tries the last few code lines from the end; a compound header gets `pass`.
   * Line numbers of the kept source stay put because the edit is only at the tail.
   */
  private def recoverIncompleteTail(source: String): Option[Chart] = {
    val lines = PythonNormalizer.default.normalizeLineEndings(source).split("\n", -1).toVector
    val normalized = lines.mkString("\n")
    val codeRows = lines.indices.filter(i => codeOf(lines(i)).nonEmpty).reverse.take(MaxDroppedLines)
    codeRows.iterator.flatMap { last =>
      val kept = lines.take(last + 1)
      val candidate =
        if (isCompoundHeader(kept(last))) withPass(kept, last)
        else kept.mkString("\n")
      // parseChart already rejected the original source; skip that candidate.
      Option.when(candidate != normalized)(candidate).flatMap(parseChart)
    }.nextOption()
  }

  private def withPass(lines: Vector[String], headerIndex: Int): String = {
    val indent = lines(headerIndex).takeWhile(_ == ' ')
    val passLine = indent + "    pass"
    val (before, after) = lines.splitAt(headerIndex + 1)
    (before :+ passLine :++ after).mkString("\n")
  }

  private def headerWithoutBody(items: List[Positioned]): Boolean = items.exists { item =>
    (item.kind == "process" && isCompoundHeader(item.text) && item.body.isEmpty) ||
      headerWithoutBody(item.body) || headerWithoutBody(item.otherwise)
  }

  private def isCompoundHeader(line: String): Boolean =
    CompoundKeywords.contains(keywordOf(line)) && codeOf(line).endsWith(":")

  private def keywordOf(line: String): String =
    codeOf(line).takeWhile(ch => ch.isLetterOrDigit || ch == '_')

  /** First non-comment token on the line; comments and strings come from extractRawLines. */
  private def codeOf(line: String): String =
    PythonNormalizer.default.extractRawLines(line)
      .map(_.text)
      .find(text => !text.startsWith("#"))
      .getOrElse("")

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
    Chart(nodes.filter(n => reachable(n.line)).sortBy(_.line).toList,
      edges.filter(e => reachable(e.from)).toList, entries.toList)
  }
}
