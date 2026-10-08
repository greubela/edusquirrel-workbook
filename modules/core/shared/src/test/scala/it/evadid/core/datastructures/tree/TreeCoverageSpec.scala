package it.evadid.core.datastructures.tree

import it.evadid.core.datastructures.tree.nodeImpl.*
import munit.FunSuite
import scala.collection.mutable.ListBuffer

class TreeCoverageSpec extends FunSuite {
  private val root = NodeBasedTreePosition.root
  private def sample = NodeBasedTreeImpl(List(
    NodeBasedTreeNode("a", List(NodeBasedTreeNode("a1", Nil), NodeBasedTreeNode("a2", Nil))),
    NodeBasedTreeNode("b", Nil)
  ))
  private def children(tree: Tree[NodeBasedTreePosition, String], p: NodeBasedTreePosition) =
    tree.getChildren(p).flatMap(tree.getData)

  test("empty trees, missing positions and subtree extraction") {
    val empty = NodeBasedTreeImpl.empty[String]()
    assertEquals(empty.entries, Set.empty[(NodeBasedTreePosition, String)])
    assertEquals(empty.size, 0)
    assertEquals(empty.getParent(root), None)
    assertEquals(sample.getChildren(root.forChild(99)), Nil)
    assertEquals(sample.getData(root.forChild(99)), None)
    assert(sample.subtreeInclPosition(root.forChild(99)).isEmpty)
    assertEquals(sample.subtreeInclPosition(root.forChild(0)).values, Set("a", "a1", "a2"))
    assert(sample.removePosition(root).isEmpty)
    assertEquals(sample.removePosition(root.forChild(99)).entries, sample.entries)
    assertEquals(sample.searchForValue("a2"), Set(root.forChild(0).forChild(1)))
  }

  test("traversal respects top down and bottom up ordering") {
    val topDown = ListBuffer.empty[String]
    val bottomUp = ListBuffer.empty[String]
    sample.foreach((_, value) => topDown += value, false)
    sample.foreach((_, value) => bottomUp += value, true)
    assertEquals(topDown.toList, List("a", "a1", "a2", "b"))
    assertEquals(bottomUp.toList, List("a1", "a2", "a", "b"))
    assertEquals(sample.map(_.length).values, Set(1, 2))
    assert(sample.toString.contains("a1"))
  }

  test("bulk insertion accepts unsorted original indices without losing siblings") {
    val transformed = sample.traverseStructureAndAddChildren(
      context => if (context.curValue == "a") List(2 -> "z", 0 -> "x", 1 -> "y") else Nil,
      List(2 -> "end", 0 -> "start", 1 -> "middle")
    )
    assertEquals(children(transformed, root), List("start", "a", "middle", "b", "end"))
    assertEquals(children(transformed, root.forChild(1)), List("x", "a1", "y", "a2", "z"))
    assertEquals(sample.size, 4)
  }

  test("bulk insertion at a shared index retains caller order") {
    val tree = sample.traverseStructureAndAddChildren(_ => Nil, List(0 -> "first", 0 -> "second"))
    assertEquals(children(tree, root), List("first", "second", "a", "b"))
  }

  test("subtree insertion retains multiple roots and descendant positions") {
    val inserted = sample.addSubtreeAsChildNr(root.forChild(0), 1, sample)
    assertEquals(children(inserted, root.forChild(0)), List("a1", "a", "b", "a2"))
    assertEquals(children(inserted, root.forChild(0).forChild(1)), List("a1", "a2"))
    assertEquals(inserted.getSubtreeInclLevel(0).size, 0)
    assertEquals(inserted.getSubtreeInclLevel(1).values, Set("a", "b"))
  }

  test("mapping with context caches child results and provides structural information") {
    val calls = ListBuffer.empty[String]
    val mapped = sample.mapWithContext[Int] { context =>
      calls += context.curValue
      assertEquals(context.tree.size, 4)
      assertEquals(context.childrenValues, context.traversalInfoForChildren.map(_.curValue))
      assertEquals(context.parentValue, context.traversalInfoForParent.map(_.curValue))
      context.curValue.length + context.accessChildrenResults.sum
    }
    assertEquals(mapped.getData(root.forChild(0)), Some(5))
    assertEquals(mapped.getData(root.forChild(0).forChild(0)), Some(2))
    assertEquals(calls.sorted.toList, List("a", "a1", "a2", "b"))
    val bottomUp = sample.applyWithChildResults[Int]((_, results) => 1 + results.values.sum)
    assertEquals(bottomUp(root.forChild(0)), 3)
    assertEquals(bottomUp(root.forChild(1)), 1)
  }

  test("position roots and relatives retain their coordinate structure") {
    assertEquals(root.forParent(), None)
    assertEquals(root.relativeTo(root.forChild(2), 10), root.forChild(2))
    assertEquals(root.toString, "TreePosition(root)")
    assertEquals(root.forChild(1).forChild(2).toString, "TreePosition(root->1->2)")
  }
}
