package it.evadid.core.datastructures.tree

import it.evadid.core.datastructures.tree.nodeImpl.*
import munit.FunSuite

class TreeMutationIntegrationTest extends FunSuite {
  test("subtree insertion and level pruning") {
    val base = NodeBasedTreeImpl.empty[String]().addAsLastChild(NodeBasedTreePosition.root, "root-child")
    val sub = NodeBasedTreeImpl.empty[String]().addAsLastChild(NodeBasedTreePosition.root, "s1").addAsLastChild(NodeBasedTreePosition(List(0)), "s1-1")

    val merged = base.addSubtreeAsLastChild(NodeBasedTreePosition(List(0)), sub)
    assert(merged.values.contains("s1"))
    assert(merged.values.contains("s1-1"))

    val pruned = merged.getSubtreeInclLevel(1)
    assert(!pruned.values.contains("s1-1"))
  }
}
