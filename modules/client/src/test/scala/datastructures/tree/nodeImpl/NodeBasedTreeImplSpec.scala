package datastructures.tree.nodeImpl

import it.evadid.core.datastructures.tree.Tree
import it.evadid.core.datastructures.tree.nodeImpl.{NodeBasedTreeImpl, NodeBasedTreeNode, NodeBasedTreePosition}
import munit.FunSuite

class NodeBasedTreeImplSpec extends FunSuite:

  private val root = NodeBasedTreePosition.root

  private case class TreeFixture(
      name: String,
      buildStringTree: List[NodeBasedTreeNode[String]] => Tree[NodeBasedTreePosition, String],
      buildIntTree: List[NodeBasedTreeNode[Int]] => Tree[NodeBasedTreePosition, Int]
  )

  private val nodeBasedFixture = TreeFixture(
    name = "node-based tree",
    buildStringTree = roots => NodeBasedTreeImpl[String](roots),
    buildIntTree = roots => NodeBasedTreeImpl[Int](roots)
  )

  // Once an array-backed implementation is introduced, add another fixture here, e.g.:
  // TreeFixture(
  //   name = "array-backed tree",
  //   buildStringTree = roots => ArrayBasedTreeImpl.fromRootArray(roots),
  //   buildIntTree = roots => ArrayBasedTreeImpl.fromRootArray(roots)
  // )
  private val fixtures: List[TreeFixture] = List(nodeBasedFixture)

  private val sampleTreeNodes: List[NodeBasedTreeNode[String]] =
    List(
      NodeBasedTreeNode(
        "rootA",
        List(
          NodeBasedTreeNode("childA1", List(NodeBasedTreeNode("grandA1", List()))),
          NodeBasedTreeNode("childA2", List())
        )
      ),
      NodeBasedTreeNode("rootB", List(NodeBasedTreeNode("childB1", List())))
    )

  fixtures.foreach { fixture =>
    test(s"${fixture.name}: structure cache lookups surface parents, children, and data") {
      val tree = fixture.buildStringTree(sampleTreeNodes)

      val rootA = root.forChild(0)
      val childA1 = rootA.forChild(0)
      val grandA1 = childA1.forChild(0)
      val childA2 = rootA.forChild(1)
      val rootB = root.forChild(1)
      val childB1 = rootB.forChild(0)

      assertEquals(tree.getData(rootA), Some("rootA"))
      assertEquals(tree.getData(grandA1), Some("grandA1"))
      assertEquals(tree.getParent(grandA1), Some(childA1))
      assertEquals(tree.getParent(childA1), Some(rootA))
      assertEquals(tree.getChildren(root), List(rootA, rootB))
      assertEquals(tree.getChildren(rootA), List(childA1, childA2))
      assertEquals(tree.getChildren(childA1), List(grandA1))
      assertEquals(tree.getChildren(rootB), List(childB1))
    }

    test(s"${fixture.name}: relative positions keep ordering when splicing subtrees as childNr or last child") {
      val base = fixture.buildStringTree(List(NodeBasedTreeNode("left", List()), NodeBasedTreeNode("right", List())))
      val subtree = fixture.buildStringTree(
        List(
          NodeBasedTreeNode("insert1", List(NodeBasedTreeNode("insert1-child1", List()), NodeBasedTreeNode("insert1-child2", List()))),
          NodeBasedTreeNode("insert2", List())
        )
      )

      val combined = base.addSubtreeAsChildNr(root, 1, subtree)
      val rootChildrenData = combined.getChildren(root).flatMap(pos => combined.getData(pos))
      assertEquals(rootChildrenData, List("left", "insert1", "insert2", "right"))

      val insert1Pos = root.forChild(1)
      val insert1Children = combined.getChildren(insert1Pos).flatMap(pos => combined.getData(pos))
      assertEquals(insert1Children, List("insert1-child1", "insert1-child2"))

      val trailingSubtree = fixture.buildStringTree(
        List(NodeBasedTreeNode("tail", List(NodeBasedTreeNode("tail-child", List()))))
      )
      val appended = combined.addSubtreeAsLastChild(insert1Pos, trailingSubtree)
      val updatedInsert1Children = appended.getChildren(insert1Pos).flatMap(pos => appended.getData(pos))
      assertEquals(updatedInsert1Children, List("insert1-child1", "insert1-child2", "tail"))
      val tailChildPosition = insert1Pos.forChild(2).forChild(0)
      assertEquals(appended.getData(tailChildPosition), Some("tail-child"))
    }

    test(s"${fixture.name}: removing a position drops its subtree but keeps siblings") {
      val tree = fixture.buildStringTree(
        List(
          NodeBasedTreeNode("keep-left", List(NodeBasedTreeNode("left-child", List()))),
          NodeBasedTreeNode("remove-me", List(NodeBasedTreeNode("removed-child", List()))),
          NodeBasedTreeNode("keep-right", List())
        )
      )

      val pruned = tree.removePosition(root.forChild(1))
      val remainingRoots = pruned.getChildren(root).flatMap(pos => pruned.getData(pos))
      assertEquals(remainingRoots, List("keep-left", "keep-right"))
      assertEquals(pruned.getChildren(root.forChild(0)).flatMap(pos => pruned.getData(pos)), List("left-child"))
      assertEquals(pruned.searchForValue("remove-me"), Set())
      assertEquals(pruned.searchForValue("removed-child"), Set())
    }
  }
