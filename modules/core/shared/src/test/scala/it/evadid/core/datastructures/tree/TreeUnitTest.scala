package it.evadid.core.datastructures.tree

import it.evadid.core.datastructures.tree.nodeImpl.*
import munit.FunSuite

class TreeUnitTest extends FunSuite {
  test("position helpers and root/parent behavior") {
    val pos = NodeBasedTreePosition(List(1, 2, 3))
    assertEquals(pos.forParent(), Some(NodeBasedTreePosition(List(1, 2))))
    assertEquals(pos.relativeTo(NodeBasedTreePosition(List(5)), 2), NodeBasedTreePosition(List(5, 3, 2, 3)))
    assert(NodeBasedTreePosition.root.isRoot)
  }

  test("basic tree add/get/remove") {
    val tree = NodeBasedTreeImpl.empty[String]()
      .addAsLastChild(NodeBasedTreePosition.root, "a")
      .addAsLastChild(NodeBasedTreePosition.root, "b")
    val posA = NodeBasedTreePosition.root.forChild(0)

    val withChild = tree.addAsLastChild(posA, "a1")
    assertEquals(withChild.getData(posA), Some("a"))
    assertEquals(withChild.getParent(posA.forChild(0)), Some(posA))

    val removed = withChild.removePosition(posA.forChild(0))
    assertEquals(removed.getChildren(posA).size, 0)
  }
}
