package it.evadid.core.datastructures.graph

import it.evadid.evacuation.core.datastructures.graphs.Position
import munit.FunSuite
import upickle.default.{read, write}

class GraphPackageSpec extends FunSuite {
  private class GraphImpl(ns: List[String] = Nil, es: List[Edge[String, Int]] = Nil)
    extends ImmutableGraphListImpl[String, Int, Edge[String, Int]](ns, es) {
    override def createEdge(start: String, dest: String, info: Int): Edge[String, Int] = Edge(start, dest, info)
    override protected def createInstance(nodes: Seq[String], edges: Seq[Edge[String, Int]]): this.type =
      new GraphImpl(nodes.toList, edges.toList).asInstanceOf[this.type]
  }

  test("immutable graphs add endpoints once and preserve the original graph") {
    val empty = new GraphImpl()
    val graph = empty.addNode("a").addNode("a").addEdge("a", "b", 7)
    assertEquals(empty.nodes, Seq.empty[String])
    assertEquals(graph.nodes, Seq("a", "b"))
    assertEquals(graph.edges.toSet, Set(Edge("a", "b", 7), Edge("b", "a", 7)))
    assertEquals(graph.getNeighbours("a"), Seq(Edge("a", "b", 7)))
    assertEquals(graph.getEdgesTo("a"), Seq(Edge("b", "a", 7)))
    assertEquals(graph.dirEdgesBetween("a", "b"), Seq(Edge("a", "b", 7)))
    assertEquals(graph.allEdgesBetween("a", "b").size, 2)
  }

  test("directed deletion preserves reverse edges") {
    val graph = new GraphImpl().addEdge("a", "b", 7)
    val directed = graph.deleteEdgesBetween("a", "b", directed = true)
    assertEquals(directed.edges, Seq(Edge("b", "a", 7)))
    assertEquals(graph.deleteEdgesBetween("a", "b", directed = false).edges, Seq.empty[Edge[String, Int]])
    assertEquals(graph.deleteEdgesBetween("missing", "b", true).edges, graph.edges)
  }

  test("self-loop endpoints and replacements remain unique and fully rebound") {
    val graph = new GraphImpl().addEdgeDirected("a", "a", 1)
    assertEquals(graph.nodes, Seq("a"))
    assertEquals(graph.allEdgesBetween("a", "a"), Seq(Edge("a", "a", 1)))
    val replaced = graph.replaceNode("a", "b")
    assertEquals(replaced.nodes, Seq("b"))
    assertEquals(replaced.edges, Seq(Edge("b", "b", 1)))
    assertEquals(graph.replaceNode("absent", "b"), graph)
  }

  test("node deletion and clearing preserve the requested remaining nodes") {
    val graph = new GraphImpl().addEdgeDirected("a", "b", 1).addEdgeDirected("b", "c", 2)
    assertEquals(graph.deleteEdgesForNode("b").nodes, Seq("a", "b", "c"))
    assertEquals(graph.deleteEdgesForNode("b").edges, Seq.empty[Edge[String, Int]])
    assertEquals(graph.deleteNodeAndEdges("b").nodes, Seq("a", "c"))
    assertEquals(graph.deleteNodeAndEdges("b").edges, Seq.empty[Edge[String, Int]])
    assertEquals(graph.clearEdges().nodes, graph.nodes)
    assertEquals(graph.clearEdges().edges, Seq.empty[Edge[String, Int]])
    assertEquals(graph.clear().nodes, Seq.empty[String])
    assertEquals(graph.clear().edges, Seq.empty[Edge[String, Int]])
  }

  test("ObservableGraph replaces directed edges and removes edges when an endpoint is removed") {
    val graph = new ObservableGraph[String, Int]
    graph.+=> ("a", "b", 1)
    graph.+=> ("a", "b", 2)
    assertEquals(graph.nodes.toList, List("a", "b"))
    assertEquals(graph.edges.toList, List(Edge("a", "b", 2)))
    graph.+=> ("c", "a", 3)
    assertEquals(graph.getEdgesTo("a"), Seq(Edge("c", "a", 3)))
    graph.replaceNode("a", "new")
    assertEquals(graph.edges.toList.toSet, Set(Edge("new", "b", 2), Edge("c", "new", 3)))
    graph.nodes -= "new"
    assertEquals(graph.edges.toList, Nil)
    assertEquals(graph.nodes.toList.toSet, Set("b", "c"))
  }

  test("ObservableGraph handles reverse edges, missing replacements and self-loops") {
    val graph = new ObservableGraph[String, Int]
    graph.+=(Edge("a", "b", 4))
    assertEquals(graph.getReverseEdges(Edge("a", "b", 4)), Seq(Edge("b", "a", 4)))
    graph.-=> ("a", "b")
    assertEquals(graph.edges.toList, List(Edge("b", "a", 4)))
    graph.-=("a", "b")
    assertEquals(graph.edges.toList, Nil)
    graph.+=> ("a", "a", 1)
    graph.replaceNode("a", "self")
    assertEquals(graph.getEdges("self", "self"), List(Edge("self", "self", 1)))
    assert(!graph.nodes.contains("a"))
    val before = graph.nodes.toList
    graph.replaceNode("missing", "extra")
    assertEquals(graph.nodes.toList, before)
  }

  test("PositionableEdge computes the midpoint and endpoint distance") {
    val a = new Positionable { val pos = Position(0, 0) }
    val b = new Positionable { val pos = Position(6, 8) }
    val edge = new PositionableEdge(a, b, "label")
    assertEquals(edge.pos, Position(3, 4))
    assertEqualsDouble(edge.pxDist, 10.0, 1e-10)
    assertEquals(edge.content, "label")
  }

  test("Edge defaults serialize arbitrary supported node and content types") {
    val edge = Edge("start", "destination", List(1, 2, 3))
    assertEquals(read[Edge[String, List[Int]]](write(edge)), edge)
  }
}
