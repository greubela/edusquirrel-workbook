package it.evadid.core.datastructures.vectorShapes.renderer

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python}

import scala.collection.mutable

/** A line-addressed flowchart for an editor gutter, independent of the source language. Language positioners
  * ([[PythonFlowchartGutter]], [[JavaFlowchartGutter]]) place a program's statements on source lines; source is
  * never rewritten.
  */
object FlowchartGutter {
  case class Node(line: Int, kind: String, depth: Int, label: String) derives upickle.default.ReadWriter
  case class Edge(from: Int, to: Int, kind: String = "next") derives upickle.default.ReadWriter
  case class FlowLine(line: Int, kind: String) derives upickle.default.ReadWriter
  case class Chart(nodes: List[Node], edges: List[Edge], entries: List[Int],
                   flow: List[List[FlowLine]] = Nil) derives upickle.default.ReadWriter
  val empty: Chart = Chart(Nil, Nil, Nil)

  /** A statement on source line `line` (its first row) with its label; decisions keep both branches and the
    * row of a physical `else`. `kind` is one of process, decision, loop, function, class.
    */
  final case class Positioned(line: Int, text: String, depth: Int,
                              body: List[Positioned] = Nil,
                              otherwise: List[Positioned] = Nil,
                              kind: String = "process", elseLine: Option[Int] = None)

  /** The chart of `source` in `language`; empty where the language has no positioner or the source is unsupported. */
  def of(language: ProgrammingLanguage, source: String): Chart = language match {
    case Python => PythonFlowchartGutter.fromPython(source)
    case Java => JavaFlowchartGutter.fromJava(source)
    case _ => empty
  }

  /** The graph and flow plan of positioned statements: each declaration body is its own graph. */
  def build(statements: List[Positioned]): Chart = {
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
        case "function" | "class" => Nil
        case _ => List(FlowLine(item.line, item.kind))
      }
    }
    def group(items: List[Positioned]): List[FlowLine] = rows(items) match {
      case Nil => Nil
      case result => FlowLine(result.head.line, "start") :: result ::: List(FlowLine(result.last.line, "stop"))
    }
    def declarations(items: List[Positioned]): List[List[FlowLine]] = items.flatMap { item =>
      val nested = item.body.exists(child => Set("function", "class")(child.kind))
      val own = if item.kind == "function" && !nested then {
        val bodyRows = rows(item.body)
        val end = bodyRows.lastOption.map(_.line).orElse(item.body.lastOption.map(_.line)).getOrElse(item.line)
        List(FlowLine(item.line, "start") :: bodyRows ::: List(FlowLine(end, "stop")))
      } else Nil
      own ::: declarations(item.body) ::: declarations(item.otherwise)
    }
    val functions = declarations(statements)
    if functions.nonEmpty then functions else List(group(statements)).filter(_.nonEmpty)
  }

}
