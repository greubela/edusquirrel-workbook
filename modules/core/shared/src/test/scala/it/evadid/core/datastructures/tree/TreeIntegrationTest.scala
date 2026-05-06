package it.evadid.core.datastructures.tree

import it.evadid.core.datastructures.tree.nodeImpl.*
import munit.FunSuite

class TreeIntegrationTest extends FunSuite {
  test("tree traversal mapping and child results") {
    val tree = NodeBasedTreeImpl.empty[String]()
      .addAsLastChild(NodeBasedTreePosition.root, "a")
      .addAsLastChild(NodeBasedTreePosition.root, "b")
      .addAsLastChild(NodeBasedTreePosition(List(0)), "a1")

    val mapped = tree.map(_.toUpperCase)
    assertEquals(mapped.getData(NodeBasedTreePosition(List(1))), Some("B"))

    val childResult = tree.applyWithChildResults[Int]((ctx, children) => ctx.curValue.length + children.values.sum)
    assertEquals(childResult(NodeBasedTreePosition(List(0))), 3)
  }

  test("tree context exposes sibling and parent metadata") {
    val tree = NodeBasedTreeImpl(List(
      NodeBasedTreeNode("r1", List(NodeBasedTreeNode("c1", List()), NodeBasedTreeNode("c2", List())))
    ))
    val posC1 = NodeBasedTreePosition(List(0, 0))

    tree.foreachWithStructure(ctx => {
      if (ctx.curPosition == posC1) {
        assertEquals(ctx.traversalInfoForSiblingsInParent.size, 2)
        assertEquals(ctx.parentValue, Some("r1"))
      }
    }, bottomUp = false)
  }
}
