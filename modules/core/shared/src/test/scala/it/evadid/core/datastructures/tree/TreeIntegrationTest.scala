package it.evadid.core.datastructures.tree

import it.evadid.core.datastructures.tree.nodeImpl.*
import munit.FunSuite

class TreeIntegrationTest extends FunSuite {

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
