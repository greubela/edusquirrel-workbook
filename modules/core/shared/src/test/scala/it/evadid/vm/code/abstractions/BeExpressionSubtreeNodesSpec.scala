package it.evadid.vm.code.abstractions

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import munit.FunSuite

class BeExpressionSubtreeNodesSpec extends FunSuite {
  private def viaTree(expression: BeExpression): Vector[BeExpression] =
    expression.recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values.collect {
      case BeExpressionReference(_, node) => node
    }.toVector

  test("subtree nodes contain the same expressions as recToTree, keep every occurrence and list them in preorder") {
    val programs = Seq(
      ProgrammingStateRaw(Python, "def f(a):\n    if a > 1:\n        return f(a - 1)\n    return 1\nfor i in range(3):\n    forward(f(i))\n"),
      ProgrammingStateRaw(Java, "public class Main {\n  static int g(int n) {\n    int s = 0;\n    while (s < n) { s = s + 1; }\n    return s;\n  }\n  public static void main(String[] args) {\n    g(2);\n  }\n}"))
    programs.foreach { state =>
      val expression = state.toBeExpression.expression
      val nodes = expression.subtreeNodes
      // recToTree's values drop repeated equal subexpressions (e.g. the literal 1); the traversal keeps them.
      assertEquals(nodes.toSet, viaTree(expression).toSet)
      assert(nodes.size > viaTree(expression).size)
      assert(nodes.head eq expression)
      def children(node: BeExpression) = node.structureInfo.getChildrenAsReference(BeScope.GlobalScope()).map(_.expr)
      def preorder(node: BeExpression): Vector[BeExpression] = node +: children(node).toVector.flatMap(preorder)
      assertEquals(nodes, preorder(expression))
    }
  }

  test("deeply nested programs are traversed without the cost of building positioned trees") {
    val depth = 12
    val body = (0 until depth).foldRight("forward(1)") { (level, inner) =>
      s"for i$level in range(2):\n" + inner.linesIterator.map("    " + _).mkString("\n")
    }
    val expression = ProgrammingStateRaw(Python, body + "\n").toBeExpression.expression
    val started = System.nanoTime()
    val nodes = expression.subtreeNodes
    assert((System.nanoTime() - started) / 1e6 < 500, "traversal stays fast")
    assertEquals(nodes.count {
      case _: it.evadid.vm.code.controlStructures.BeFor | _: it.evadid.vm.code.controlStructures.BeRepeatNr => true
      case _ => false
    }, depth)
  }
}
